package com.qualcomm.qti.setuptemp.poa;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Message;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.qualcomm.qti.setuptemp.DefaultActivity;
import com.qualcomm.qti.setuptemp.R;
import com.qualcomm.qti.setuptemp.utils.Utils;
import com.qualcomm.qti.setuptemp.view.ScrollViewExt;


public class VzwPendingOrderActivationFragment extends BasePoaFragment implements View.OnClickListener, DialogInterface.OnDismissListener {
	private static final String TAG = VzwPendingOrderActivationFragment.class.getSimpleName();
	public static final double REQ_RETRY_MAX_TIMES = 5;
	public static final int MSG_HANDLE_AIRPLANE_MODE_CHANGED = 9;
	public static final int MSG_GET_MDN = 10;
	private static final int MSG_RETRY_RELEASE_ORDER_REQ = 11;
	public static final int MSG_ACTIVATION_SUCCESS = 12;
	public static final int MSG_AUTO_ACTIVATION = 13;

	public static final int GET_MDN_DELAY_MILLIS = 3500;
	public static final int ACTIVATION_SUCCESS_DISPLAY_DELAY_MILLIS = 5000;
	public static final int AIRPLANE_MODE_CHANGED_DELAY_MILLIS = 3000;

	private ProgressBar mProgressIndicator;
	private Button mBtnActivateNow;
	private TextView mTvNotice;
	private String mCorrelationID;
	private String mRequestID;
	private ReleaseTask mReleaseTask;
	private int mPco;
	private int mReqRetry = 0;
	private ProgressDialog mProgressDialog;
	private int mOrderType;
	private PcoChangeReceiver mPcoReceiver;
	private ScrollViewExt mScrollView;
	private String mSecurityQuestionID;

	@Override
	protected int getContentLayoutResId() {
		return R.layout.fragment_po_activate;
	}

	@Override
	public String getTitleString() {
		return getString(R.string.ready_to_activate);
	}

	@Override
	protected void initContent(ViewGroup container) {
		mTvNotice = (TextView) container.findViewById(R.id.tv_notice_text);
		mProgressIndicator = (ProgressBar) container.findViewById(R.id.act_progress_indicator);
		mBtnActivateNow = (Button) container.findViewById(R.id.activate_now_button);
		mBtnActivateNow.setOnClickListener(this);

		mTvNotice.setText(R.string.activation_last_minutes);  //// for default
		initActivationButtons();

		mScrollView = (ScrollViewExt) container.findViewById(R.id.po_sv_container);
		mScrollView.setScrollViewListener(new ScrollViewExt.IScrollViewChangedListener() {
			@Override
			public void onScrolledToBottom() {
				requestFocusForFuncBtns();
			}
		});
	}

	@Override
	public boolean onKeyUp(KeyEvent event) {
		switch (event.getKeyCode()) {
			case KeyEvent.KEYCODE_DPAD_DOWN:
				Log.d(TAG, "mScrollView.canScrollVertically" + mScrollView.canScrollVertically(1) + " : " + mScrollView.canScrollVertically(-1));
				if (mScrollView.isBottomReached()) {
					requestFocusForFuncBtns();
				} else if (getEmergencyBtn().getVisibility() == View.VISIBLE && getEmergencyBtn().hasFocus() ||
						getRightFuncBtn().getVisibility() == View.VISIBLE && getRightFuncBtn().hasFocus()) {
					if (mScrollView.canScrollVertically(1)) {
						mScrollView.pageScroll(ScrollView.FOCUS_DOWN);
						requestFocusForFuncBtns();
					}
				}

				break;
		}
		return super.onKeyUp(event);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		Bundle args = getArguments();
		if (args != null) {
			mCorrelationID = args.getString("mCorrelationID");
			mRequestID = args.getString("mRequestID");
			mOrderType = args.getInt("mOrderType");
			mSecurityQuestionID = args.getString("mSecurityQuestionID");
			setNoteTextByOrderType();
		}

		if (DEBUG) {
			Log.e(TAG, "onActivityCreated mOrderType=" + mOrderType + " ,mRequestID=" + mRequestID + " ,mCorrelationID=" + mCorrelationID);
		}

		if (getActivity() instanceof DefaultActivity) {
			boolean callerNotification = ((DefaultActivity) getActivity()).isCallerNotification();
			if (DEBUG) {
				Log.d(TAG, "callerNotification=" + callerNotification);
			}
			if (callerNotification) {
				sendEmptyMessage(MSG_AUTO_ACTIVATION);
			}
		}
	}

