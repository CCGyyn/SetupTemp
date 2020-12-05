package com.qualcomm.qti.setuptemp.fragments;

import android.app.Fragment;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemClock;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.qualcomm.qti.setuptemp.R;
import com.qualcomm.qti.setuptemp.event.ActivationStatusCallback;
import com.qualcomm.qti.setuptemp.event.ActivationTracker;
import android.content.Intent;
import com.qualcomm.qti.setuptemp.poa.LookUpOrderRequest;
import com.qualcomm.qti.setuptemp.poa.PoaConfig;
import com.qualcomm.qti.setuptemp.poa.VzwPendingOrderAuthenticationFragment;
import com.qualcomm.qti.setuptemp.poa.VzwPoaRequest;
import com.qualcomm.qti.setuptemp.poa.VzwPoaStatusFragment;
import com.qualcomm.qti.setuptemp.utils.Utils;

public class VzwSimCheckFragment extends FragmentCommon implements ActivationStatusCallback {
	private static final String TAG = VzwSimCheckFragment.class.getSimpleName();

	public static final String SIM_STATUS_KEY = "sim_status";
	public static final String SIM_MDN_KEY = "sim_mdn";
	public static final String SIM_FROM_NOTIFICATION_KEY = "from_notification";

	public static final int ACTION_SKIP_DISPLAY = -1;
	public static final int ACTION_SHOW_NO_SIM = 0;
	public static final int ACTION_SIM_NOT_READY = 1;
	public static final int ACTION_SHOW_SIM_ERROR = 2;
	public static final int ACTION_SIM_READY = 3;
	public static final int ACTION_SHOW_ACTIVATED = 4;
	public static final int ACTION_SHOW_NOT_ACTIVATED = 5;
	public static final int ACTION_SHOW_PLAN_SELECTION = 6;
	public static final int ACTION_SHOW_ACTIVATE_TIMEOUT = 7;
    public static final int ACTION_NON_VZW_SIM = 8;

	public static final int MSG_ACTION_NON_VZW_SIM_CHECK = 66;

	public static final long TIMEOUT = 60 * 1000 * 3;  //  3m
	public static final long TIMEOUT_LONG = 60 * 1000 * 5; // 5m

	public static final int MSG_RETRY_LOOKUP_ORDER_REQ = 1;
	public static final int REQ_RETRY_MAX_TIMES = 5;
	private int mReqRetry;

	private String mMdn = null;

	private TextView mTvPrepare;
	private View mLoadView;
	private long mLastTimeMs;
	private PowerManager.WakeLock mWakeLock;
	private int mPco = ActivationTracker.HANDLER_PCO_DATA_NONE;
	private String mSimDescription;
	private LookUpOrderTask mLookupOrderTask;
	private String mCorrelationID;
	private String mRequestID;
	private int mSecurityQID;
	private TelephonyManager mTelephonyManager;
	private String mImsi;
	private String mImei;

	@Override
	public String getTitleString() {
		return getString(R.string.phone_activation);
	}

	@Override
	protected int getLayoutResId() {
		return R.layout.fragment_sim_check;
	}

	@Override
	public void onInitContent(View root) {
		super.onInitContent(root);
		mTvPrepare = (TextView) root.findViewById(R.id.id_info_prepare_phone);
		mLoadView = root.findViewById(R.id.id_load_container);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		mLastTimeMs = System.currentTimeMillis();
		mTelephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

		// register status callback
		ActivationTracker.getInstance(getActivity()).registerActivationStatusCallback(this, TIMEOUT);
		postDelayed(() -> {
			if (DEBUG) Log.e(TAG, "long timeout for whatever state");
			if (ActivationTracker.hasSimCard(getActivity())) { // has sim
				startShowSimStatusFragment(ACTION_SHOW_ACTIVATE_TIMEOUT);  // for long timeout
			}
		}, TIMEOUT_LONG);

		acquireWakeLock();
		mReqRetry = 0;
	}

	private void acquireWakeLock() {
		PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
		mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
		mWakeLock.acquire(TIMEOUT_LONG);
	}

