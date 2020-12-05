package com.qualcomm.qti.setuptemp.poa;

import android.content.Intent;
import android.os.Bundle;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.text.Html;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.qualcomm.qti.setuptemp.R;
import com.qualcomm.qti.setuptemp.fragments.VerizonCloudFragment;
import com.qualcomm.qti.setuptemp.utils.Utils;

import java.util.Locale;

public class VzwPoaStatusFragment extends BasePoaFragment {
	private static final String TAG = VzwPoaStatusFragment.class.getSimpleName();
	public static final String POA_STATUS_KEY = "poa_status";
	public static final String POA_ORDER_TYPE_KEY = "mOrderType";
	private static final String ACTION_SHOW_ACTIVATION_SUCCESS = "com.android.phone.ACTION_ACTIVATION_SUCCESS";

	public static final int UpgradeOrderNotFound = 1;
	public static final int UpgradeOrderPasswordAuthFailed = 2;
	public static final int UpgradeOrderSSNAuthFailed = 3;
	public static final int UpgradeReleaseOrderCorrelationIdIncorrect = 4;
	public static final int UpgradeReleaseOrderFailedOrderNotExist = 5;
	public static final int UpgradeReleaseOrderAuthFailed = 6;
	public static final int UpgradeReleaseOrderMutipleReq = 7;
	public static final int UpgradeReleaseOrderSuccess = 8;
	public static final int NewActOrderNotFound = 9;
	public static final int NewActOrderRestricted = 10;
	public static final int NewActCustValidatePasswdIncorrect = 11;
	public static final int NewActCustValidateSSNIncorrect = 12;
	public static final int NewActReleaseOrderCoorrelIDIncorrect = 13;
	public static final int NewActReleaseOrderPendingNotExist = 14;
	public static final int NewActReleaseOrderFailedAuthError = 15;
	public static final int NewActReleaseOrderMultipleReq = 16;
	public static final int NewActReleaseOrderSuccess = 17;
	public static final int LookupOrderTimeout = 18;
	public static final int ValidateCustomerTimeout = 19;
	public static final int ReleaseOrderTimeout = 20;
	public static final int Lost_and_Stolen_Device_or_SIM = 21;
	public static final int PendingProvisionErrorCode00013 = 22;
	public static final int UpgradeReleaseOrderSuccessThruNotification = 23;
	public static final int UpgradeReleaseOrderSuccessThruWebPortal = 24;
	public static final int UpgradeReleaseSuccess5CharAccountPIN = 25;
	private TextView mTvNotice;
	private int mPoaStatus;
	private boolean isActivated = false;

	@Override
	public String getTitleString() {
		return getString(R.string.phone_activation);
	}

	@Override
	protected int getContentLayoutResId() {
		return R.layout.fragment_poa_tatus;
	}

	@Override
	protected void initContent(ViewGroup container) {
		mTvNotice = (TextView) container.findViewById(R.id.tv_notice);
		mTvNotice.setMovementMethod(ScrollingMovementMethod.getInstance());
		setEmergencyBtnVisibility(View.VISIBLE);
		//getEmergencyBtn().requestFocus();
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		Bundle args = getArguments();
		if (args != null) {
			mPoaStatus = args.getInt(POA_STATUS_KEY, -1);
		}

		initView();
	}

	@Override
	public void onResume() {
		super.onResume();
		setLabelText();
	}

	private void setLabelText() {
		setLeftLabel(getString(R.string.label_back));
		if (isActivated) {
			setRightLabel(getString(R.string.label_next));
		}
	}


