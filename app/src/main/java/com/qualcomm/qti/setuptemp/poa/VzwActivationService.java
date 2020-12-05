package com.qualcomm.qti.setuptemp.poa;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.app.Dialog;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.WindowManager;

import com.qualcomm.qti.setuptemp.DefaultActivity;
import com.qualcomm.qti.setuptemp.R;
import com.qualcomm.qti.setuptemp.event.ActivationTracker;
import com.qualcomm.qti.setuptemp.utils.Utils;

import static com.qualcomm.qti.setuptemp.event.ActivationTracker.HANDLER_PCO_DATA_0;
import static com.qualcomm.qti.setuptemp.event.ActivationTracker.HANDLER_PCO_DATA_5;
import static com.qualcomm.qti.setuptemp.utils.Utils.ACTION_PCO_CHANGE;
import static com.qualcomm.qti.setuptemp.receiver.ProvisionReceiver.ACTION_POA_DEBUG_MODE_CHANGE;

public class VzwActivationService extends Service {
	private static final String TAG = VzwActivationService.class.getSimpleName();
	private static final boolean DEBUG = Utils.DEBUG;
	public static final int MSG_RETRY_LOOKUP_ORDER_REQ = 1;
	public static final int MSG_CHECK_SIM_STATE = 2;

	public static final String REQ_TYPE = "req_release";
	public static final String REQ_RELEASE = "req_release";
	public static final String REQ_NONE = "req_none";
	private static final String ACTION_ORDER_FOUND = "com.qualcomm.qti.setuptemp.poa.ACTION_ORDER_FOUND";
	private static final String ACTION_SHOW_ACTIVATING = "com.qualcomm.qti.setuptemp.poa.ACTION_SHOW_ACTIVATING";
	private static final String ACTION_SHOW_ACTIVATION_SUCCESS = "com.qualcomm.qti.setuptemp.poa.ACTION_ACTIVATION_SUCCESS";
	private static final String ACTION_SHOW_ACTIVATION_FAILURE = "com.qualcomm.qti.setuptemp.poa.ACTION_ACTIVATION_FAILURE";
	private static final String ACTION_SHOW_ACTIVATION_DIALOG = "com.qualcomm.qti.setuptemp.poa.ACTION_ACTIVATION_DIALOG";
	public static final int MSG_RELEASEORDER_REQUEST_SUCCESS=11;
	public static final int MSG_RELEASEORDER_REQUEST_FAILURE=12;
	public static final int MSG_RELEASEORDER_REQUEST_TIMEOUT=13;
	public static final int MSG_RETRY_RELEASE_ORDER_REQ=14;
	public static final int MSG_HANDLE_AIRPLANE_MODE_CHANGED=15;
	public static final int MSG_ACTIVATION_SUCCESS=16;
	public static final int MSG_GET_MDN = 17;
	public static final int MSG_GET_MDN_TIMEOUT = 18;

	private PoaSpecialReceiver mPoaReceiver;
	private LookUpOrderTask mLookupOrderTask;

	public static final int REQ_RETRY_MAX_TIMES = 5;
	private int mReqRetry;
	private double mPco;
	private String mImsi;
	private String mImei;
	private String mCorrelationID;
	private String mRequestID;
	private int mSecurityQID;
	private int mOrderType;
	private String mReqType=REQ_NONE;
	private ReleaseTask mReleaseTask;


	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		if (DEBUG) {
			Log.d(TAG, "onCreate");
		}

		registerPoaSpecialReceiver();

