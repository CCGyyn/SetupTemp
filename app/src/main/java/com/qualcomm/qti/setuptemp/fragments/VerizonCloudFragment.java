package com.qualcomm.qti.setuptemp.fragments;

import android.provider.Settings;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import com.qualcomm.qti.setuptemp.R;

public class VerizonCloudFragment extends FragmentCommon {
    private static final String TAG = VerizonCloudFragment.class.getSimpleName();
    public boolean isNext;
    private TextView mCloundInfo;

    @Override
    public String getTitleString() {
        return getString(R.string.verizon_cloud_title);
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.fragment_verizon_cloud;
    }

    @Override
    public void onInitContent(View root) {
        super.onInitContent(root);
        mCloundInfo = (TextView) root.findViewById(R.id.id_clound_info);
    }


    @Override
    public void onResume() {
        super.onResume();

        setLabelsForCloud();
        showCloudUsage();
    }

    private void setLabelsForCloud() {
        setLeftLabel(getString(R.string.label_skip));
        setCenterLabel(getString(R.string.label_view));
        setRightLabel(getString(R.string.label_next));
    }

    private void showCloudUsage() {

        final SpannableStringBuilder style = new SpannableStringBuilder();

        //设置文字
        String info = getString(R.string.cloud_use_info);
        style.append(info);

        //设置部分文字点击事件
        ClickableSpan clickableSpan = new ClickableSpan() {
            @Override
            public void onClick(View widget) {
                startFragmentPanel(TermsAndContidions.class.getName(),
                        null, 0, null, null, 0);
            }
        };

        // 设置可点击的部分
        int start = info.lastIndexOf("T");
        style.setSpan(clickableSpan, start, info.length() - 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        mCloundInfo.setText(style);


        //配置给TextView
        mCloundInfo.setMovementMethod(LinkMovementMethod.getInstance());
        mCloundInfo.setText(style);
    }


    @Override
    public void onLeftLabelClick(View v) {
        super.onLeftLabelClick(v);
        startFragmentPanel(ReadyFragment.class.getName(), null);
    }

    @Override
    public void onRightLabelClick(View v) {
        super.onRightLabelClick(v);
        //startCloud();
       // finishSetup(getActivity());
      //  startFragmentPanel(ReadyFragment.class.getName(), null);
    }

    @Override
    protected boolean onBackKeyPressd(KeyEvent event) {
        // skip conditions. set to 0
        Settings.Secure.putInt(getActivity().getContentResolver(), AGREE_TERMS_CONTIDIONS, 0);
        startFragmentPanel(ReadyFragment.class.getName(), null);
        return true;
    }

    @Override
    protected boolean onNextKeyPressed(KeyEvent event) {
        isNext=true;

        // agree conditions. set to 1
        Intent intent = new Intent("com.vcast.mediamanager.ReceiverSelections.SEND_SELECTIONS");
        intent.setPackage("com.vcast.mediamanager");
        intent.setFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        intent.putExtra("com.vcast.mediamanager.ReceiverSelections.SELECTIONS_OBJECT", "{\"contacts.sync\": \"true\"}");
        getActivity().sendBroadcast(intent, "com.vcast.mediamanager.CLOUD_PERMISSION");
        System.out.println("=================---=============SEND_SELECTIONS");


      Settings.Secure.putInt(getActivity().getContentResolver(), AGREE_TERMS_CONTIDIONS, 1);
       startFragmentPanel(ReadyFragment.class.getName(), null);
       // startCloud();
        return super.onNextKeyPressed(event);
    }
    private void startCloud() {
        startTargetActivity(new ComponentName("com.vcast.mediamanager", "com.synchronoss.syncdrive.android.uibasic.activities.HomeScreenActivity"));
    }
    private void startTargetActivity(ComponentName name) {
        Intent intent = new Intent();
        // start activity in new task and clear all activities above it.
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.setComponent(name);
        if (intent.resolveActivityInfo(getActivity().getPackageManager(), PackageManager.MATCH_DEFAULT_ONLY) != null) {
            startActivity(intent);
        }
    }
}

