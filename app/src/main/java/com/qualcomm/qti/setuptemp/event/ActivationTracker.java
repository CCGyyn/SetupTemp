package com.qualcomm.qti.setuptemp.event;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.provider.Settings;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.qualcomm.qti.setuptemp.utils.Utils;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author uni-qinyu
 * @since 20190112
 */
public class ActivationTracker {
	private static final String TAG = ActivationTracker.class.getSimpleName();
	private static final boolean DEBUG = true;

	public static final int HANDLER_PCO_DATA_INIT = -33;
	public static final int HANDLER_PCO_DATA_0 = 0;
	public static final int HANDLER_PCO_DATA_3 = 3;
	public static final int HANDLER_PCO_DATA_5 = 5;
	public static final int HANDLER_PCO_DATA_NONE = -1;
	public static final int HANDLER_PCO_DATA_TIME_OUT = -22;


	private static final String ACTION_PCO_CHANGE = "com.teleepoch.setupwizardoverlay.PCO_CHANGE";
	private static final String ACTION_SIM_STATE_CHANGED = "android.intent.action.SIM_STATE_CHANGED";

	private static final int MSG_ACTIVATION_TIMEOUT = -1;
	private static final int MSG_RETRY_SCHEDULE_TIMEOUT = 1;
	private static final int MSG_REDISPATCH_PCO = 2;

	public static final String SIM_DESCRIPTION_ABSENT = "absent";
	public static final String SIM_DESCRIPTION_NOT_READY = "not_ready";
	public static final String SIM_DESCRIPTION_READY = "ready";
	public static final String SIM_DESCRIPTION_ERROR = "error";

	private String mSimDescription;
	private int mSimState = -1;
	private String mMdn = null;
	private int mPco = HANDLER_PCO_DATA_INIT;
	private int mLastPco = HANDLER_PCO_DATA_INIT; /// last valid pco

	private static ActivationTracker sInstance;
	private WeakReference<Context> mContextRef;
	private SimStateChangeReceiver mSimStateChangeReceiver;
	private PcoChangeReceiver mPcoChangeReceiver;
	private final ArrayList<CallbackInfo> mCallbackInfos = new ArrayList<>();
	private final ArrayList<Long> mTimeouts = new ArrayList<>();
	private Handler mInternalHandler;
	private TelephonyManager mTelephonyManager;
	private ActivationCheckThread mCheckThread;
	private long mTimeoutUptimeMillis = -1;
	private static final Object Lock = new Object();
	private volatile boolean isTracking = false;

	private ActivationTracker(Context context) {
		mContextRef = new WeakReference<>(context);
	}

	public static ActivationTracker getInstance(Context context) {
		if (sInstance == null) {
			synchronized (ActivationTracker.class) {
				if (sInstance == null) {
					sInstance = new ActivationTracker(context.getApplicationContext());
				}
			}
		}

		return sInstance;
	}

	public static void init(Context context) {
		getInstance(context);
	}


	class CallbackInfo {
		ActivationStatusCallback callback;
		long timeout;
		long uptimeMillis;
		long lastUptimeMillis;

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			CallbackInfo that = (CallbackInfo) o;
			return Objects.equals(callback, that.callback);
		}

