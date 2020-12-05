package com.qualcomm.qti.setuptemp.poa;

import android.content.Context;
import android.util.Log;

import com.qualcomm.qti.setuptemp.utils.Utils;

import java.net.SocketTimeoutException;
import java.util.ArrayList;

import javax.net.ssl.HttpsURLConnection;

public class LookUpOrderRequest
		extends VzwPoaRequest {
	private static final String TAG = LookUpOrderRequest.class.getSimpleName();

	public static final int MSG_PO_NEW_ORDER = 7;
	public static final int MSG_PO_UPGRADE_ORDER = 8;
	public static final int MSG_PO_NOT_FOUND = 9;
	public static final int MSG_PO_TIME_OUT = 10;
	private static final String[] lookup_order_tags = {"service", "serviceHeader", "serviceBody", "clientName", "serviceName", "subserviceName", "requestID", "imsi", "imei", "correlationID", "statusCode", "securityResponse", "securityQuestionID", "errorCode", "errorMessage", "orderFound", "validationReq", "accountRestricted", "orderType", "priority", "securityResponseFormat", "securityResponseLength", "orderingMtn", "alternateMtn"};
	public static final String SECURITY_QUESTION_ID_001 = "001";
	public static final String SECURITY_QUESTION_ID_002 = "002";

	private String mAccountRestricted = null;
	private String mAlternateMtn = null;
	private String mCorrelationID = null;
	private String mErrorCode = null;
	private String mErrorMessage = null;
	private String mOrderFound = null;
	private String mOrderType = null;
	private String mOrderingMtn = null;
	private String mPriority = null;
	private String mRequestID = null;
	private int mSecurityQuestionID_1 = -1;
	private int mSecurityQuestionID_2 = -1;
	private String mStatusCode = null;
	private String mValidationReq = null;

	public LookUpOrderRequest() {
		setValidTags(lookup_order_tags);
		mResponseStatus = MSG_PO_NOT_FOUND;
	}

	public int getAccountRestricted() {
		return mAccountRestricted == null ? -1 : (mAccountRestricted.equals("Y") ? 0 : 1);
	}

	public String getCorrelationID() {
		return mCorrelationID;
	}

	public String getErrorCode() {
		return mErrorCode;
	}

	public boolean getOrderFound() {
		return mOrderFound != null && mOrderFound.equals("Y");
	}

	public int getOrderType() {
		Log.d(TAG, "getOrderType=" + mOrderType);

		boolean orderFound = getOrderFound();
		if (orderFound) {
			if (mOrderType.equals("N")) {
				return MSG_PO_NEW_ORDER;
			} else if (mOrderType.equals("U")) {
				return MSG_PO_UPGRADE_ORDER;
			}

			Log.e(TAG, "error order type  : orderFound=" + orderFound + " ,mOrderType=" + mOrderType);
		}
		return MSG_PO_NOT_FOUND;
	}

	public String getRequestID() {
		return mRequestID;
	}

	public int getSecurityQuestionID() {
		return mSecurityQuestionID_1;
	}

	public String getErrorMessage() {
		return mErrorMessage;
	}

	public String getValidationReq() {
		return mValidationReq;
	}

	public String getStatusCode() {
		return mStatusCode;
	}

	public int getSecurityQuestionID_2() {
		return mSecurityQuestionID_2;
	}

	public int lookupOrderReq(Context context, String imsi, String imei) {
		mResponseStatus = MSG_PO_NOT_FOUND;
		String format = Utils.loadDataFromAsset(context, "lookuporder.xml");
		if (format == null) {
			Log.e(TAG, "xml is null ");
			return mResponseStatus;
		}

		if (DEBUG) {
			Log.d(TAG, "imsi = " + imsi + " imei = " + imei);
			Log.d(TAG, "xml for lookup order request is " + format);
		}

		String clientName = "EAS_OEM";
		String serviceName = "EASSelfActivation";
		String subserviceName = "LookupOrder";
		String requestID = "abcd1234";
		String param = String.format(format, new Object[]{clientName, serviceName, subserviceName, requestID, imsi, imei});
		String url = PoaConfig.getPoaConfig().getLookupOrderUrl();

		HttpsURLConnection conn = null;
		try {
			conn = doHttpPost(context, param, url, PoaConfig.LOOKUP_ORDER_TIMEOUT);
			if (conn == null) {
				Log.e(TAG, "connection is null");
				return mResponseStatus;
			}

			String response = Utils.getInputStreamStringResponse(conn.getInputStream());
			parseTagVals(parseEasXml(response));
			mResponseStatus = getOrderType();
			if (DEBUG) {
				Log.e(TAG, "lookupOrderReq mResponseStatus=" + mResponseStatus);
				Log.e(TAG, "lookupOrderReq response=" + response);
			}
		} catch (SocketTimeoutException e) {
			mResponseStatus = MSG_PO_TIME_OUT;
			Log.d(TAG, "lookupOrderReq timeout : mResponseStatus = " + mResponseStatus + " ,e="+ e);
		} catch (Exception e1) {
			mResponseStatus = MSG_PO_NOT_FOUND;
			Log.d(TAG, "lookupOrderReq : mResponseStatus = " + mResponseStatus + " ,e=" + e1);
		} finally {
			if (conn != null) {
				conn.disconnect();
			}
		}

		return mResponseStatus;
	}

	protected void parseTagVals(ArrayList<TagVal> tagVals) {
		if (tagVals == null) {
			return;
		}

		if (DEBUG) {
			Log.e(TAG, "parseTagVals size=" + tagVals.size());
		}

		for (int index = 0; index < tagVals.size(); index++) {
			TagVal tagVal = tagVals.get(index);
			String tag = tagVal.tag;
			String val = tagVal.val;

			if (DEBUG) {
				Log.d(TAG, "parseTagVals index=" + index + ", tag=" + tag + ", val=" + val);
			}

			switch (tag) {
				case "correlationID":
					mCorrelationID = val;
					break;
				case "requestID":
					mRequestID = val;
					break;
				case "errorCode":
					mErrorCode = val;
				case "errorMessage":
					mErrorMessage = val;
					break;
				case "orderFound":
					mOrderFound = val;
					break;
				case "accountRestricted":
					mAccountRestricted = val;
					break;
				case "validationReq":
					mValidationReq = val;
					break;
				case "orderType":
					mOrderType = val;
					break;
				case "securityQuestionID":
					if (SECURITY_QUESTION_ID_001.equals(val)) {
						mSecurityQuestionID_1 = 1;
					} else if (SECURITY_QUESTION_ID_002.equals(val)) {
						mSecurityQuestionID_2 = 2;
					}
					break;
				case "priority":
					mPriority = val;
					break;
				case "orderingMtn":
					mOrderingMtn = val;
					break;
				case "alternateMtn":
					mAlternateMtn = val;
					break;
				case "statusCode":
					mStatusCode = val;
					break;
			}
		}
	}

}