		mImsi = Utils.getImsi(this);
		mImei = Utils.getImei(this);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent != null) {
			mReqType = intent.getStringExtra(REQ_TYPE);
			handleReq(intent,mReqType);
		}

		return super.onStartCommand(intent, flags, startId);
	}

	private void handleReq(Intent intent,String req) {
		if (TextUtils.isEmpty(req)) {
			return;
		}

		switch (req) {
			case REQ_RELEASE:
				sendNotificationViaPhoneReceiver(ACTION_SHOW_ACTIVATING);

				Bundle args = intent.getBundleExtra("args");
				mRequestID = args.getString("mRequestID");
				mCorrelationID = args.getString("mCorrelationID");
				if (DEBUG) {
					Log.d(TAG, "REQ_RELEASE mRequestID=" + mRequestID + " ,mCorrelationID=" + mCorrelationID);
				}
				pendingOrderReleaseOrder();
				break;
		}
	}

	private void registerPoaSpecialReceiver() {
		if (mPoaReceiver == null) {
			mPoaReceiver = new PoaSpecialReceiver();
		}
		IntentFilter filter = new IntentFilter();
		filter.addAction(ACTION_POA_DEBUG_MODE_CHANGE);
		filter.addAction(ACTION_PCO_CHANGE);
		filter.addAction(ACTION_SHOW_ACTIVATION_DIALOG);
		registerReceiver(mPoaReceiver, filter);
	}

	private void unregisterPoaSpecialReceiver() {
		if (mPoaReceiver != null) {
			unregisterReceiver(mPoaReceiver);
			mPoaReceiver = null;
		}
	}


	class PoaSpecialReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (DEBUG) {
				Log.d(TAG, "onReceive action=" + action);
			}
			switch (action) {
				case ACTION_PCO_CHANGE:
					int pco = intent.getIntExtra("pco_data", -1);
					if (DEBUG) {
						Log.d(TAG, "ACTION_PCO_CHANGE pco=" + pco);
					}
					handlePcoChange(pco);
					break;
				case ACTION_POA_DEBUG_MODE_CHANGE:
					handlePoaDebugModeChanged();
					break;
				case ACTION_SHOW_ACTIVATION_DIALOG:
					if (DEBUG) {
						Log.d(TAG, "receive ACTION_SHOW_ACTIVATION_DIALOG");
					}

					showReadToActivateDialog();
					break;
			}
		}

	}

	private void showReadToActivateDialog() {
		Context context = getApplicationContext();
		AlertDialog.Builder builder;
		builder = new AlertDialog.Builder(context).setIcon(R.mipmap.ic_launcher)
				.setMessage(context.getString(R.string.ready_to_activate) + "?")
				.setPositiveButton(R.string.activate_now, (dialog, which) -> {
					verifyYourAccount();
					dialog.dismiss();
				})
				.setNegativeButton(R.string.activate_cancel, (dialog, which) -> {

				});
		builder.create().show();
	}

	private void verifyYourAccount() {
		if (DEBUG) {
			Log.d(TAG, "verifyYourAccount");
		}

		Utils.enableComponentSetting(getApplicationContext(), DefaultActivity.class, true);

		Bundle args = new Bundle();
		args.putString("mCorrelationID", mCorrelationID);
		args.putString("mRequestID", mRequestID);
		args.putInt("mSecurityQuestionID", mSecurityQID);
		args.putInt("mOrderType",mOrderType);

		Intent intent = new Intent(getApplicationContext(), DefaultActivity.class);
		intent.putExtra(DefaultActivity.ARGS, args);
		intent.putExtra(DefaultActivity.START_FROM_NOTIFICATION, true);
		intent.putExtra(DefaultActivity.SPECIFIC_PREF, VzwPendingOrderAuthenticationFragment.class.getName());
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		startActivity(intent);
	}


	private void handlePoaDebugModeChanged() {
		if (!Utils.isSetupComplete(this)) {
			Log.d(TAG, "handlePoaDebugModeChanged setup not complete");
			return;
		}

		if (DEBUG && mPco == HANDLER_PCO_DATA_0) {
			Log.d(TAG, "handlePoaDebugModeChanged");

			boolean enable = Settings.Secure.getInt(getContentResolver(), PoaConfig.POA_SECURE_DEBUG_MODE, 0) == 1;

			if (enable) {
				prepareLookupOrder();
			}
		}
	}

	private void handlePcoChange(int pco) {
		if (pco >= 0) {
			mPco = pco;
		}

		switch (pco) {
			case HANDLER_PCO_DATA_0:
				if (REQ_RELEASE.equals(mReqType)) {
					dealWithMdn();
					mH.sendEmptyMessageDelayed(MSG_GET_MDN_TIMEOUT, 5 * 60 * 1000);
					return;
				}

				if (PoaConfig.isDebuggable()) { /// for test when debug
					prepareLookupOrder();
				}
				break;
			case HANDLER_PCO_DATA_5: /// for product:
				if (!Utils.isValidMbn(Utils.getMDN(getApplicationContext()))) {
					prepareLookupOrder();
				}

				break;
		}
	}

	private void dealWithMdn() {
		String mdn = Utils.getMDN(getApplicationContext());
		if (DEBUG) {
			Log.e(TAG, "MSG_GET_MDN mdn=" + mdn);
		}
		if (Utils.isValidMbn(mdn)) {
			mH.removeMessages(MSG_GET_MDN_TIMEOUT);
			Message msg = mH.obtainMessage();
			msg.what = MSG_ACTIVATION_SUCCESS;
			msg.obj = mdn;
			mH.sendMessage(msg);
		} else {
			mH.sendEmptyMessageDelayed(MSG_GET_MDN, 1000);
		}
	}


	private void prepareLookupOrder() {
		if (!ActivationTracker.hasSimCard(getApplicationContext())) {
			Log.e(TAG, "sim card not ready..");
			mH.sendEmptyMessageDelayed(MSG_CHECK_SIM_STATE, 3000);
			return;
		}

		if (mLookupOrderTask != null && mLookupOrderTask.getStatus() == AsyncTask.Status.RUNNING) {
			return;
		}

		lookUpOrder();
	}


	class LookUpOrderTask extends AsyncTask<Void, Void, Integer> {
		LookUpOrderRequest request;

		LookUpOrderTask() {
			request = new LookUpOrderRequest();
		}

		protected Integer doInBackground(Void... args) {
			if (isCancelled() || request == null) {
				Log.e(TAG, "doInBackground no need to do work");
				return null;
			}

			Log.d(TAG, "LookUpOrderTask doInBackground..");
			Log.e(TAG, "imsi=" + mImsi + " imei=" + mImei);
			return request.lookupOrderReq(getApplicationContext(), mImsi, mImei);
		}

		protected void onPostExecute(Integer result) {
			if (isCancelled() || request == null || result == null) { // return when canceled or fragment invalid state
				Log.d(TAG, "isCancelled=" + isCancelled());
				return;
			}

			// retry for failure
			if (result != LookUpOrderRequest.MSG_PO_TIME_OUT) {
				if (request.getStatusCode() == null || VzwPoaRequest.STATUS_CODE_FAILURE.equalsIgnoreCase(request.getStatusCode())) {
					Log.e(TAG, "request=" + request + " , req retry times=" + mReqRetry + " , statusCode=" + request.getStatusCode());
					if (mReqRetry < REQ_RETRY_MAX_TIMES) {
						mReqRetry++;
						mH.removeMessages(MSG_RETRY_LOOKUP_ORDER_REQ);
						mH.sendEmptyMessageDelayed(MSG_RETRY_LOOKUP_ORDER_REQ, 9000);
						return;
					}
				}
			}

			mH.removeMessages(MSG_RETRY_LOOKUP_ORDER_REQ);

			Log.d(TAG, "LookUpOrderTask onPostExecute result=" + result);

			Message msg = mH.obtainMessage();
			msg.obj = request;

			switch (result) {
				case LookUpOrderRequest.MSG_PO_NEW_ORDER:
					msg.what = LookUpOrderRequest.MSG_PO_NEW_ORDER;
					break;
				case LookUpOrderRequest.MSG_PO_UPGRADE_ORDER:
					msg.what = LookUpOrderRequest.MSG_PO_UPGRADE_ORDER;
					break;
				case LookUpOrderRequest.MSG_PO_NOT_FOUND:
					msg.what = LookUpOrderRequest.MSG_PO_NOT_FOUND;
					break;
				case LookUpOrderRequest.MSG_PO_TIME_OUT:
					msg.what = LookUpOrderRequest.MSG_PO_TIME_OUT;
					break;
			}

			msg.sendToTarget();
		}

		@Override
		protected void onCancelled() {
			super.onCancelled();
			if (DEBUG) {
				Log.e(TAG, this + " onCancelled");
			}
			request.onCancelled();
			request = null;
		}
	}

	void lookUpOrder() {
		cancelLookupTaskIfNeeded();

		if (DEBUG) {
			//Toast.makeText(getApplicationContext(), "lookup Order ...", Toast.LENGTH_SHORT).show();
			Log.d(TAG, "lookup Order ...");
		}

		mH.removeMessages(MSG_RETRY_LOOKUP_ORDER_REQ);

		mLookupOrderTask = new LookUpOrderTask();
		mLookupOrderTask.execute();
	}

	private void cancelLookupTaskIfNeeded() {
		if ((mLookupOrderTask != null) && (mLookupOrderTask.getStatus() != AsyncTask.Status.FINISHED)) {
			if (DEBUG) {
				Log.e(TAG, "status=" + mLookupOrderTask.getStatus());
			}

			mLookupOrderTask.cancel(true);
			mLookupOrderTask = null;
		}
	}

	class ReleaseTask extends AsyncTask<Void, Void, Integer> {
		ReleaseOrderRequest request;

		public ReleaseTask() {
			request = new ReleaseOrderRequest();
		}

		@Override
		protected Integer doInBackground(Void... voids) {
			if (isCancelled() || request == null) { // return when canceled or fragment invalid state
				Log.d(TAG, "isCancelled=" + isCancelled());
				return null;
			}
			if (DEBUG) {
				Log.d(TAG, "ReleaseTask doInBackground..");
			}
			String imei = Utils.getImei(getApplicationContext());
			return request.releaseOrderReq(getApplicationContext(), imei, mRequestID, mCorrelationID);
		}

		@Override
		protected void onPostExecute(Integer result) {
			super.onPostExecute(result);
			if (isCancelled() || request == null || result == null) { // return when canceled
				Log.d(TAG, "isCancelled=" + isCancelled());
				return;
			}

			// deal with failure
			if (result != ReleaseOrderRequest.MSG_RELEASEORDER_REQUEST_TIMEOUT) {
				String statusCode = request.getStatusCode();
				if (statusCode == null || VzwPoaRequest.STATUS_CODE_FAILURE.equalsIgnoreCase(statusCode)) {
					String errorCode = request.getErrorCode();
					String errorMessage = request.getErrorMessage();
					if (DEBUG) {
						Log.e(TAG, "statusCode=" + statusCode + " ,errorCode=" + errorCode + " ,errorMessage=" + errorMessage);
					}

					if (VzwPoaRequest.matchAuthenticationFailure(errorCode, errorMessage) || VzwPoaRequest.matchSecurityFailure(errorCode, errorMessage) ||
							VzwPoaRequest.ERR_CODE_00013.equals(errorCode)) {
						Message msg = mH.obtainMessage();
						msg.what = ReleaseOrderRequest.MSG_RELEASEORDER_REQUEST_FAILURE;
						msg.obj = request;
						msg.sendToTarget();
						return;
					}


					Log.e(TAG, "req retry times=" + mReqRetry + " , statusCode=" + statusCode);
					// retry for failure
					if (mReqRetry < REQ_RETRY_MAX_TIMES) {
						mReqRetry++;
						mH.removeMessages(MSG_RETRY_RELEASE_ORDER_REQ);
						mH.sendEmptyMessageDelayed(MSG_RETRY_RELEASE_ORDER_REQ, 9000);
						return;
					}
				}
			}

			mH.removeMessages(MSG_RETRY_RELEASE_ORDER_REQ);

			Message msg = mH.obtainMessage();
			msg.obj = request;

			Log.d(TAG, "ReleaseTask onPostExecute result=" + result);
			switch (result) {
				case ReleaseOrderRequest.MSG_RELEASEORDER_REQUEST_SUCCESS:
					msg.what = MSG_RELEASEORDER_REQUEST_SUCCESS;
					break;
				case ReleaseOrderRequest.MSG_RELEASEORDER_REQUEST_FAILURE:
					msg.what = MSG_RELEASEORDER_REQUEST_FAILURE;
					break;
				case ReleaseOrderRequest.MSG_RELEASEORDER_REQUEST_TIMEOUT:
					msg.what = MSG_RELEASEORDER_REQUEST_TIMEOUT;
					break;
				case ReleaseOrderRequest.MSG_RELEASEORDER_REQUEST_FAILURE_UNKNOWN_HOST:
					msg.what = MSG_RELEASEORDER_REQUEST_FAILURE;
					break;
			}

			msg.sendToTarget();
		}

		@Override
		protected void onCancelled() {
			super.onCancelled();
			if (DEBUG) {
				Log.e(TAG, this + " onCancelled");
			}
			request.onCancelled();
			request = null;
		}
	}

	private void pendingOrderReleaseOrder() {
		cancelReleaseTask();

		mH.removeMessages(MSG_RETRY_RELEASE_ORDER_REQ);

		mReleaseTask = new ReleaseTask();
		mReleaseTask.execute();

		if (DEBUG) {
			//Toast.makeText(getApplicationContext(), "pendingOrderReleaseOrder", Toast.LENGTH_SHORT).show();
			Log.d(TAG, "pendingOrderReleaseOrder");
		}
	}

	private void cancelReleaseTask() {
		if (mReleaseTask != null && mReleaseTask.getStatus() != AsyncTask.Status.FINISHED) {
			if (DEBUG) {
				Log.e(TAG, "status=" + mReleaseTask.getStatus());
			}
			mReleaseTask.cancel(true);
			mReleaseTask = null;
		}
	}

	@SuppressLint("HandlerLeak")
	Handler mH = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			switch (msg.what) {
				case LookUpOrderRequest.MSG_PO_NEW_ORDER:
				case LookUpOrderRequest.MSG_PO_UPGRADE_ORDER:
					if (DEBUG) {
						Log.e(TAG, "handlePendingOrderFound what=" + msg.what);
					}
					handlePendingOrderFound((LookUpOrderRequest) msg.obj);
					break;
				case LookUpOrderRequest.MSG_PO_NOT_FOUND:
					if (DEBUG) {
						Log.e(TAG, "handlePendingOrderNotFound what=" + msg.what);
					}
					handlePendingOrderNotFound((LookUpOrderRequest) msg.obj);
					break;
				case LookUpOrderRequest.MSG_PO_TIME_OUT:
					if (DEBUG) {
						Log.e(TAG, "handlePendingOrderLookupTimeout what=" + msg.what);
					}
					handlePendingOrderLookupTimeout((LookUpOrderRequest) msg.obj);
					break;

				case MSG_RETRY_LOOKUP_ORDER_REQ:
					if (mPco == 0 || mPco == 5) {
						lookUpOrder();
					} else {
						Log.e(TAG, "error : wrong pco value");
					}

					break;
				case MSG_CHECK_SIM_STATE:
					prepareLookupOrder();
					break;
				case MSG_RELEASEORDER_REQUEST_SUCCESS:
					if (DEBUG) {
						Log.d(TAG, "po_release_success");
						//Toast.makeText(getApplicationContext(), "release order success", Toast.LENGTH_SHORT).show();
					}

					onReleaseOrderSuccess();
					break;

				case MSG_RELEASEORDER_REQUEST_FAILURE:
					if (DEBUG) {
						Log.d(TAG, "po_release_failed");
						//Toast.makeText(getApplicationContext(), "release order failed", Toast.LENGTH_SHORT).show();
					}
					onReleaseOrderFailed((ReleaseOrderRequest) msg.obj);
					break;

				case MSG_RELEASEORDER_REQUEST_TIMEOUT:
					if (DEBUG) {
						Log.d(TAG, "po_release_timeout");
						//Toast.makeText(getApplicationContext(), "release order timeout", Toast.LENGTH_SHORT).show();
					}
					onReleaseOrderFailed((ReleaseOrderRequest) msg.obj);
					break;
				case MSG_RETRY_RELEASE_ORDER_REQ:
					pendingOrderReleaseOrder();
					break;
				case MSG_HANDLE_AIRPLANE_MODE_CHANGED:
					handleAirplaneModeChanged();
					break;
				case MSG_ACTIVATION_SUCCESS:
					sendNotificationViaPhoneReceiver(ACTION_SHOW_ACTIVATION_SUCCESS);
					break;
				case MSG_GET_MDN:
					dealWithMdn();
					break;
				case MSG_GET_MDN_TIMEOUT:
					sendNotificationViaPhoneReceiver(ACTION_SHOW_ACTIVATION_FAILURE);
					break;
			}
		}
	};

	private void handleAirplaneModeChanged() {
		if (Utils.getAirplaneMode(getApplicationContext())) {
			Utils.setAirplaneMode(getApplicationContext(), false);  /// turn off

			// register for pco
			registerPoaSpecialReceiver();
			if (DEBUG) {
				Log.e(TAG, "register for pco activation");
			}
		} else {
			mH.sendEmptyMessageDelayed(MSG_HANDLE_AIRPLANE_MODE_CHANGED, 3000);
		}
	}

	private void onReleaseOrderFailed(ReleaseOrderRequest request) {
		sendNotificationViaPhoneReceiver(ACTION_SHOW_ACTIVATION_FAILURE);
	}

	private void onReleaseOrderSuccess() {
		// turn on  airplane mode
		Utils.setAirplaneMode(getApplicationContext(), true);

		// schedule to close it
		mH.sendEmptyMessageDelayed(MSG_HANDLE_AIRPLANE_MODE_CHANGED, 3000);
	}

	private void handlePendingOrderLookupTimeout(LookUpOrderRequest request) {

	}

	private void handlePendingOrderNotFound(LookUpOrderRequest request) {

	}

	private void handlePendingOrderFound(LookUpOrderRequest request) {
		if (VzwPoaRequest.ERR_CODE_00000.equals(request.getErrorCode())) {
			mCorrelationID = request.getCorrelationID();
			mRequestID = request.getRequestID();
			mSecurityQID = request.getSecurityQuestionID();
			mOrderType = request.getOrderType();

			if (DEBUG) {
				Log.d(TAG, "handlePendingOrderFound mSecurityQID =" + mSecurityQID + "\n mRequestID="
						+ mRequestID + "\n mCorrelationID=" + mCorrelationID + " ,mOrderType=" + mOrderType);
			}
		} else {
			Log.d(TAG, "order found but error code is " + request.getErrorCode());
		}

		sendNotificationViaPhoneReceiver(ACTION_ORDER_FOUND);
	}

	private void sendNotificationViaPhoneReceiver(String action) {
		if (DEBUG) {
			Log.d(TAG, "sendNotificationViaPhoneReceiver action=" + action);
		}

		Intent intent = new Intent();
		intent.setAction(action);
		intent.setPackage("com.android.phone");
		intent.setFlags(Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND); //qinyu add for background app
		getApplicationContext().sendBroadcast(intent);
	}


	@Override
	public void onDestroy() {
		super.onDestroy();
		mH.removeCallbacksAndMessages(null);
		unregisterPoaSpecialReceiver();
		cancelLookupTaskIfNeeded();
		cancelReleaseTask();
	}
}