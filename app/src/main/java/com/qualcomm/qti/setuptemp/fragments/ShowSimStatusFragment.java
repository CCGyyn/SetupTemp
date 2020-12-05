package com.qualcomm.qti.setuptemp.fragments;

import android.content.Context;
import android.os.Bundle;
import android.provider.Settings;
import android.telephony.PhoneNumberUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.qualcomm.qti.setuptemp.R;
import com.qualcomm.qti.setuptemp.event.ActivationTracker;

import java.util.Locale;
import android.content.Intent;

public class ShowSimStatusFragment extends FragmentCommon {
    private static final String TAG = ShowSimStatusFragment.class.getSimpleName();

    private Context mContext;

    private TextView mWelcomeSubTitleText;
    private TextView mSimStatusText;
    private TextView mSimNoteText;

    private LinearLayout mSimStatusBodyLayout;

    private int mSimStatus = -1;

    private String mMdn = null;

    private boolean mIsFromNotification;

    @Override
    public String getTitleString() {
        return getString(R.string.phone_activation);
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.fragment_show_sim_status;
    }

    @Override
    public void onInitContent(View root) {
        super.onInitContent(root);
        mSimStatusBodyLayout = (LinearLayout) root.findViewById(R.id.sim_status_body);
        mSimStatusBodyLayout.setOnClickListener(onClickSimStatusBodyListener);

        mWelcomeSubTitleText = (TextView) root.findViewById(R.id.welcome_sub_title);
        mSimStatusText = (TextView) root.findViewById(R.id.sim_status_text);
        mSimNoteText = (TextView) root.findViewById(R.id.sim_note_text);
    }


    @Override
    public void onResume() {
        super.onResume();
        Bundle arguments = getArguments();
        mContext = getActivity();
        if (arguments != null) {
            if (arguments.containsKey(VzwSimCheckFragment.SIM_STATUS_KEY))
                mSimStatus = arguments.getInt(VzwSimCheckFragment.SIM_STATUS_KEY, VzwSimCheckFragment.ACTION_SKIP_DISPLAY);
            if (arguments.containsKey(VzwSimCheckFragment.SIM_MDN_KEY))
                mMdn = arguments.getString(VzwSimCheckFragment.SIM_MDN_KEY);
            if (arguments.containsKey(VzwSimCheckFragment.SIM_FROM_NOTIFICATION_KEY))
                mIsFromNotification = arguments.getBoolean(VzwSimCheckFragment.SIM_FROM_NOTIFICATION_KEY, false);
        }

        initUi();
    }


    private void initUi() {
        setLeftLabel(getString(R.string.label_back));
        setRightLabel(getString(R.string.label_next));

        if (DEBUG) Log.e(TAG, "SimStatus: " + mSimStatus);

        switch (mSimStatus) {
            case VzwSimCheckFragment.ACTION_SKIP_DISPLAY:
                mWelcomeSubTitleText.setVisibility(View.VISIBLE);
                mWelcomeSubTitleText.setText(R.string.skip_activation_ui_warn);
                break;
            case VzwSimCheckFragment.ACTION_SHOW_NO_SIM:
                mWelcomeSubTitleText.setVisibility(View.VISIBLE);
                mWelcomeSubTitleText.setText(mContext.getText(R.string.no_sim));
                break;
            case VzwSimCheckFragment.ACTION_NON_VZW_SIM:
                Log.d(TAG, "non vzw sim");
                mWelcomeSubTitleText.setVisibility(View.VISIBLE);
                mWelcomeSubTitleText.setText(R.string.wrong_operator_vzw);
                break;
            case VzwSimCheckFragment.ACTION_SIM_NOT_READY:
                mWelcomeSubTitleText.setVisibility(View.VISIBLE);
                mWelcomeSubTitleText.setText(R.string.sim_not_ready_warn);
                break;
            case VzwSimCheckFragment.ACTION_SHOW_SIM_ERROR:
                mSimStatusText.setText(R.string.corrupt_sim);
                break;
            case VzwSimCheckFragment.ACTION_SIM_READY:
                break;
            case VzwSimCheckFragment.ACTION_SHOW_NOT_ACTIVATED:
               // mWelcomeSubTitleText.setText(R.string.phone_not_activated_warn);
               // mWelcomeSubTitleText.setVisibility(View.VISIBLE);
                break;
            case VzwSimCheckFragment.ACTION_SHOW_PLAN_SELECTION:
                setRightLabel("");
                mWelcomeSubTitleText.setVisibility(View.VISIBLE);
                mWelcomeSubTitleText.setText(R.string.activate_via_call);
                break;
            case VzwSimCheckFragment.ACTION_SHOW_ACTIVATED:
                mWelcomeSubTitleText.setText(R.string.sim_card_already_activatd_txt);
                mWelcomeSubTitleText.setVisibility(View.VISIBLE);
                String phoneNumber = getString(R.string.phone_number_unknown);
                if (mMdn != null)
                    phoneNumber = PhoneNumberUtils.formatNumber(mMdn, Locale.getDefault().getCountry());
                mSimStatusText.setText(phoneNumber);
                mSimStatusText.setVisibility(View.VISIBLE);
                mSimNoteText.setText(R.string.sim_note);
                mSimNoteText.setVisibility(View.VISIBLE);
                // write valid phoneNumber
                if (!getString(R.string.phone_number_unknown).equals(phoneNumber)) {
                    Settings.Secure.putString(getActivity().getContentResolver(), ACTIVATED_PHONE_NUMBER, phoneNumber);
                }
                break;
            case VzwSimCheckFragment.ACTION_SHOW_ACTIVATE_TIMEOUT:
                //setRightLabel("");
                mWelcomeSubTitleText.setVisibility(View.VISIBLE);
                mWelcomeSubTitleText.setText(R.string.activate_timeout_warn);
                break;
        }
    }

    private View.OnClickListener onClickSimStatusBodyListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mSimStatus == VzwSimCheckFragment.ACTION_SHOW_ACTIVATED) {
            }
        }
    };

    @Override
    protected boolean onBackKeyPressd(KeyEvent event) {
        getActivity().onBackPressed();
        return true;
    }

    @Override
    protected boolean onNextKeyPressed(KeyEvent event) {
        if (mSimStatus == VzwSimCheckFragment.ACTION_SHOW_PLAN_SELECTION /*||
                mSimStatus == VzwSimCheckFragment.ACTION_SHOW_ACTIVATE_TIMEOUT*/) { // cannot skip in mbb or timeout
            return true;
        }

        if (ActivationTracker.hasSimCard(getActivity())) {
            send_start_cloud(); //20190328 rwei start_cloud
            startFragmentPanel(VerizonCloudFragment.class.getName(), null);
        } else {
            startFragmentPanel(ReadyFragment.class.getName(), null);
        }
        return true;
    }
    //20190328 rwei start_cloud
    private void send_start_cloud(){
        Intent intent = new Intent("com.vcast.mediamanager.START_CLOUD");
        intent.setPackage("com.vcast.mediamanager");
        intent.setFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        getActivity().sendBroadcast(intent, "com.vcast.mediamanager.CLOUD_PERMISSION");
        System.out.println("===========---=========>>>>START_CLOUD");
    }
}
