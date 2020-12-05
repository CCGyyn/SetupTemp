package com.qualcomm.qti.setuptemp.poa;

import com.qualcomm.qti.setuptemp.R;
import com.qualcomm.qti.setuptemp.utils.Utils;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.ref.SoftReference;

public abstract class PoaConfig {
	private static final String TAG = PoaConfig.class.getSimpleName();
	public static final String KEY_IOT_EAS_SERVER_URL = "IOT_EAS_SERVER_URL";
	private static boolean DEBUG_MODE = false;  /// debug mode
	private static boolean DEBUGGABLE = DEBUG_MODE;
	public static final String POA_SECURE_DEBUG_MODE = "poa_secure_debug_mode";  /// for secure settings
	private static final String PERSIST_FILE_PATH = "/persist/provision/poa_test_url";

	private static PreTestPoaConfig sPreTestPoaConfig;
	private static ProductionPoaConfig sProductionPoaConfig;

	public static final int LOOKUP_ORDER_TIMEOUT = 30000; // 30s
	public static final int VALIDATE_ORDER_TIMEOUT = 20000;  // 20s
	public static final int RELEASE_ORDER_TIMEOUT = 60000; // 60s

	protected String username;
	protected String passwd;
	protected String lookupOrderUrl;
	protected String validateCustomerUrl;
	protected String releaseOrderUrl;
	private static SoftReference<Context> mContextRef;

	public static final String DEBUG_MODE_PSW = "vzpoatest";
	public static final String IOT_EAS_SERVER_URL = "https://poa-iot.vzw.com/vzw-eas-ws/EASSelfActivation";

	private PoaConfig() {

	}

	public abstract String getUsername();

	public abstract String getPasswd();

	public abstract String getLookupOrderUrl();

	public abstract String getValidateCustomerUrl();

	public abstract String getReleaseOrderUrl();

	public static boolean isDebuggable() {
		return DEBUGGABLE;
	}

	public static void setDebugMode(boolean enable) {
		setDebugMode(enable, false);
	}

	public static void init(Context context) {
		mContextRef = new SoftReference<>(context.getApplicationContext());
		setDebugMode(false, true);
		if (Utils.DEBUG) {
			String testUrl = readPersistUrl();
			Log.e(TAG, "testUrl=" + testUrl);
			if (!TextUtils.isEmpty(testUrl)) {
				Settings.Secure.putString(mContextRef.get().getContentResolver(), KEY_IOT_EAS_SERVER_URL, testUrl);
			}
		}
	}

	public static void setDebugMode(boolean enable, boolean force) {
		if (!force && DEBUG_MODE == enable) {  // no change
			return;
		}

		DEBUG_MODE = enable;
		DEBUGGABLE = DEBUG_MODE;
		Settings.Secure.putInt(mContextRef.get().getContentResolver(), PoaConfig.POA_SECURE_DEBUG_MODE, enable ? 1 : 0);

		// broadcast debug mode change
		Intent intent = new Intent("com.qualcomm.qti.setuptemp.ACTION_DEBUG_MODE_CHANGE");
		intent.setFlags(Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND); // add for background app
		mContextRef.get().sendBroadcast(intent);
	}

	public static void setTestUrl(String url) {
		if (TextUtils.isEmpty(url)) {
			return;
		}

		url = url.trim();
		String testUrl = getTestUrl();
		if (Utils.DEBUG){
			Log.e(TAG, "setTestUrl url=" + url + " ,curUrl=" + testUrl);
		}

		// has change or reset
		if (!IOT_EAS_SERVER_URL.equals(url) || !IOT_EAS_SERVER_URL.equals(testUrl)) {
			Settings.Secure.putString(mContextRef.get().getContentResolver(), KEY_IOT_EAS_SERVER_URL, url);
			writePersistUrl(url);
			if (Utils.DEBUG) Log.e(TAG, "changed url=" + url);

			// notify config change
			if (sPreTestPoaConfig != null) sPreTestPoaConfig.onConfigChanged();
		} else {
			if (Utils.DEBUG) Log.e(TAG, "match default url=" + url);
		}
	}

	public static String getTestUrl() {
		String modifiedUrl = Settings.Secure.getString(mContextRef.get().getContentResolver(), KEY_IOT_EAS_SERVER_URL);
		if (modifiedUrl != null) {
			return modifiedUrl;
		}
		return IOT_EAS_SERVER_URL;
	}

