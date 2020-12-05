package com.qualcomm.qti.setuptemp.poa;

import android.content.Context;
import android.util.Log;

import com.qualcomm.qti.setuptemp.utils.Utils;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;

import javax.net.ssl.HttpsURLConnection;

import static com.qualcomm.qti.setuptemp.poa.PoaConfig.RELEASE_ORDER_TIMEOUT;

public class ReleaseOrderRequest
		extends VzwPoaRequest {
	public static final String TAG = ReleaseOrderRequest.class.getSimpleName();

	private static final String[] validate_order_tags = {"service", "serviceHeader", "serviceBody", "clientName", "serviceName", "subserviceName", "requestID", "correlationID", "statusCode", "errorCode", "errorMessage", "imei", "orderReleased"};
	private String mErrorCode = null;
	private String mErrorMessage = null;
	private String mOrderReleased = null;
	private String mStatusCode;

	public static final int MSG_RELEASEORDER_REQUEST_SUCCESS = 1;
	public static final int MSG_RELEASEORDER_REQUEST_FAILURE = 0;
	public static final int MSG_RELEASEORDER_REQUEST_TIMEOUT = -1;
	public static final int MSG_RELEASEORDER_REQUEST_FAILURE_UNKNOWN_HOST = -2;
	public static final int MSG_RELEASEORDER_REQUEST_FAILURE_ERR_00013 = 13;

	public ReleaseOrderRequest() {
		setValidTags(validate_order_tags);
		mResponseStatus = MSG_RELEASEORDER_REQUEST_FAILURE;
	}

	public int releaseOrderReq(Context context, String imei, String requestID, String correlationID) {
		mResponseStatus = MSG_RELEASEORDER_REQUEST_FAILURE;
		String xml = Utils.loadDataFromAsset(context, "releaseorder.xml");
		if (xml == null) {
			Log.e(TAG, "xml is null ");
			return mResponseStatus;
		}
		Log.d(TAG, "imei=" + imei + " ,requestID=" + requestID + " ,correlationID=" + correlationID);
		String clientName = "EAS_OEM";
		String serviceName = "EASSelfActivation";
		String subserviceName = "ReleaseOrder";
		//String requestID = "abcd1234";
		String param = String.format(xml, new Object[]{clientName, serviceName, subserviceName, requestID, correlationID, imei});
		String url = PoaConfig.getPoaConfig().getReleaseOrderUrl();

		HttpsURLConnection conn = null;
		try {
			conn = doHttpPost(context, param, url, RELEASE_ORDER_TIMEOUT);
			if (conn == null) {
				Log.e(TAG, "connection is null ");
				return mResponseStatus;
			}

			String response = Utils.getInputStreamStringResponse(conn.getInputStream());
			parseTagVals(parseEasXml(response));
			if (DEBUG) {
				Log.e(TAG, "validateOrderReq response=" + response);
			}

			mResponseStatus = dealWithResponseError();
		} catch (SocketTimeoutException e) {
			mResponseStatus = MSG_RELEASEORDER_REQUEST_TIMEOUT;
			Log.d(TAG, "releaseOrderReq : mResponseStatus = " + mResponseStatus + " ,e=" + e);
		} catch (UnknownHostException e) {
			mResponseStatus = MSG_RELEASEORDER_REQUEST_FAILURE_UNKNOWN_HOST;
			Log.d(TAG, "releaseOrderReq : mResponseStatus = " + mResponseStatus + " ,e=" + e);
		} catch (IOException e) {
			Log.e(TAG, "Error creating connection in readLine", e);
			mResponseStatus = MSG_RELEASEORDER_REQUEST_FAILURE;
		} catch (Exception e) {
			Log.e(TAG, "Error creating connection", e);
			mResponseStatus = MSG_RELEASEORDER_REQUEST_FAILURE;
		} finally {
			if (conn != null) {
				conn.disconnect();
			}

			if (DEBUG) {
				Log.d(TAG, "finally disconnect");
			}
		}

		return mResponseStatus;
	}

	private int dealWithResponseError() {
		if (mErrorCode != null) {
			if (mErrorCode.equals("00000") && mOrderReleased != null) {
				if (mOrderReleased.equals("Y")) {
					if (DEBUG) {
						Log.d(TAG, "order release success " + mOrderReleased);
					}

					mResponseStatus = MSG_RELEASEORDER_REQUEST_SUCCESS;
				} else {
					if (DEBUG) {
						Log.d(TAG, "order release failed " + mOrderReleased);
					}

					mResponseStatus = MSG_RELEASEORDER_REQUEST_FAILURE;
				}
			} else {
				if (DEBUG) {
					Log.d(TAG, "order release failed mErrorCode=" + mErrorCode + " ,mOrderReleased=" + mOrderReleased);
				}

				mResponseStatus = MSG_RELEASEORDER_REQUEST_FAILURE;
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

			switch (tag) {
				case "errorCode":
					mErrorCode = val;
					break;
				case "errorMessage":
					mErrorMessage = val;
					break;
				case "orderReleased":
					mOrderReleased = val;
					break;
				case "statusCode":
					mStatusCode = val;
					break;
			}
		}
	}

	public String getErrorCode() {
		return mErrorCode;
	}

	public String getStatusCode() {
		return mStatusCode;
	}

	public String getErrorMessage() {
		return mErrorMessage;
	}
}