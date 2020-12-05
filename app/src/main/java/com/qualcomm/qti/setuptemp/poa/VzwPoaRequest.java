package com.qualcomm.qti.setuptemp.poa;

import android.content.Context;
import android.os.Build;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import com.qualcomm.qti.setuptemp.utils.Utils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class VzwPoaRequest {
	public static final String TAG = VzwPoaRequest.class.getSimpleName();
	protected static final boolean DEBUG = true;

	private static String CNAME;

	private static Set<String> validTags = null;
	// error code
	public static final String ERR_CODE_00000 = "00000";  //  Success
	public static final String ERR_CODE_00001 = "00001";  //  general.error
	public static final String ERR_CODE_00002 = "00002";  //  validation.error ,  Invalid input parameters
	public static final String ERR_CODE_00003 = "00003";  //  Internal Failure
	public static final String ERR_CODE_00004 = "00004";  //  Internal Failure
	public static final String ERR_CODE_00005 = "00005"; //  authentication.failure , Invalid user credentials
	public static final String ERR_CODE_00009 = "00009"; //  security.failure
	public static final String ERR_CODE_00013 = "00013"; // Pending Provision , Activations taking longer than	expected

	// Status code
	public static final String STATUS_CODE_FAILURE = "FAILURE";
	public static final String STATUS_CODE_SUCCESS = "SUCCESS";

	// error message
	public static final String ERROR_MESSAGE_GENERAL_ERROR = "general.error";
	/**
	 * error 00002 , Invalid input parameters.
	 */
	public static final String ERROR_MESSAGE_VALIDATION_ERROR = "validation.error";
	/**
	 * error 00005 , Invalid user credentials.
	 */
	public static final String ERROR_MESSAGE_AUTHENTICATION_FAILURE = "authentication.failure";
	/**
	 * error 00009 , security.failure
	 */
	public static final String ERROR_MESSAGE_SECURITY_FAILURE = "security.failure";

	private volatile HttpsURLConnection mConnection;

	protected int mResponseStatus;

	public int getResponseStatus() {
		return mResponseStatus;
	}

	/**
	 * if this is authentication.failure
	 *
	 * @param errorCode
	 * @param errorMessage
	 * @return
	 */
	public static boolean matchAuthenticationFailure(String errorCode, String errorMessage) {
		return ERROR_MESSAGE_AUTHENTICATION_FAILURE.equals(errorMessage) && ERR_CODE_00005.equals(errorCode);
	}

	/**
	 * if this is security.failure
	 *
	 * @param errorCode
	 * @param errorMessage
	 * @return
	 */
	public static boolean matchSecurityFailure(String errorCode, String errorMessage) {
		return ERROR_MESSAGE_SECURITY_FAILURE.equals(errorMessage) && ERR_CODE_00009.equals(errorCode);
	}


	/**
	 *  parse the xml response from server
	 * @param xmlString
	 * @return
	 */
	public static synchronized ArrayList<TagVal> parseEasXml(String xmlString) {
		Log.d(TAG, "xml: " + xmlString);
		ArrayList<TagVal> tagValList = new ArrayList<>();
		XmlPullParserFactory parserFactory;
		XmlPullParser xmlParser;
		try {
			parserFactory = XmlPullParserFactory.newInstance();
			parserFactory.setNamespaceAware(true);
			xmlParser = parserFactory.newPullParser();
			xmlParser.setInput(new StringReader(xmlString));
			int eventType = xmlParser.getEventType();
			TagVal tagVal = null;
			while (eventType != XmlPullParser.END_DOCUMENT) {
				switch (eventType) {
					case XmlPullParser.START_DOCUMENT:
						if (DEBUG) Log.d(TAG, "Start document");
						break;
					case XmlPullParser.START_TAG:
						if (tagVal == null) {
							tagVal = new TagVal();
						}
						String tag = xmlParser.getName();
						tagVal.setTag(tag);
						break;
					case XmlPullParser.TEXT:
						if (tagVal != null) {
							String text = xmlParser.getText();
							boolean valid = !TextUtils.isEmpty(text);
							if (valid) {
								tagVal.setVal(text);
								tagValList.add(tagVal);
							}
						}
						break;
					case XmlPullParser.END_TAG:
						if (tagVal != null) {
							if (xmlParser.getName().equals(tagVal.tag)) {  /// end tag name should match start tag's
								if (DEBUG)
									Log.d(TAG, "Name = " + tagVal.tag + " ,Text #" + tagVal.val + "#");
								boolean empty = TextUtils.isEmpty(tagVal.val);
								if (!empty) {
									tagVal = null;  // this tagVal is used. reset for create new one
								}
							}
						}

						break;
				}

				eventType = xmlParser.next();
			}
			if (DEBUG) Log.d(TAG, "End document");
		} catch (XmlPullParserException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return tagValList;
	}

	/**
	 * do http post request
	 * @param context
	 * @param param
	 * @param urlString
	 * @param timeout
	 * @return
	 */
	public HttpsURLConnection doHttpPost(Context context, String param, String urlString, int timeout) throws SocketTimeoutException, UnknownHostException {
		// config for current version
		PoaConfig config = PoaConfig.getPoaConfig();
		String user = config.getUsername();
		String passwd = config.getPasswd();
		if (DEBUG) {
			Log.d(TAG, "user=" + user + " ,passwd=" + passwd);
			Log.d(TAG, "xml request param=" + param + " \n url to connect " + urlString);
		}

		if (!PoaConfig.isDebuggable()) {
			CNAME = "eas.verizonwireless.com";
		}


		URL url;
		byte[] paramBytes;

		try {
			url = new URL(urlString);
			mConnection = getHttpsConnection(url, timeout);
			if (mConnection == null) {
				Log.d(TAG, "getHttpsConnection returned null");
				return null;
			}
			mConnection.setRequestProperty("Connection", "Keep-Alive");
			mConnection.setDoInput(true);
			mConnection.setDoOutput(true);
			mConnection.setRequestMethod("POST");
			String auth = "Basic " + encodeToB64(user + ":" + passwd);
			if (DEBUG) Log.d(TAG, "auth : " + auth);
			mConnection.setRequestProperty("Authorization", auth);
			mConnection.setRequestProperty("Content-Type", "application/xml");
			paramBytes = param.getBytes(Charset.forName("UTF-8"));
			mConnection.setFixedLengthStreamingMode(paramBytes.length);
			OutputStream out = null;
			try {
				out = mConnection.getOutputStream();
				out.write(paramBytes, 0, paramBytes.length);
				out.flush();
			} catch (IOException e) {
				e.printStackTrace();
				Log.d(TAG, "Exception write params : HTTP POST response code=" + mConnection.getResponseCode() + " ,error=" + e.getMessage());
			} finally {
				Utils.closeQuietly(out);
			}
			Log.d(TAG, "HTTP response code:" + mConnection.getResponseCode());
			if (mConnection.getResponseCode() == 200) {
				Log.d(TAG, "Response Code : " + mConnection.getResponseCode());
				Log.d(TAG, "Cipher Suite : " + mConnection.getCipherSuite());
				Certificate[] certificates = mConnection.getServerCertificates();
				for (Certificate certificate : certificates) {
					if (DEBUG) {
						Log.d(TAG, "start ---->");
						Log.d(TAG, "Cert Type : " + certificate.getType());
						Log.d(TAG, "Cert Hash Code : " + certificate.hashCode());
						Log.d(TAG, "Cert Public Key Algorithm : " + certificate.getPublicKey().getAlgorithm());
						Log.d(TAG, "Cert Public Key Format : " + certificate.getPublicKey().getFormat());
						Log.d(TAG, "end ---->");
					}
				}
			}
		} catch (SocketTimeoutException e) {
			throw new SocketTimeoutException("do http post timeout " + e.getMessage());
		} catch (UnknownHostException e) {
			throw new UnknownHostException("do http post unknown host "+e.getMessage());
		} catch (Exception e) {
			Log.d(TAG, "Exception: HTTP POST " + e.getMessage());
			e.printStackTrace();
			Log.d(TAG, "mConnection = " + mConnection);
			return mConnection;
		}
		String pName = null;
		try {
			pName = mConnection.getPeerPrincipal().getName();
			Log.d(TAG, "pName = " + pName + ", CNAME=" + CNAME);
			//if (pName.contains(CNAME)) {
			Log.d(TAG, "mConnection = " + mConnection);
			return mConnection;
			//}
		} catch (SSLPeerUnverifiedException e) {
			e.printStackTrace();
		}

		return null;
	}

	public void onCancelled() {
		if (mConnection != null) {
			mConnection.disconnect();
			mConnection = null;
			Log.e(TAG, "mConnection disconnect");
		}
	}

	public String encodeToB64(String encrypt) {
		return Base64.encodeToString(encrypt.getBytes(Charset.forName("UTF-8")), 2);
	}

	public HttpsURLConnection getHttpsConnection(URL url, int timeout) throws SocketTimeoutException {
		HttpsURLConnection conn;
		try {
			trustAllHosts();
			conn = (HttpsURLConnection) url.openConnection();
			conn.setConnectTimeout(timeout);
			conn.setReadTimeout(timeout);
			conn.setHostnameVerifier(GRANTED_HOSTNAME_VERIFIER);
			return conn;
		} catch (SocketTimeoutException e1) {
			Log.e(TAG, "Error SocketTimeoutException connection ", e1);
			throw new SocketTimeoutException("Error SocketTimeoutException connection : "+ e1.getMessage());
		} catch (IOException e) {
			e.printStackTrace();
			Log.e(TAG, "Error creating connection ", e);
		}
		return null;
	}

	public static final HostnameVerifier GRANTED_HOSTNAME_VERIFIER = new HostnameVerifier() {
		@Override
		public boolean verify(String s, SSLSession sslSession) {
			return true;
		}
	};

	private void trustAllHosts() {
		// create trust managers . X509TrustManager for android
		TrustManager[] trustManagers = new TrustManager[]{new X509TrustManager() {
			@Override
			public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
			}

			@Override
			public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
			}

			@Override
			public X509Certificate[] getAcceptedIssuers() {
				return new X509Certificate[0];
			}
		}};

		// install trust managers
		try {
			SSLContext sslContext = SSLContext.getInstance("TLS");
			sslContext.init(null, trustManagers, new SecureRandom());
			HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (KeyManagementException e) {
			e.printStackTrace();
		}
	}

	public void setValidTags(String[] strings) {
		validTags = new HashSet(Arrays.asList(strings));
	}

	public static class TagVal {
		String tag;
		String val;

		TagVal(String tag, String val) {
			this.tag = tag;
			this.val = val;
		}

		public TagVal() {
		}

		public void setTag(String tag) {
			this.tag = tag;
		}

		public void setVal(String val) {
			this.val = val;
		}
	}
}