	private void initActivationButtons() {
		setRightFuncBtnVisibility(View.VISIBLE);
		setRightFuncBtnOnClickListener(this);
		getRightFuncBtn().requestFocus();
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
			case R.id.function_right_button:
			case R.id.activate_now_button:
				if (DEBUG) {
					Log.d(TAG, "onClickActivateNow");
				}
				onClickActivateNow();
				break;
		}
	}

	private void onClickActivateNow() {
		if (mOrderType == LookUpOrderRequest.MSG_PO_NEW_ORDER) { //// Get Phone Number
			setTitle(getString(R.string.getting_phone_number));
			mTvNotice.setText(R.string.wait_for_get_number);
		} else {  //// update ,  deactivation old phone Number
			setTitle(getString(R.string.deactivate_old_phone));
			mTvNotice.setText(R.string.turn_off_for_deactivate);
		}

		setRightFuncBtnVisibility(View.GONE);
		mReqRetry = 0;
		if (DEBUG) {
			//Toast.makeText(getContext(), R.string.activate_now, Toast.LENGTH_SHORT).show();
		}
		pendingOrderReleaseOrder();
	}

	private void setNoteTextByOrderType() {
		Log.e(TAG, "mOrderType = " + mOrderType);
		switch (mOrderType) {
			case LookUpOrderRequest.MSG_PO_NEW_ORDER:   /// for new order
				mTvNotice.setText(getString(R.string.activation_last_minutes));
				break;
			case LookUpOrderRequest.MSG_PO_UPGRADE_ORDER:  /// for update order
				mTvNotice.setText(getString(R.string.prepare_activate_your_phone));
				break;
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		requestFocusForFuncBtns();
	}

	private void requestFocusForFuncBtns() {
		if (getRightFuncBtn().getVisibility() == View.VISIBLE) {
			getRightFuncBtn().requestFocus();
		} else if (getEmergencyBtn().getVisibility() == View.VISIBLE) {
			getEmergencyBtn().requestFocus();
		}
	}

	@Override
	protected boolean onNextKeyPressed(KeyEvent event) {
		//startFragmentPanel(ReadyFragment.class.getName(), null);
		return super.onNextKeyPressed(event);
	}

	private void pendingOrderReleaseOrder() {
		//showVerifyDialog();
		mProgressIndicator.setVisibility(View.VISIBLE);
		cancelReleaseTask();

		removeMessages(MSG_RETRY_RELEASE_ORDER_REQ);

		mReleaseTask = new ReleaseTask();
		mReleaseTask.execute();

		if (DEBUG) {
			//Toast.makeText(getContext(), "pendingOrderReleaseOrder", Toast.LENGTH_SHORT).show();
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

	private void showVerifyDialog() {
		if (mProgressDialog == null) {
			mProgressDialog = new ProgressDialog(getActivity());
			mProgressDialog.setOnDismissListener(this);
			mProgressDialog.setMessage(getString(R.string.activate_now));
		}

		if (!mProgressDialog.isShowing()) {
			mProgressDialog.show();
		}
	}

	@Override
	public void onDismiss(DialogInterface dialog) {
		removeMessages(MSG_RETRY_RELEASE_ORDER_REQ);
		cancelReleaseTask();
	}


	class ReleaseTask extends AsyncTask<Void, Void, Integer> {
		ReleaseOrderRequest request;

		public ReleaseTask() {
			request = new ReleaseOrderRequest();
		}

		@Override
		protected Integer doInBackground(Void... voids) {
			if (isCancelled() || isInvalidFragmentState() || request == null) { // return when canceled or fragment invalid state
				Log.d(TAG, "isCancelled=" + isCancelled());
				return null;
			}
			if (DEBUG) {
				Log.d(TAG, "ReleaseTask doInBackground..");
			}
			String imei = Utils.getImei(getActivity());
			return request.releaseOrderReq(getContext(), imei, mRequestID, mCorrelationID);
		}

		@Override
		protected void onPostExecute(Integer result) {
			super.onPostExecute(result);
			if (isCancelled() || isInvalidFragmentState() || request == null || result == null) { // return when canceled or fragment invalid state
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
						Message msg = getInternalHandler().obtainMessage();
						msg.what = ReleaseOrderRequest.MSG_RELEASEORDER_REQUEST_FAILURE;
						msg.obj = request;
						msg.sendToTarget();
						return;
					}


					Log.e(TAG, "req retry times=" + mReqRetry + " , statusCode=" + statusCode);
					// retry for failure
					if (mReqRetry < REQ_RETRY_MAX_TIMES) {
						mReqRetry++;
						removeMessages(MSG_RETRY_RELEASE_ORDER_REQ);
						getInternalHandler().sendEmptyMessageDelayed(MSG_RETRY_RELEASE_ORDER_REQ, 9000);
						return;
					}
				}
			}

			removeMessages(MSG_RETRY_RELEASE_ORDER_REQ);
//			if (mProgressDialog != null) {
//				mProgressDialog.dismiss();
//			}

			Message msg = getInternalHandler().obtainMessage();
			msg.obj = request;

			Log.d(TAG, "ReleaseTask onPostExecute result=" + result);
			switch (result) {
				case ReleaseOrderRequest.MSG_RELEASEORDER_REQUEST_SUCCESS:
					msg.what = ReleaseOrderRequest.MSG_RELEASEORDER_REQUEST_SUCCESS;
					break;
				case ReleaseOrderRequest.MSG_RELEASEORDER_REQUEST_FAILURE:
					msg.what = ReleaseOrderRequest.MSG_RELEASEORDER_REQUEST_FAILURE;
					break;
				case ReleaseOrderRequest.MSG_RELEASEORDER_REQUEST_TIMEOUT:
					msg.what = ReleaseOrderRequest.MSG_RELEASEORDER_REQUEST_TIMEOUT;
					break;
				case ReleaseOrderRequest.MSG_RELEASEORDER_REQUEST_FAILURE_UNKNOWN_HOST:
					msg.what = ReleaseOrderRequest.MSG_RELEASEORDER_REQUEST_FAILURE;
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

	@Override
	protected void clearIfNeeded() {
		super.clearIfNeeded();
		if (mPcoReceiver != null) {
			unregisterReceiver(mPcoReceiver);
			mPcoReceiver = null;
		}
	}

	@Override
	public boolean handleMessage(Message msg) {
		boolean consumed = false;

		switch (msg.what) {
			case ReleaseOrderRequest.MSG_RELEASEORDER_REQUEST_SUCCESS:
				consumed = true;
				if (DEBUG) {
					Log.d(TAG, "po_release_success");
					//Toast.makeText(getContext(), "release order success", Toast.LENGTH_SHORT).show();
				}

				onReleaseOrderSuccess();
				break;

			case ReleaseOrderRequest.MSG_RELEASEORDER_REQUEST_FAILURE:
				consumed = true;
				if (DEBUG) {
					Log.d(TAG, "po_release_failed");
					//Toast.makeText(getContext(), "release order failed", Toast.LENGTH_SHORT).show();
				}

				onReleaseOrderFailed((ReleaseOrderRequest) msg.obj);
				break;

			case ReleaseOrderRequest.MSG_RELEASEORDER_REQUEST_TIMEOUT:
				consumed = true;
				if (DEBUG) {
					Log.d(TAG, "po_release_timeout");
					//Toast.makeText(getContext(), "release order timeout", Toast.LENGTH_SHORT).show();
				}

				mProgressIndicator.setVisibility(View.GONE);
				showPoaStatus(VzwPoaStatusFragment.ReleaseOrderTimeout);
				break;

			case MSG_RETRY_RELEASE_ORDER_REQ:
				if (!isInvalidFragmentState()) {
					pendingOrderReleaseOrder();
				}
				break;

			case MSG_HANDLE_AIRPLANE_MODE_CHANGED:
				handleAirplaneModeChanged();
				break;

			case MSG_GET_MDN:
				dealWithMdn();
				break;

			case MSG_ACTIVATION_SUCCESS:
				String mdn = (String) msg.obj;
				displayActivationSuccess(mdn);
				break;
			case MSG_AUTO_ACTIVATION:
				if (DEBUG) {
					Log.d(TAG, "auto activation");
				}
				onClickActivateNow();
				break;
		}

		return consumed;
	}

	private void displayActivationSuccess(String mdn) {
		mProgressIndicator.setVisibility(View.GONE);

		Bundle args = new Bundle();
		args.putString("mdn", mdn);
		int value = VzwPoaStatusFragment.NewActReleaseOrderSuccess;
		if (mOrderType == LookUpOrderRequest.MSG_PO_UPGRADE_ORDER && LookUpOrderRequest.SECURITY_QUESTION_ID_001.equals(mSecurityQuestionID)) {
			value = VzwPoaStatusFragment.UpgradeReleaseSuccess5CharAccountPIN;
		}
		args.putInt(VzwPoaStatusFragment.POA_STATUS_KEY, value);
		args.putInt(VzwPoaStatusFragment.POA_ORDER_TYPE_KEY, mOrderType);
		startFragmentPanel(VzwPoaStatusFragment.class.getName(), args);
		if (DEBUG) {
			Log.e(TAG, "MSG_ACTIVATION_SUCCESS mdn=" + mdn);
		}
	}

	private void handleAirplaneModeChanged() {
		if (Utils.getAirplaneMode(getContext())) {
			Utils.setAirplaneMode(getContext(), false);  /// turn off
			mProgressIndicator.setVisibility(View.VISIBLE);

			// register for pco
			if (mPcoReceiver == null) {
				mPcoReceiver = new PcoChangeReceiver();
			}
			IntentFilter filter = new IntentFilter(Utils.ACTION_PCO_CHANGE);
			registerReceiver(mPcoReceiver, filter);
			if (DEBUG) {
				Log.e(TAG, "register for pco activation");
			}
		} else {
			sendEmptyMessageDelayed(MSG_HANDLE_AIRPLANE_MODE_CHANGED, 3000);
		}
	}

	private void handleReleaseFailError(String errorCode, String errorMessage, ReleaseOrderRequest request) {
		if (DEBUG) {
			Log.e(TAG, "handleReleaseFailError errorCode=" + errorCode + " ,errorMessage=" + errorMessage);
		}

		int whatCase = -1;
		boolean update = mOrderType == LookUpOrderRequest.MSG_PO_UPGRADE_ORDER;
		if (VzwPoaRequest.matchAuthenticationFailure(errorCode, errorMessage)) {  /// for auth error
			Log.d(TAG, "auth error occurred");
			// show auth error
			whatCase = update ? VzwPoaStatusFragment.UpgradeReleaseOrderAuthFailed :
					VzwPoaStatusFragment.NewActReleaseOrderFailedAuthError;
		}

		if (VzwPoaRequest.matchSecurityFailure(errorCode, errorMessage)) {  /// for security error
			Log.e(TAG, "security error occurred");
			// show security error
			whatCase = VzwPoaStatusFragment.Lost_and_Stolen_Device_or_SIM;
		}

		if (VzwPoaRequest.ERR_CODE_00013.equals(errorCode)) { /// for PendingProvision- Error Code 00013
			Log.e(TAG, "pending provision error occurred");
			/// show pending provision error
			whatCase = VzwPoaStatusFragment.PendingProvisionErrorCode00013;
		}

		if (VzwPoaRequest.ERR_CODE_00001.equals(errorCode)) {  //// general err
			Log.e(TAG, "general error occurred");

			whatCase = update ? VzwPoaStatusFragment.UpgradeReleaseOrderCorrelationIdIncorrect :
					VzwPoaStatusFragment.NewActReleaseOrderCoorrelIDIncorrect;
		}

		if (whatCase == -1) {
			if (request.getResponseStatus() == ReleaseOrderRequest.MSG_RELEASEORDER_REQUEST_FAILURE_UNKNOWN_HOST) {
				// just for unknown host
				whatCase = VzwPoaStatusFragment.ReleaseOrderTimeout;
			}
		}
		if (whatCase != -1) {
			showPoaStatus(whatCase);
		}
		Log.e(TAG, "handleReleaseFailError whatCase=" + whatCase);
	}

	private void handleReleaseFailNoError() {
		Log.e(TAG, "handleReleaseFailNoError");
		int whatCase = -1;
		boolean update = mOrderType == LookUpOrderRequest.MSG_PO_UPGRADE_ORDER;
		whatCase = update ? VzwPoaStatusFragment.UpgradeReleaseOrderFailedOrderNotExist
				: VzwPoaStatusFragment.NewActReleaseOrderPendingNotExist;
		showPoaStatus(whatCase);
		if (DEBUG) {
			Log.e(TAG, "handleReleaseFailNoError whatCase=" + whatCase);
		}
	}

	private void onReleaseOrderFailed(ReleaseOrderRequest request) {
		mProgressIndicator.setVisibility(View.GONE);

		String errorCode = request.getErrorCode();
		String errorMessage = request.getErrorMessage();
		if (VzwPoaRequest.STATUS_CODE_SUCCESS.equals(request.getStatusCode()) && VzwPoaRequest.ERR_CODE_00000.equals(errorCode)) {
			handleReleaseFailNoError();
		} else {
			handleReleaseFailError(errorCode, errorMessage,request);
		}
	}

	private void onReleaseOrderSuccess() {
		mProgressIndicator.setVisibility(View.GONE);

		if (mOrderType == LookUpOrderRequest.MSG_PO_NEW_ORDER) {  ////Preparing to Activate Your Phone
			setTitle(getString(R.string.po_prepare_to_activate));
			mTvNotice.setText(R.string.po_setup_email_cloud);
		} else {  /// transfer number
			setTitle(getString(R.string.transferring_phone_umber));
			mTvNotice.setText(R.string.transferring_from_existing_number);
		}

		resetModem();
	}

	private void resetModem() {
		// turn on  airplane mode
		Utils.setAirplaneMode(getContext(), true);

		// schedule to close it
		sendEmptyMessageDelayed(MSG_HANDLE_AIRPLANE_MODE_CHANGED, AIRPLANE_MODE_CHANGED_DELAY_MILLIS);
	}

	private class PcoChangeReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			int pco_data = intent.getIntExtra("pco_data", -1);
			if (DEBUG) {
				Log.e(TAG, "PcoChangeReceiver onReceive pco_data=" + pco_data);
			}

			if (pco_data >= 0) {
				mPco = pco_data;
			}

			if (mPco == 0) {
				dealWithMdn();
			}
		}
	}

	private void dealWithMdn() {
		String mdn = Utils.getMDN(getContext());
		if (DEBUG) {

			Log.e(TAG, "MSG_GET_MDN mdn=" + mdn);
		}
		if (Utils.isValidMbn(mdn)) {
			Message msg = getInternalHandler().obtainMessage();
			msg.what = MSG_ACTIVATION_SUCCESS;
			msg.obj = mdn;
			sendMessageDelayed(msg, ACTIVATION_SUCCESS_DISPLAY_DELAY_MILLIS);  // delay secs to show the state
		} else {
			sendEmptyMessageDelayed(MSG_GET_MDN, GET_MDN_DELAY_MILLIS);
		}
	}

	private void showPoaStatus(int status) {
		Bundle args = new Bundle();
		args.putInt(VzwPoaStatusFragment.POA_STATUS_KEY, status);
		args.putInt(VzwPoaStatusFragment.POA_ORDER_TYPE_KEY, mOrderType);
		startFragmentPanel(VzwPoaStatusFragment.class.getName(), args);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		clearIfNeeded();
	}
}