	private static void writePersistUrl(String url) {
		File file = new File(PERSIST_FILE_PATH);
		OutputStream out = null;
		try {
			if (!file.exists()) {
				if (!file.createNewFile()) {
					Log.e(TAG, "create new file failed, path=" + PERSIST_FILE_PATH);
					return;
				}
			}
			out = new FileOutputStream(file);
			out.write(url.getBytes());
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			Log.e(TAG, "no file found, path=" + PERSIST_FILE_PATH);
		} catch (IOException e) {
			e.printStackTrace();
			Log.e(TAG, "ex occurred when writing file, path=" + PERSIST_FILE_PATH);
		}finally {
			Utils.closeQuietly(out);
		}
	}

	public static String readPersistUrl() {
		File file = new File(PERSIST_FILE_PATH);
		BufferedReader reader = null;
		String line = null;
		StringBuilder sb = new StringBuilder();
		try {
			reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
			if ((line = reader.readLine()) != null) {
				sb.append(line);
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			sb.delete(0, sb.length());
		} catch (IOException e) {
			e.printStackTrace();
			sb.delete(0, sb.length());
		} finally {
			Utils.closeQuietly(reader);
		}

		return sb.toString();
	}

	public static PoaConfig getPoaConfig() {
		if (DEBUGGABLE) {
			if (sPreTestPoaConfig == null) {
				sPreTestPoaConfig = new PreTestPoaConfig();
			}
			return sPreTestPoaConfig;
		} else {
			if (sProductionPoaConfig == null) {
				sProductionPoaConfig = new ProductionPoaConfig();
			}
			return sProductionPoaConfig;
		}
	}

	public void onConfigChanged() {

	}


	static class ProductionPoaConfig extends PoaConfig {

		@Override
		public String getUsername() {
			return PoaConfig.getStringFromResId(R.string.poa_username);
		}

		@Override
		public String getPasswd() {
			return PoaConfig.getStringFromResId(R.string.poa_passwd);
		}

		@Override
		public String getLookupOrderUrl() {
			return PoaConfig.getStringFromResId(R.string.po_lookup_url);
		}

		@Override
		public String getValidateCustomerUrl() {
			return PoaConfig.getStringFromResId(R.string.po_validate_url);
		}

		@Override
		public String getReleaseOrderUrl() {
			return PoaConfig.getStringFromResId(R.string.po_release_url);
		}
	}

	static class PreTestPoaConfig extends PoaConfig {

		public static final String LOOKUP_ORDER = "LookupOrder";
		public static final String VALIDATE_CUSTOMER = "ValidateCustomer";
		public static final String RELEASE_ORDER = "ReleaseOrder";
		String modifiedUrl = null;

		PreTestPoaConfig() {
			tryGetModifiedUrl();
		}

		private void tryGetModifiedUrl() {
			modifiedUrl = Settings.Secure.getString(mContextRef.get().getContentResolver(), KEY_IOT_EAS_SERVER_URL);
			if (Utils.DEBUG) {
				Log.d(TAG, "modifiedUrl=" + modifiedUrl);
			}

			if (!TextUtils.isEmpty(modifiedUrl)) {
				int index = modifiedUrl.lastIndexOf("/");
				int length = modifiedUrl.length();
				if (index == length - 1) {  // last char is / , remove it
					Log.d(TAG, "last char is / , remove it");
					modifiedUrl = modifiedUrl.substring(0, length - 2);
				}
			}
		}

		@Override
		public void onConfigChanged() {
			super.onConfigChanged();
			if (Utils.DEBUG) {
				Log.d(TAG, "onConfigChanged");
			}
			tryGetModifiedUrl();
		}

		@Override
		public String getUsername() {
			return PoaConfig.getStringFromResId(R.string.poa_username_for_test);
		}

		@Override
		public String getPasswd() {
			return PoaConfig.getStringFromResId(R.string.poa_passwd_for_test);
		}

		@Override
		public String getLookupOrderUrl() {
			if (modifiedUrl != null) {
				return modifiedUrl + "/" + LOOKUP_ORDER;
			}

			return PoaConfig.getStringFromResId(R.string.po_lookup_url_for_test);
		}

		@Override
		public String getValidateCustomerUrl() {
			if (modifiedUrl != null) {
				return modifiedUrl + "/" + VALIDATE_CUSTOMER;
			}

			return PoaConfig.getStringFromResId(R.string.po_validate_url_for_test);
		}

		@Override
		public String getReleaseOrderUrl() {
			if (modifiedUrl != null) {
				return modifiedUrl + "/" + RELEASE_ORDER;
			}

			return PoaConfig.getStringFromResId(R.string.po_release_url_for_test);
		}
	}

	private static String getStringFromResId(int resId) {
		if (mContextRef.get() != null) {
			return mContextRef.get().getString(resId);
		}
		return null;
	}
}