		@Override
		public int hashCode() {
			return Objects.hash(callback);
		}
	}

	public void registerActivationStatusCallback(ActivationStatusCallback callback, long timeout) {
		if (!isTracking) {
			if (mContextRef.get() != null) {
				Log.d(TAG, "registerActivationStatusCallback : start tracking first");
				startTracking();
			}
 		}

		if (callback == null) {
			throw new IllegalArgumentException("callback should not be null");
		}

		CallbackInfo info = new CallbackInfo();
		info.callback = callback;
		info.timeout = timeout;
		info.lastUptimeMillis = SystemClock.uptimeMillis();
		info.uptimeMillis = info.lastUptimeMillis + timeout;
		if (mCallbackInfos.contains(info)) return;
 		mCallbackInfos.add(info);
		mTimeouts.add(info.uptimeMillis); // add timeout

		onAddCallbackInfo(info);

		notifyNow(); // notify the thread
	}

	public void unregisterActivationStatusCallback(ActivationStatusCallback callback) {
		if (callback == null) {
			throw new IllegalArgumentException("callback should not be null");
		}

		CallbackInfo dying = null;
		for (CallbackInfo info : mCallbackInfos) {
			if (info.callback == callback) {
				dying = info;
			}
		}

		if (DEBUG) Log.e(TAG, "dying=" + dying);
		if (dying != null) {
			mCallbackInfos.remove(dying);
			mTimeouts.remove(dying.uptimeMillis);
			onRemoveCallbackInfo(dying);
		}
	}

	private void onAddCallbackInfo(CallbackInfo info) {
		// notify sim state changes if needed
		if (mSimState >= 0 && !TextUtils.isEmpty(mSimDescription)) { // sim status has changed
			fireSimCardStatusChanged(mSimState, mSimDescription);
		}

		if (!hasSimCard(mContextRef.get())) { // if no sim , no need to schedule timeout. but wait for secs to retry
			// retry
			mInternalHandler.sendEmptyMessageDelayed(MSG_RETRY_SCHEDULE_TIMEOUT, 15 * 1000);
			return;
		}

		// send timeout msg if necessary
		// invalid mTimeoutUptimeMillis(no msg) or smaller uptimeMillis(has msg)
		if (mTimeoutUptimeMillis <= 0 || info.uptimeMillis < mTimeoutUptimeMillis) {
			if (DEBUG) Log.e(TAG, "info.uptimeMillis=" + info.uptimeMillis + " ,mTimeoutUptimeMillis=" + mTimeoutUptimeMillis);
			scheduleNextTimeoutIfNeeded();
		}

		// schedule redeliver valid pco if needed
		if (mLastPco >= 0 || mPco >= 0) {
			long delayMillis = (getNextMinUptimeMillis() - SystemClock.uptimeMillis()) / 3;
			if (DEBUG) Log.e(TAG, "redispatch pco delayMillis=" + delayMillis);
			mInternalHandler.sendEmptyMessageDelayed(MSG_REDISPATCH_PCO, delayMillis > 15000 ? delayMillis : 15000);
		}
	}

	private void onRemoveCallbackInfo(CallbackInfo info) {
		// schedule next msg if necessary
		if (mTimeoutUptimeMillis == info.uptimeMillis) {
			scheduleNextTimeoutIfNeeded();
		}
	}

	private long getNextMinUptimeMillis() {
		long min = -1;
		if (mTimeouts.size() > 0) {
			min = mTimeouts.get(0);
			for (int i = 1; i < mTimeouts.size(); i++) {
				Long l = mTimeouts.get(i);
				if (l < min) {
					min = l;
				}
			}
		}
		if (DEBUG) Log.e(TAG, "min=" + min);
		return min;
	}

	public void startTracking() {
		if (isTracking){
			Log.d(TAG, TAG + " is already tracking..");
			return;
		}
		isTracking = true;

		if (DEBUG) Log.e(TAG, "start tracking");
		mInternalHandler = new InternalHandler(mContextRef.get().getMainLooper());
		mTelephonyManager = (TelephonyManager) mContextRef.get().getSystemService(Context.TELEPHONY_SERVICE);
		registerReceivers();
		getMDN();
		if (DEBUG) Log.e(TAG, "start -> hasSimCard=" + hasSimCard(mContextRef.get()));
	}

	public void stopTracking() {
		if (!isTracking) {
			Log.d(TAG, TAG + " is not tracking..");
			return;
		}
		isTracking = false;

		if (DEBUG) Log.e(TAG, "stop tracking");
		unregisterReceivers();
		mInternalHandler.removeCallbacksAndMessages(null);
		mCallbackInfos.clear();
		mTimeouts.clear();
		if (mCheckThread != null) {
			mCheckThread.interrupt();
			mCheckThread = null;
		}
		mTimeoutUptimeMillis = -1;
	}

	class InternalHandler extends Handler {
		InternalHandler(Looper looper) {
			super(looper);
		}

		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			switch (msg.what) {
				case MSG_ACTIVATION_TIMEOUT:
					if (mContextRef.get() != null) {
						Log.d(TAG, "send 'receive pco time out' broadcast");
						Intent intent = new Intent(ACTION_PCO_CHANGE);
						intent.putExtra("timeoutUptimeMillis", mTimeoutUptimeMillis);
						intent.putExtra("pco_data", HANDLER_PCO_DATA_TIME_OUT);
						mContextRef.get().sendBroadcast(intent);
					}

					scheduleNextTimeoutIfNeeded();
					break;
				case MSG_RETRY_SCHEDULE_TIMEOUT:
					if (hasSimCard(mContextRef.get())) {
						checkSimActivationState();
						scheduleNextTimeoutIfNeeded();
						if (DEBUG) Log.e(TAG, "retry schedule timeout");
					} else {
						mInternalHandler.sendEmptyMessageDelayed(MSG_RETRY_SCHEDULE_TIMEOUT, 5 * 1000);
					}
					break;
				case MSG_REDISPATCH_PCO:
					if (mContextRef.get() != null) {
						int pco = HANDLER_PCO_DATA_NONE;
						if (mLastPco >= 0 && (mPco < 0 || mLastPco == mPco)) {
							pco = mLastPco;
						}

						if (mLastPco == HANDLER_PCO_DATA_INIT && mPco >= 0) {
							pco = mPco;
						}

						if (pco == HANDLER_PCO_DATA_NONE) return;

						Log.d(TAG, "redeliver valid pco=" + pco);

						Intent intent = new Intent(ACTION_PCO_CHANGE);
						intent.putExtra("pco_data", pco);
						intent.setFlags(Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND);
						mContextRef.get().sendBroadcast(intent);
					}
					break;
			}
		}
	}

	private void scheduleNextTimeoutIfNeeded() {
		mTimeouts.remove(mTimeoutUptimeMillis); // remove current

		long nextMinUptimeMillis = getNextMinUptimeMillis();
		if (nextMinUptimeMillis <= 0) { // no time to schedule
			mTimeoutUptimeMillis = -1; // should reset
			return;
		}

		if (nextMinUptimeMillis != mTimeoutUptimeMillis) {
			if (DEBUG) Log.e(TAG, "scheduleNextTimeoutIfNeeded nextMinUptimeMillis=" + nextMinUptimeMillis);
			mInternalHandler.removeMessages(MSG_ACTIVATION_TIMEOUT);
			Message msg = mInternalHandler.obtainMessage(MSG_ACTIVATION_TIMEOUT);
			mTimeoutUptimeMillis = nextMinUptimeMillis;
			mInternalHandler.sendMessageAtTime(msg, mTimeoutUptimeMillis);
		}
	}

	private void registerReceivers() {
		Context context = mContextRef.get();
		if (context == null) {
			return;
		}

		if (mSimStateChangeReceiver == null) mSimStateChangeReceiver = new SimStateChangeReceiver();
		IntentFilter intentFilter = new IntentFilter(ACTION_SIM_STATE_CHANGED/*TelephonyIntents.ACTION_SIM_STATE_CHANGED*/);
		context.registerReceiver(mSimStateChangeReceiver, intentFilter);

		if (mPcoChangeReceiver == null) mPcoChangeReceiver = new PcoChangeReceiver();
		intentFilter = new IntentFilter(ACTION_PCO_CHANGE);
		context.registerReceiver(mPcoChangeReceiver, intentFilter);
		if (DEBUG) Log.d(TAG, "register receivers");
	}

	private void unregisterReceivers() {
		Context context = mContextRef.get();
		if (context == null) {
			return;
		}

		if (mSimStateChangeReceiver != null) {
			context.unregisterReceiver(mSimStateChangeReceiver);
			mSimStateChangeReceiver = null;
			if (DEBUG) Log.d(TAG, "unregisterSimStateChangeReceiver");
		}

		if (mPcoChangeReceiver != null) {
			context.unregisterReceiver(mPcoChangeReceiver);
			mPcoChangeReceiver = null;
			if (DEBUG) Log.d(TAG, "unregisterPcoChangeReceiver");
		}

		if (DEBUG) Log.d(TAG, "unregister receivers");
	}

	private class SimStateChangeReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			String state = intent.getStringExtra("ss");
			Log.d(TAG, "SIM state " + state + " getExtras: " + intent.getExtras().toString());

			mSimState = mTelephonyManager.getSimState();
			switch (state) {
				case "ABSENT":
					mSimDescription = SIM_DESCRIPTION_ABSENT;
					break;
				case "NOT_READY":
					mSimDescription = SIM_DESCRIPTION_NOT_READY;
					break;
				case "LOADED":
					Log.d(TAG, "getSimState " + mSimState);
					if (mSimState == TelephonyManager.SIM_STATE_READY) {
						mSimDescription = SIM_DESCRIPTION_READY;
					}
					break;
				case "READY":
				case "IMSI":
					mSimDescription = SIM_DESCRIPTION_READY;
					break;
				case "CARD_IO_ERROR":
				case "UNKNOWN":
					mSimDescription = SIM_DESCRIPTION_ERROR;
					break;
			}

			fireSimCardStatusChanged(mSimState, mSimDescription);
			if (SIM_DESCRIPTION_READY.equalsIgnoreCase(mSimDescription)) {
				checkSimActivationState();
			}

			if (SIM_DESCRIPTION_READY.equalsIgnoreCase(mSimDescription) &&
					!mInternalHandler.hasMessages(MSG_ACTIVATION_TIMEOUT)) { // when ready , schedule timeout
				if (DEBUG) Log.e(TAG, "scheduleNextTimeoutIfNeeded when sim state is ready");
				mInternalHandler.removeMessages(MSG_RETRY_SCHEDULE_TIMEOUT);
				scheduleNextTimeoutIfNeeded();
			}
		}
	}

	private class PcoChangeReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			int pco_data = intent.getIntExtra("pco_data", -1);
			if (pco_data != mPco) { // pco changed
				if (mPco >= HANDLER_PCO_DATA_0 && mPco <= HANDLER_PCO_DATA_5) { // valid pco
					mLastPco = mPco;
				}
				notifyNow();
				if (pco_data >= 0) { // new pco received, no need to schedule
					mInternalHandler.removeMessages(MSG_REDISPATCH_PCO);
				}
			}
			mPco = pco_data;

			if (DEBUG) Log.d(TAG, "receive pco " + mPco);

			if (mContextRef.get() != null) {
				if (!hasSimCard(mContextRef.get())) {  // no sim
					Log.e(TAG, "no sim found");
					return;
				}
				Settings.Secure.putInt(mContextRef.get().getContentResolver(), "vzw_pco", mPco);
			}

			fireOnPcoReceived((mPco < 0 && mLastPco >= 0) ? mLastPco : mPco);

			switch (mPco) {
				case HANDLER_PCO_DATA_0:
					if (isValidMbn()) {
						fireActivateSuccessful(mMdn,mPco);
					} else {
						if (DEBUG) Log.e(TAG, "mMdn is empty, wait...");
					}
					break;
				case HANDLER_PCO_DATA_3:
				case HANDLER_PCO_DATA_5:
					if (DEBUG) Log.e(TAG, "start plan selection");
					fireActivateWithMBB();
					break;
				case HANDLER_PCO_DATA_TIME_OUT:
					if (DEBUG) Log.e(TAG, "receive pco or read mdn time out");
					long timeoutUptimeMillis = intent.getLongExtra("timeoutUptimeMillis", -1);
					mPco = HANDLER_PCO_DATA_INIT; // reset
					if (mLastPco == HANDLER_PCO_DATA_3 || mLastPco == HANDLER_PCO_DATA_5) {
						if (DEBUG) Log.e(TAG, "due to last pco is valid mbb. mLastPco=" + mLastPco);
						mPco = mLastPco;
						fireActivateWithMBB();
					} else {
						fireActivateTimeout(timeoutUptimeMillis);
					}
					break;
				case HANDLER_PCO_DATA_NONE:
					if (DEBUG) Log.e(TAG, "receive pco is -1");
					if (isValidMbn()) {
						fireActivateSuccessful(mMdn,mPco);
					} else {
						fireActivateFailed(mPco);
					}
					break;
				default:
					if (DEBUG) Log.e(TAG, "sim card invalid");
					if (isValidMbn()) {
						fireActivateSuccessful(mMdn,mPco);
					} else {
						Log.e(TAG, "mMdn is empty, wait...");
					}
					break;
			}
		}

	}

	protected void fireOnPcoReceived(int pco) {
		if (DEBUG) Log.e(TAG, "fireOnPcoReceived pco=" + pco);
		synchronized (mCallbackInfos) {
			for (CallbackInfo info : mCallbackInfos) {
				info.callback.onPcoReceived(mMdn,pco);
			}
		}
	}

	protected void fireSimCardStatusChanged(int status, String description) {
		if (DEBUG) Log.e(TAG, "fireSimCardStatusChanged status=" + status + " ,description=" + description);
		synchronized (mCallbackInfos) {
			for (CallbackInfo info : mCallbackInfos) {
				info.callback.onSimCardStatusChanged(status, description);
			}
		}
	}

	protected void fireActivateSuccessful(String mdn,int pco) {
		if (DEBUG) Log.e(TAG, "fireActivateSuccessful mdn=" + mdn + " ,pco=" + pco);
		synchronized (mCallbackInfos) {
			for (CallbackInfo info : mCallbackInfos) {
				info.callback.onActivateSuccessful(mdn,pco);
			}

			cancelTimeouts();
		}
	}

	protected void fireActivateFailed(int reason) {
		if (DEBUG) Log.e(TAG, "fireActivateFailed reason=" + reason);
		synchronized (mCallbackInfos) {
			for (CallbackInfo info : mCallbackInfos) {
				info.callback.onActivateFailed(reason,mPco);
			}

			cancelTimeouts();
		}
	}

	protected void fireActivateTimeout(long timeoutUptimeMillis) {
		if (DEBUG) Log.e(TAG, "fireActivateTimeout " + timeoutUptimeMillis);

		synchronized (mCallbackInfos) {
			for (CallbackInfo info : mCallbackInfos) {
				if (info.uptimeMillis == timeoutUptimeMillis) {
					info.callback.onActivateTimeout();
				}
			}
		}
	}

	protected void fireActivateWithMBB() {
		if (DEBUG) Log.e(TAG, "fireActivateWithMBB mdn=" + mMdn + " ,pco=" + mPco);
		synchronized (mCallbackInfos) {
			for (CallbackInfo info : mCallbackInfos) {
				info.callback.onActivateWithMBB(mMdn,mPco);
			}

			cancelTimeouts();
		}
	}

	private void cancelTimeouts() {
		mInternalHandler.removeMessages(MSG_ACTIVATION_TIMEOUT);
		mTimeouts.clear();
		mTimeoutUptimeMillis = -1;
	}


	private boolean isValidMbn() {
		return !TextUtils.isEmpty(mMdn) && !mMdn.startsWith("00000");
	}

	class ActivationCheckThread extends Thread {
		@Override
		public void run() {
			super.run();
			for (; ; ) {
				if (isInterrupted()) break;
				getMDN();
				if (isValidMbn()) {
					if (mPco != HANDLER_PCO_DATA_INIT) {
						Context context = mContextRef.get();
						if (context != null) {
							Log.d(TAG, "re-send broadcast PCO_CHANGE");
							Intent intent = new Intent(ACTION_PCO_CHANGE);
							intent.putExtra("pco_data", mPco);
							context.sendBroadcast(intent);

							if (DEBUG) Log.e(TAG, "ActivationCheckThread is waiting");
							waitNow();
							if (DEBUG) Log.e(TAG, "ActivationCheckThread is notified");
						}
					} else { // pco is invalid , wait..
						SystemClock.sleep(3000);
					}
				} else {
					SystemClock.sleep(5000);
				}

				if (DEBUG) Log.e(TAG, "ActivationCheckThread is running, listener=" + mCallbackInfos.size());
				if (mCallbackInfos.size() <= 0) {
					if (DEBUG) Log.e(TAG, "ActivationCheckThread, no listener , wait..");
					waitNow();
				}
			}

			Log.e(TAG, "exit loop..");
		}
	}

	private void waitNow() {
		try {
			synchronized (Lock) {
				Lock.wait();
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	private void notifyNow() {
		synchronized (Lock) {
			Lock.notifyAll();
		}
	}

	private void checkSimActivationState() {
		if (mCheckThread == null) {
			mCheckThread = new ActivationCheckThread();
			mCheckThread.start();
		}
	}

	private void getMDN() {
		if (DEBUG) Log.e(TAG, "getMDN");
		Context context = mContextRef.get();
		if (context == null) return;

		SubscriptionManager subscriptionManager = SubscriptionManager.from(context);

		if (subscriptionManager != null) {
			List<SubscriptionInfo> subscriptionInfos = subscriptionManager.getActiveSubscriptionInfoList();

			if (subscriptionInfos != null && subscriptionInfos.size() > 0) {
				SubscriptionInfo selectableSubInfo = subscriptionInfos.get(0);
				if (selectableSubInfo != null) {
					mMdn = selectableSubInfo.getNumber();
					Log.d(TAG, "mdn: " + mMdn);
				}
			}
		}
	}

	/**
	 * 判断是否包含SIM卡
	 *
	 * @return 状态
	 */
	public static boolean hasSimCard(Context context) {
		TelephonyManager telMgr = (TelephonyManager)
				context.getSystemService(Context.TELEPHONY_SERVICE);
		int simState = telMgr.getSimState();
		boolean result = true;
		switch (simState) {
			case TelephonyManager.SIM_STATE_ABSENT: // 没有SIM卡
			case TelephonyManager.SIM_STATE_UNKNOWN: // 未知，可能处于状态转换
				result = false;
				break;
		}
		if (DEBUG) Log.d(TAG, result ? "sim card is inserted" : "no sim card found , simState="+simState);
		return result;
	}

}