	private void releaseWakeLock() {
		if (mWakeLock != null) {
			mWakeLock.release();
			mWakeLock = null;
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		if (DEBUG) Log.d(TAG, TAG + " -> onResume");
		if (ActivationTracker.hasSimCard(getActivity())) {
			mTvPrepare.setText(getString(R.string.wait_while_activating));
			mLoadView.setVisibility(View.VISIBLE);
		} else {
			mTvPrepare.setText(getString(R.string.no_sim));
			mLoadView.setVisibility(View.INVISIBLE);
			setLeftLabel(getString(R.string.label_skip));
		}
	}

	@Override
	public void onLeftLabelClick(View v) {
		super.onLeftLabelClick(v);
		send_start_cloud(); //20190328 rwei start_cloud
		startFragmentPanel(VerizonCloudFragment.class.getName(), null,
				0, null, null, 0);
	}

	@Override
	public void onCenterLabelClick(View v) {
		super.onCenterLabelClick(v);
		if (DEBUG) {
			Toast.makeText(getActivity(), VzwSimCheckFragment.class.getName(), Toast.LENGTH_SHORT).show();
		}
	}

	@Override
	public void onRightLabelClick(View v) {
		super.onRightLabelClick(v);
		startShowSimStatusFragment(ACTION_SKIP_DISPLAY);
	}

	@Override
	public void onSimCardStatusChanged(int status, String description) {
		if (DEBUG) Log.e(TAG, "status=" + status + " ,description=" + description);
		mSimDescription = description;

		switch (description) {
			case ActivationTracker.SIM_DESCRIPTION_ABSENT:   // no sim
				//startShowSimStatusFragment(ACTION_SHOW_NO_SIM);
				//Toast.makeText(getActivity(),getString(R.string.no_sim_found),Toast.LENGTH_SHORT).show();
				setRightLabel(getString(R.string.label_next));
				mTvPrepare.setText(getString(R.string.no_sim));
				mLoadView.setVisibility(View.INVISIBLE);
				if (DEBUG) Log.d(TAG, "SIM_DESCRIPTION_ABSENT");
				break;
			case ActivationTracker.SIM_DESCRIPTION_NOT_READY:
				if (DEBUG) Log.d(TAG, "SIM_DESCRIPTION_NOT_READY");
				break;
			case ActivationTracker.SIM_DESCRIPTION_READY: // ready
				if (DEBUG) Log.d(TAG, "SIM_DESCRIPTION_READY");
				mTvPrepare.setText(getString(R.string.wait_while_activating));
				mLoadView.setVisibility(View.VISIBLE);
				checkIfNonVzwSimInterval();
				break;
			case ActivationTracker.SIM_DESCRIPTION_ERROR:
				if (DEBUG) Log.d(TAG, "SIM_DESCRIPTION_ERROR");
				startWhatPage(ACTION_SHOW_SIM_ERROR);
				break;
		}
	}

	    private final String vzwSimMccMnc = "311480";
    private void checkIfNonVzwSimInterval() {
		String operator = mTelephonyManager.getSimOperator();
		Log.d(TAG, "checkIfNonVzwSimInterval operator=" + operator);

		if (TextUtils.isEmpty(operator)) {
			Log.d(TAG, "checkIfNonVzwSimInterval : operator not available, retry later");
			sendMessageDelayed(getInternalHandler().obtainMessage(MSG_ACTION_NON_VZW_SIM_CHECK), 5 * 1000);
		} else{
			if (vzwSimMccMnc.contains(operator)) {
				Log.d(TAG, "checkIfNonVzwSimInterval : current is vzw sim");
			} else {
				Log.d(TAG, "checkIfNonVzwSimInterval : current is non vzw sim");
				startWhatPage(ACTION_NON_VZW_SIM);
			}
		}
	}

	@Override
	public void onActivateSuccessful(String mdn, int pco) {
		if (PoaConfig.isDebuggable()) return;  //// temp
		mMdn = mdn;
		if (DEBUG) Log.e(TAG, "onActivateSuccessful : mbn=" + mdn + " ,pco" + pco);
		setActivated(true);
		startWhatPage(ACTION_SHOW_ACTIVATED);
	}

	@Override
	public void onActivateFailed(int reason, int pco) {
		//startShowSimStatusFragment(ACTION_SHOW_NOT_ACTIVATED);
		if (DEBUG) Log.e(TAG, "onActivateFailed : " + reason);
		setActivated(false);
		lunchNextPage();  // just skip
	}

	@Override
	public void onActivateTimeout() {
		if (DEBUG) Log.e(TAG, "onActivateTimeout");
		startWhatPage(ACTION_SHOW_ACTIVATE_TIMEOUT);
	}

	@Override
	public void onActivateWithMBB(String mdn, int pco) {
		if (DEBUG) Log.e(TAG, "onActivateWithMBB pco=" + pco);
		if (pco == 5) {
			postDelayed(this::startPcoCheck, 100);
		}
	}

	private void startWhatPage(int what) {
		if (System.currentTimeMillis() - mLastTimeMs > 5000) {
			startShowSimStatusFragment(what);
		} else {
			postDelayed(() -> startShowSimStatusFragment(what), 5000);
		}
	}

	private void startShowSimStatusFragment(int simStatus) {
		startShowSimStatusFragmentForResult(null, simStatus);
	}

	private void startShowSimStatusFragmentForResult(Fragment resultTo, int simStatus) {
		clearIfNeeded();
		// start target
		Bundle args = new Bundle();
		args.putInt(SIM_STATUS_KEY, simStatus);
		args.putString(SIM_MDN_KEY, mMdn);
		startFragmentPanel(ShowSimStatusFragment.class.getName(), args,
				0, null, resultTo, 1);
	}

	private void clearIfNeeded() {
		releaseWakeLock();
		ActivationTracker.getInstance(getActivity()).unregisterActivationStatusCallback(this);
		getInternalHandler().removeCallbacksAndMessages(null);

		if (mLookupOrderTask != null && (mLookupOrderTask.getStatus() != AsyncTask.Status.FINISHED)) {
			mLookupOrderTask.cancel(true);
			mLookupOrderTask = null;
		}
	}

	private void lunchNextPage() {
		if (ActivationTracker.hasSimCard(getActivity())) {
			send_start_cloud(); //20190328 rwei start_cloud
			startFragmentPanel(VerizonCloudFragment.class.getName(), null);
		} else {
			startFragmentPanel(ReadyFragment.class.getName(), null);
		}
	}

	@Override
	protected boolean onNextKeyPressed(KeyEvent event) {
		if (!ActivationTracker.hasSimCard(getActivity())) {
			lunchNextPage();
		}
		return true;
	}

	@Override
	protected boolean onBackKeyPressd(KeyEvent event) {
		if (!ActivationTracker.hasSimCard(getActivity())) {  // cannot skip when sim is detected
			lunchNextPage();
		}
		return true;
	}


	@Override
	public void onDestroy() {
		super.onDestroy();
		clearIfNeeded();
	}
	//20190328 rwei start_cloud
	private void send_start_cloud(){
		Intent intent = new Intent("com.vcast.mediamanager.START_CLOUD");
		intent.setPackage("com.vcast.mediamanager");
		intent.setFlags(Intent.FLAG_RECEIVER_FOREGROUND);
		getActivity().sendBroadcast(intent, "com.vcast.mediamanager.CLOUD_PERMISSION");
		System.out.println("===========---=========>>>>START_CLOUD");
	}

	@Override
	public void onPcoReceived(String mdn, int pco) {
		if (pco >= 0) {
			mPco = pco;
		}
		Log.e(TAG, "onPcoReceived pco=" + pco + " ,mdn=" + mdn);

		if (pco == 0 && PoaConfig.isDebuggable()) {
			postDelayed(this::startPcoCheck, 100);
		}
	}

	private void startPcoCheck() {
		if (ActivationTracker.SIM_DESCRIPTION_ABSENT.equals(mSimDescription) ||
				ActivationTracker.SIM_DESCRIPTION_ERROR.equals(mSimDescription)) {
			Log.e(TAG, "error sim state");
			return; // absent or error
		}

		mImsi = Utils.getImsi(getActivity());
		mImei = Utils.getImei(getActivity());

		if (DEBUG) {
			Log.e(TAG, "startPcoCheck pco=" + mPco);
		}

		/// if task is running , just return
		if (mLookupOrderTask != null && mLookupOrderTask.getStatus() == AsyncTask.Status.RUNNING) {
			return;
		}

		processPcoValues(getActivity(), mPco);
	}

	void processPcoValues(Context context, int pco) {
		if (pco <= ActivationTracker.HANDLER_PCO_DATA_NONE) {
			return; // invalid pco
		}

		if (PoaConfig.isDebuggable()) {
			if (pco == ActivationTracker.HANDLER_PCO_DATA_0) { // pco 0 for test
				lookUpOrder();
			}
		} else if (pco == ActivationTracker.HANDLER_PCO_DATA_5) {  // pco 5 for product
				lookUpOrder();
		}
	}

	void lookUpOrder() {
		if ((mLookupOrderTask != null) && (mLookupOrderTask.getStatus() != AsyncTask.Status.FINISHED)) {
			if (DEBUG) {
				Log.e(TAG, "status=" + mLookupOrderTask.getStatus());
			}

			mLookupOrderTask.cancel(true);
			mLookupOrderTask = null;
		}

		if (DEBUG) {
			//Toast.makeText(getActivity(), "lookup Order ...", Toast.LENGTH_SHORT).show();
			Log.d(TAG, "lookup Order ...");
		}

		removeMessages(MSG_RETRY_LOOKUP_ORDER_REQ);

		mLookupOrderTask = new LookUpOrderTask();
		mLookupOrderTask.execute();
	}

	class LookUpOrderTask extends AsyncTask<Void, Void, Integer> {
		LookUpOrderRequest request;
		LookUpOrderTask() {
			request = new LookUpOrderRequest();
		}

		protected Integer doInBackground(Void... args) {
			if (isCancelled() || isInvalidFragmentState() || request == null) {
				Log.e(TAG, "doInBackground no need to do work");
				return null;
			}

			Log.d(TAG, "LookUpOrderTask doInBackground..");
			Log.e(TAG, "imsi=" + mImsi + " imei=" + mImei);
			return request.lookupOrderReq(getActivity(), mImsi, mImei);
		}

		protected void onPostExecute(Integer result) {
			if (isCancelled() || isInvalidFragmentState() || request == null || result == null) { // return when canceled or fragment invalid state
				Log.d(TAG, "isCancelled=" + isCancelled());
				return;
			}

			// retry for failure
			if (result != LookUpOrderRequest.MSG_PO_TIME_OUT) {
				if (request.getStatusCode() == null || VzwPoaRequest.STATUS_CODE_FAILURE.equalsIgnoreCase(request.getStatusCode())) {
					Log.e(TAG, "request=" + request + " , req retry times=" + mReqRetry + " , statusCode=" + request.getStatusCode());
					if (mReqRetry < REQ_RETRY_MAX_TIMES) {
						mReqRetry++;
						removeMessages(MSG_RETRY_LOOKUP_ORDER_REQ);
						getInternalHandler().sendEmptyMessageDelayed(MSG_RETRY_LOOKUP_ORDER_REQ, 9000);
						return;
					}
				}
			}

			removeMessages(MSG_RETRY_LOOKUP_ORDER_REQ);

			Log.d(TAG, "LookUpOrderTask onPostExecute result=" + result);

			Handler handler = getInternalHandler();
			Message msg = handler.obtainMessage();
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

	/***
	 *  invalid fragment state currently
	 * @return true, currently fragment state is invalid
	 */
	private boolean isInvalidFragmentState() {
		if (DEBUG) {
			Log.e(TAG, "isVisible=" + isVisible() + " ,isRemoving=" + isRemoving() + " ,isDetached=" + isDetached());
		}
		return !isVisible() || isRemoving() || isDetached();
	}

	@Override
	public void onStop() {
		super.onStop();
		Log.e(TAG, "onStop isVisible=" + isVisible());
	}

	@Override
	public boolean handleMessage(Message msg) {

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
				Log.e(TAG, "retry lookup order req what=" + msg.what);

				if (mPco == 0 || mPco == 5) {
					if (!isInvalidFragmentState()) {
						lookUpOrder();
					}
				} else {
					Log.e(TAG, "error : wrong pco value");
				}
				break;
			case MSG_ACTION_NON_VZW_SIM_CHECK:
				if (DEBUG) {
					Log.e(TAG, "handle msg checkIfNonVzwSimInterval what=" + msg.what);
				}
				checkIfNonVzwSimInterval();
				break;
		}
		return super.handleMessage(msg);
	}

	@Override
	public void startFragmentPanel(String fragmentClass, Bundle args) {
		clearIfNeeded();
		super.startFragmentPanel(fragmentClass, args);
	}

	private void handlePendingOrderFound(LookUpOrderRequest request) {
		int rc = request.getAccountRestricted();
		Log.d(TAG, "mLookupReq.getAccountRestricted rc  =" + rc);
		if (rc == 0) { // Restricted Ac
			Log.e(TAG, "handlePendingOrderFound Restricted Ac");
			String errorCode = request.getErrorCode();
			Log.d(TAG, "handlePendingOrderFound: mLookupReq.getErrorCode=" + errorCode + " ,getOrderType=" + request.getOrderType());
			Bundle args = new Bundle();
			args.putInt(VzwPoaStatusFragment.POA_STATUS_KEY, VzwPoaStatusFragment.NewActOrderRestricted);
			args.putInt(VzwPoaStatusFragment.POA_ORDER_TYPE_KEY, request.getOrderType());
			startFragmentPanel(VzwPoaStatusFragment.class.getName(), args);
			return;
		}

		String errorCode = request.getErrorCode();
		if (DEBUG) {
			Log.e(TAG, "handlePendingOrderFound errorCode=" + errorCode);
		}

		if (VzwPoaRequest.ERR_CODE_00000.equals(errorCode)) {
			mCorrelationID = request.getCorrelationID();
			mRequestID = request.getRequestID();
			mSecurityQID = request.getSecurityQuestionID();
			Bundle args = new Bundle();
			args.putString("mCorrelationID", mCorrelationID);
			args.putString("mRequestID", mRequestID);
			args.putInt("mSecurityQuestionID", mSecurityQID);
			args.putInt("mOrderType",request.getOrderType());
			Log.e(TAG, "mOrderType=" + request.getOrderType());
			startFragmentPanel(VzwPendingOrderAuthenticationFragment.class.getName(), args);
			Log.d(TAG, "handlePendingOrderFound mSecurityQID =" + mSecurityQID + "\n mRequestID=" + mRequestID + "\n mCorrelationID=" + mCorrelationID);

			Log.d(TAG, "Billing Password Exist =" + (mSecurityQID == 1));
		} else {
			Log.d(TAG, "order found but error code is " + rc);
		}
	}

	private void handlePendingOrderLookupTimeout(LookUpOrderRequest request) {
		String errorCode = request.getErrorCode();
		if (DEBUG) {
			//Toast.makeText(getActivity(), "lookup order timeout errorCode=" + errorCode, Toast.LENGTH_SHORT).show();
			Log.d(TAG, "lookup order timeout errorCode=" + errorCode);
			Log.d(TAG, "handlePendingOrderLookupTimeout: mLookupReq.getErrorCode=" + errorCode + " ,getOrderType=" + request.getOrderType());
		}
		Bundle args = new Bundle();
		args.putInt(VzwPoaStatusFragment.POA_STATUS_KEY, VzwPoaStatusFragment.LookupOrderTimeout);
		args.putInt(VzwPoaStatusFragment.POA_ORDER_TYPE_KEY, request.getOrderType());
		startFragmentPanel(VzwPoaStatusFragment.class.getName(), args);
	}

	void handlePendingOrderNotFound(LookUpOrderRequest request) {
		String errorCode = request.getErrorCode();
		if (DEBUG) {
			//Toast.makeText(getActivity(), "no order found errorCode=" + errorCode, Toast.LENGTH_SHORT).show();
			Log.d(TAG, "handlePendingOrderNotFound: mLookupReq.getErrorCode=" + errorCode + " ,getOrderType=" + request.getOrderType());
		}
		Bundle args = new Bundle();
		args.putInt(VzwPoaStatusFragment.POA_STATUS_KEY, VzwPoaStatusFragment.NewActOrderNotFound);
		args.putInt(VzwPoaStatusFragment.POA_ORDER_TYPE_KEY, request.getOrderType());
		startFragmentPanel(VzwPoaStatusFragment.class.getName(), args);
	}
}