	private void initView() {

		switch (mPoaStatus) {
			case UpgradeOrderNotFound:
			case NewActOrderNotFound:
				showWifiButton();
				mTvNotice.setText(R.string.poa_upgrade_order_not_found);
				break;
			case UpgradeOrderPasswordAuthFailed:
			case UpgradeOrderSSNAuthFailed:
			case NewActCustValidatePasswdIncorrect:
			case NewActCustValidateSSNIncorrect:
				setEmergencyBtnVisibility(View.VISIBLE);
				mTvNotice.setText(R.string.poa_entry_not_match_records);
				break;
			case UpgradeReleaseOrderAuthFailed:
			case UpgradeReleaseOrderFailedOrderNotExist:
			case NewActReleaseOrderFailedAuthError:
			case NewActReleaseOrderPendingNotExist:
				showWifiButton();
				mTvNotice.setText(R.string.sorry_not_activate);
				break;
			case UpgradeReleaseOrderCorrelationIdIncorrect:
			case NewActReleaseOrderCoorrelIDIncorrect:
				setEmergencyBtnVisibility(View.VISIBLE);
				showWifiButton();
				mTvNotice.setText(R.string.sorry_not_activate);
				break;
			case NewActOrderRestricted:
				// get Device ID and SIM ID
				String imsi = Utils.getImsi(getContext());
				String imei = Utils.getImei(getContext());

				String format = getString(R.string.account_restricted);
				String restrictedInfo = String.format(format, imei, imsi);
				if (DEBUG) {
					Log.d(TAG, "restricted info=" + restrictedInfo);
				}
				mTvNotice.setText(Html.fromHtml(restrictedInfo));
				break;
			case LookupOrderTimeout:
				mTvNotice.setText(R.string.lookup_order_timeout);
				break;
			case ValidateCustomerTimeout:
				mTvNotice.setText(R.string.validate_customer_timeout);
				break;
			case ReleaseOrderTimeout:
				showWifiButton();
				mTvNotice.setText(R.string.release_order_timeout);
				break;
			case Lost_and_Stolen_Device_or_SIM:
				mTvNotice.setText(R.string.lost_and_stolen_device_or_sim);
				break;
			case PendingProvisionErrorCode00013:
				mTvNotice.setText(R.string.pending_provision_error_code_00013);
				break;
			case NewActReleaseOrderSuccess:
			case UpgradeReleaseSuccess5CharAccountPIN:
				isActivated = true;
				setRightLabel(getString(R.string.label_next));

				Bundle args = getArguments();
				if (args != null) {
					String mdn = args.getString("mdn", null);
					int type = args.getInt(POA_ORDER_TYPE_KEY);
					boolean showEm = (type == LookUpOrderRequest.MSG_PO_NEW_ORDER) || mPoaStatus == UpgradeReleaseSuccess5CharAccountPIN;
					setEmergencyBtnVisibility(showEm ? View.VISIBLE : View.GONE);
					String phoneNumber = getString(R.string.phone_number_unknown);
					if (mdn != null) {
						phoneNumber = PhoneNumberUtils.formatNumber(mdn, Locale.getDefault().getCountry());
					}

					String info = String.format(getString(R.string.phone_is_now_active), phoneNumber);
					mTvNotice.setText(info);

					sendActivationSuccessBroadcast(mdn);
				}
				break;
		}
	}

	private void sendActivationSuccessBroadcast(String mdn) {
		Intent intent = new Intent(ACTION_SHOW_ACTIVATION_SUCCESS);
		intent.setPackage("com.android.phone");
		getContext().sendBroadcast(intent);
	}

	private void showWifiButton() {
		//ccg later modified use googlr waird
		/*getCenterFuncBtn().setText(R.string.use_wifi);
		setCenterFuncBtnVisibility(View.VISIBLE);
		setCenterFuncBtnOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				if (DEBUG) {
					Log.e(TAG, "WifiButton click");
				}
				Bundle args = new Bundle();
				args.putBoolean(WifiSettingsFragment.KEY_USE_WIFI, true);
				startFragmentPanel(WifiSettingsFragment.class.getName(), args);
			}
		});*/
	}


	@Override
	protected boolean onBackKeyPressd(KeyEvent event) {
		getActivity().onBackPressed();
		return true;
	}

	@Override
	protected boolean onNextKeyPressed(KeyEvent event) {
		if (isActivated) {
			startFragmentPanel(VerizonCloudFragment.class.getName(), null);
		}
		return true;
	}
}
