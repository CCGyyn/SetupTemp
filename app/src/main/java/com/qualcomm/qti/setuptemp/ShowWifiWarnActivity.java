package com.qualcomm.qti.setuptemp;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import com.android.setupwizardlib.util.WizardManagerHelper;
import android.provider.Settings;

public class ShowWifiWarnActivity extends Activity {

    private static final String TAG = ShowWifiWarnActivity.class.getSimpleName();

    private Button useMobileBtn;
    private Button backToWifiBtn;
    private Button skipBtn;
    private Intent mIntent;
    private View continueView;
    private View skipView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mIntent = getIntent();
        setContentView(R.layout.wifi_setup_warn);
        initView();
        initAction();
    }

    private void initView() {
        useMobileBtn = (Button) findViewById(R.id.use_mobile);
        skipBtn = (Button) findViewById(R.id.skip_btn);
        backToWifiBtn = (Button) findViewById(R.id.back_to_wifi);
        continueView = findViewById(R.id.wifi_setup_continue);
        skipView = findViewById(R.id.wifi_setup_skip_net);
        boolean activated = isPhoneActivatedSuccess(this);
        if (hasSim(this) && activated) {
            continueView.setVisibility(View.VISIBLE);
            skipView.setVisibility(View.GONE);
            useMobileBtn.setVisibility(View.VISIBLE);
            skipBtn.setVisibility(View.GONE);
        } else {
            continueView.setVisibility(View.GONE);
            skipView.setVisibility(View.VISIBLE);
            useMobileBtn.setVisibility(View.GONE);
            skipBtn.setVisibility(View.VISIBLE);
        }
    }

    private void initAction() {
        useMobileBtn.setOnClickListener(v -> launchNextPage(101));
        skipBtn.setOnClickListener(v -> launchNextPage(1));
        backToWifiBtn.setOnClickListener(v -> onBackPressed());
    }

    private void launchNextPage(int resultCode) {
        if (mIntent == null) mIntent = getIntent();
        Intent intent = WizardManagerHelper.getNextIntent(mIntent, resultCode);
        startActivityForResult(intent,1);
        Log.d(TAG, "launchNextPage startActivityForResult  resultCode=" + resultCode + " ,intent=" + intent);
    }

    // get phone activation status
    private boolean isPhoneActivatedSuccess(Context context) {
        // get phone activation status
        String activationStatus = Settings.Secure.getString(context.getContentResolver(), "vzw_activation_status");
        Log.d(TAG, "activationStatus=" + activationStatus);
        if (!TextUtils.isEmpty(activationStatus)) {
            String[] datas = activationStatus.split(":");
            if (datas != null && datas.length == 2) { // two parts, [mdn,pco]
                String mdn = datas[0];
                int pco = Integer.parseInt(datas[1]);
                Log.d(TAG, "isPhoneActivatedSuccess mdn=" + mdn + " ,pco=" + pco);
                boolean invalidMdn = TextUtils.isEmpty(mdn) || mdn.startsWith("00000");
                return !invalidMdn && pco == 0;
            }
        }
        return false;
    }

    private boolean hasSim(Context context) {
        TelephonyManager telephonyManager = context.getSystemService(TelephonyManager.class);
        final int simState = telephonyManager.getSimState();
        // Note that pulling the SIM card returns UNKNOWN, not ABSENT.
        return simState != TelephonyManager.SIM_STATE_ABSENT
                && simState != TelephonyManager.SIM_STATE_UNKNOWN;
    }


}
