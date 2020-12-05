package com.qualcomm.qti.setuptemp.poa;

import android.content.Context;
import android.util.Log;

import com.qualcomm.qti.setuptemp.utils.Utils;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;

import javax.net.ssl.HttpsURLConnection;

import static com.qualcomm.qti.setuptemp.poa.PoaConfig.VALIDATE_ORDER_TIMEOUT;

public class ValidateCustomerRequest
		extends VzwPoaRequest {
	public static final String TAG = ValidateCustomerRequest.class.getSimpleName();

	private static final String[] validate_order_tags = {"service", "serviceHeader", "serviceBody", "clientName", "serviceName", "subserviceName", "requestID", "correlationID", "statusCode", "securityResponse", "securityQuestionID", "errorCode", "errorMessage", "customerValidated", "retryAllowed"};
	private String mCustomerValidated = null;
	private String mErrorCode = null;
	private String mErrorMessage = null;
	private String mRetryAllowed = null;
	private String mStatusCode = null;
	private String mRequestID = null;
	private String mCorrelationID = null;

	public static final int MSG_VALIDATE_CUSTOMER_SUCCESS = 1;
	public static final int MSG_VALIDATE_CUSTOMER_FAILURE = 0;
	public static final int MSG_VALIDATE_CUSTOMER_TIMEOUT = -1;

	public ValidateCustomerRequest() {
		setValidTags(validate_order_tags);
		mResponseStatus = MSG_VALIDATE_CUSTOMER_FAILURE;
	}

	public int validateOrderReq(Context context, String passcode, String correlationID, String requestID, String securityQuestionID) {
		mResponseStatus = MSG_VALIDATE_CUSTOMER_FAILURE;
		if (DEBUG) {
			Log.d(TAG, "validateOrderReq ENTER : passcode=" + passcode + " ,securityQuestionID=" + securityQuestionID);
		}
		String format = Utils.loadDataFromAsset(context, "validatecustomer.xml");
		if (format == null) {
			Log.e(TAG, "xml is null ");
			return mResponseStatus;
		}
		String clientName = "EAS_OEM";
		String serviceName = "EASSelfActivation";
		String subserviceName = "ValidateCustomer";
		String param = String.format(format, new Object[]{clientName, serviceName, subserviceName, requestID, correlationID, passcode, securityQuestionID});
		String url = PoaConfig.getPoaConfig().getValidateCustomerUrl();

		HttpsURLConnection conn = null;
		try {
			conn = doHttpPost(context, param, url, VALIDATE_ORDER_TIMEOUT);
			if (conn == null) {
				Log.e(TAG, "conn is null ");
				return mResponseStatus;
			}

			String response = Utils.getInputStreamStringResponse(conn.getInputStream());
			parseTagVals(parseEasXml(response));
			if (DEBUG) {
				Log.e(TAG, "validateOrderReq response=" + response);
			}

			mResponseStatus = dealWithResponseError();
		} catch (SocketTimeoutException e) {
			mResponseStatus = MSG_VALIDATE_CUSTOMER_TIMEOUT;
			Log.e(TAG, "validateOrderReq timeout: mResponseStatus = " + mResponseStatus + " ,e=" + e);
		}catch (IOException e) {
			mResponseStatus = MSG_VALIDATE_CUSTOMER_FAILURE;
			Log.e(TAG, "Error creating connection in readLine", e);
		} finally {
			if (DEBUG) {
				Log.d(TAG, "finally disconnect");
			}

			if (conn != null) {
				conn.disconnect();
			}
		}

		return mResponseStatus;
	}

	private int dealWithResponseError() {
		if (mErrorCode != null && mErrorCode.equals("00000")) {
			if (mCustomerValidated != null && mCustomerValidated.equals("Y")) {
				mResponseStatus = MSG_VALIDATE_CUSTOMER_SUCCESS;
				if (DEBUG) {
					Log.d(TAG, "customer validated");
				}
			} else{
				mResponseStatus = MSG_VALIDATE_CUSTOMER_FAILURE;
				if (DEBUG) {
					Log.d(TAG, "customer validation failed" + mCustomerValidated);
				}
			}
		} else {
			mResponseStatus = MSG_VALIDATE_CUSTOMER_FAILURE;
			if (DEBUG) {
				Log.e(TAG, "validation failed  mErrorCode=" + mErrorCode + ",mErrorMessage=" + mErrorMessage +
						" mRetryAllowed=" + mRetryAllowed + " ,mStatusCode=" + mStatusCode);
			}
		}
		return mResponseStatus;
	}

	private void parseTagVals(ArrayList<TagVal> tagVals) {
		if (tagVals == null) {
			return;
		}

		for (int i = 0; i < tagVals.size(); i++) {
			TagVal tagVal = tagVals.get(i);

			String tag = tagVal.tag;
			String val = tagVal.val;
			Log.d(TAG, "INDEX[" + i + "]: TAG=" + tag + " VAL=" + val);
			switch (tag) {
				case "statusCode":
					mStatusCode = val;
					break;
				case "errorCode":
					mErrorCode = val;
					break;
				case "errorMessage":
					mErrorMessage = val;
					break;
				case "customerValidated":
					mCustomerValidated = val;
					break;
				case "retryAllowed":
					mRetryAllowed = val;
					break;
				case "requestID":
					mRequestID = val;
					break;
				case "correlationID":
					mCorrelationID = val;
					break;
			}
		}
	}

	public String getErrorCode() {
		return mErrorCode;
	}

	public String getErrorMessage() {
		return mErrorMessage;
	}

	public String getRetryAllowed() {
		return mRetryAllowed;
	}

	public String getStatusCode() {
		return mStatusCode;
	}

	public String getRequestID() {
		return mRequestID;
	}

	public String getCorrelationID() {
		return mCorrelationID;
	}
}