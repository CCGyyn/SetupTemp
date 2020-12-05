package com.qualcomm.qti.setuptemp.utils;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.UserHandle;
import android.provider.Settings;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

public class Utils {
	public static final String ACTION_PCO_CHANGE = "com.odm.setupwizardoverlay.PCO_CHANGE";

	private static final String TAG = Utils.class.getSimpleName();
	public static final boolean DEBUG = true;


	public static String loadDataFromAsset(Context context, String fileName) {
		InputStream is = null;
		try {
			is = context.getAssets().open(fileName);
			byte[] bytes = new byte[is.available()];
			is.read(bytes);
			String result = new String(bytes);
			Log.d(TAG, "loadDataFromAsset result=" + result);
			return result;
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			closeQuietly(is);
		}
		return null;
	}

	public static String getInputStreamStringResponse(InputStream is, String charsetName) throws IOException {
		BufferedReader br = new BufferedReader(new InputStreamReader(is, charsetName));
		String line = null;
		StringBuilder sb = new StringBuilder();
		try {
			while ((line = br.readLine()) != null) {
				if (DEBUG) {
					Log.d(TAG, "Line:" + line);
				}
				sb.append(line);
			}
		} finally {
			Utils.closeQuietly(br);
		}

		return sb.toString();

	}

	public static String getInputStreamStringResponse(InputStream is) throws IOException {
		return getInputStreamStringResponse(is, "UTF-8");
	}

	public static void closeQuietly(Closeable stream) {
		if (stream != null) {
			try {
				stream.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public static boolean isSetupComplete(Context paramContext) {
		if (Settings.Secure.getInt(paramContext.getContentResolver(), "user_setup_complete", 0) == 1) {
			Log.d(TAG, "post setup");
			return true;
		}
		Log.d(TAG, "during setup");
		return false;
	}

	public static String getMDN(Context context) {
		String mdn = null;
		SubscriptionManager subscriptionManager = SubscriptionManager.from(context);

		if (subscriptionManager != null) {
			List<SubscriptionInfo> subscriptionInfos = subscriptionManager.getActiveSubscriptionInfoList();

			if (subscriptionInfos != null && subscriptionInfos.size() > 0) {
				SubscriptionInfo selectableSubInfo = subscriptionInfos.get(0);
				if (selectableSubInfo != null) {
					mdn = selectableSubInfo.getNumber();
					Log.d(TAG, "mdn: " + mdn);
				}
			}
		}

		return mdn;
	}

	public static boolean isValidMbn(String mdn) {
		return !TextUtils.isEmpty(mdn) && !mdn.startsWith("00000");
	}

	/*set AirplaneMode & sendBroadcast*/
	public static void setAirplaneMode(Context context, boolean enabling) {
		Settings.Global.putInt(context.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, enabling ? 1 : 0);
		if (DEBUG) {
			int mode = Settings.Global.getInt(context.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0);
			Log.d(TAG, "mode=" + mode);
		}

		// Post the intent
		Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
		intent.putExtra("state", enabling);
		context.sendBroadcastAsUser(intent, UserHandle.ALL);
	}

	public static boolean getAirplaneMode(Context context) {
		return Settings.Global.getInt(context.getContentResolver(),
				Settings.Global.AIRPLANE_MODE_ON, 0) == 1;
	}

	public static void enableComponentSetting(Context context, Class<?> clazz, boolean enable) {
		// enable/disable this activity from the package manager.
		PackageManager pm = context.getPackageManager();
		ComponentName name = new ComponentName(context, clazz);
		pm.setComponentEnabledSetting(name, enable ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
				: PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
	}

	public static int getComponentEnabled(Context context, Class<?> clazz) {
		PackageManager pm = context.getPackageManager();
		return pm.getComponentEnabledSetting(new ComponentName(context, clazz));
	}

	public static String getImei(Context context) {
		TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
		if (tm == null) {
			return null;
		}

		return tm.getImei();
	}

	public static String getImsi(Context context) {
		TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
		if (tm == null) {
			return null;
		}

		return tm.getSubscriberId();
	}

	public static ActivityInfo getHomeActivityInfo(Context context) {
		Intent intent = getHomeIntent();
		// query available home activity info
		List<ResolveInfo> resolveInfos = context.getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
		String thisPackageName = context.getPackageName();

		for (ResolveInfo resolveInfo : resolveInfos) {
			ActivityInfo activityInfo = resolveInfo.activityInfo;
			if (DEBUG) Log.d(TAG, "activity = " + activityInfo.name + " , package = " + activityInfo.packageName);
			if (!thisPackageName.equals(activityInfo.packageName)) { // package is not this
				return activityInfo;
			}
		}

		return null;
	}

	public static Intent getHomeIntent() {
		Intent intent = new Intent();
		intent.setAction(Intent.ACTION_MAIN);
		intent.addCategory(Intent.CATEGORY_HOME);
		return intent;
	}
}