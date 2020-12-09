package com.qualcomm.qti.setuptemp.receiver;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.util.Log;

import com.qualcomm.qti.setuptemp.DefaultActivity;
import com.qualcomm.qti.setuptemp.event.PcoDataObserver;
import com.qualcomm.qti.setuptemp.poa.PoaConfig;
import com.qualcomm.qti.setuptemp.utils.Utils;
import com.qualcomm.qti.setuptemp.poa.VzwActivationService;

/**
 * receive actions about provision
 *
 * @since 20181206
 */
public class ProvisionReceiver extends BroadcastReceiver {
    private static final boolean DEBUG = true;
    private static final String TAG = ProvisionReceiver.class.getSimpleName();
    public static final String ACTION_POA_DEBUG_MODE_CHANGE = "com.android.provision.ACTION_DEBUG_MODE_CHANGE";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (DEBUG) {
            Log.e(TAG, TAG + " -> " + action);
        }

        switch (action) {
            case "com.android.provision.ACTION_ACTIVATION_UI":
                boolean isCallerDialer = intent.getBooleanExtra(DefaultActivity.START_FROM_DIALER, false);
                boolean isCallerNotification = intent.getBooleanExtra(DefaultActivity.START_FROM_NOTIFICATION, false);
                boolean isVerifiedCaller = isCallerDialer || isCallerNotification;
                if (DEBUG) Log.e(TAG, "isVerifiedCaller = " + isVerifiedCaller);
                if (!isVerifiedCaller) {
                    return; // just return when lunch flag not set
                }

                PackageManager packageManager = context.getPackageManager();
                // enable component
                packageManager.setComponentEnabledSetting(new ComponentName(context, DefaultActivity.class),
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);

                intent = new Intent(context, DefaultActivity.class);
                intent.putExtra(DefaultActivity.START_FROM_DIALER, isCallerDialer);
                intent.putExtra(DefaultActivity.START_FROM_NOTIFICATION, isCallerNotification);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // new task needed
                // start activity
                context.startActivity(intent);
                break;
            case Intent.ACTION_BOOT_COMPLETED:
                // start observing..
                PcoDataObserver.startListen(context.getApplicationContext());
                if (DEBUG) Log.e(TAG, "start observing.. ");

                handleActivationCheck(context.getApplicationContext());
                break;
        }
    }

    private void handleActivationCheck(Context context) {
        if (!Utils.isSetupComplete(context)) {
            return;
        }

        String mdn = Utils.getMDN(context);
        if (Utils.isValidMbn(mdn)) {
            if (DEBUG) {
                Log.d(TAG, "mdn=" + mdn);
            }

            if (Utils.DEBUG) {
                startVzwActivationService(context);
            }
        } else {
            startVzwActivationService(context);
        }
    }

    private void startVzwActivationService(Context context) {
        Intent intent = new Intent(context, VzwActivationService.class);
        intent.setPackage(context.getPackageName());
        context.startService(intent);
    }
}
