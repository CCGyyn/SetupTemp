package com.qualcomm.qti.setuptemp.fragments;

import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;

import com.qualcomm.qti.setuptemp.DefaultActivity;
import com.qualcomm.qti.setuptemp.R;
import com.qualcomm.qti.setuptemp.utils.Utils;

import java.util.List;

public class ReadyFragment extends FragmentCommon {
    private static final String TAG = ReadyFragment.class.getSimpleName();
    private ProgressDialog mDialog;

    @Override
    public String getTitleString() {
        return getString(R.string.summary_title);
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.simple_text_with_scroll;
    }

    @Override
    public void onInitContent(View root) {
        super.onInitContent(root);
        TextView textView = (TextView) root.findViewById(R.id.id_info);
        textView.setText(R.string.ready_to_go);
        textView.setTextSize(25);
        textView.setMovementMethod(ScrollingMovementMethod.getInstance());
    }

    @Override
    public void onResume() {
        super.onResume();
        setCenterLabel(getString(R.string.label_got_it));
    }

    @Override
    protected boolean onConfirmKeyPressed(KeyEvent event) {
        // disable def activity
        Utils.enableComponentSetting(getActivity(), DefaultActivity.class,false);
        provision(getActivity());
        goHome();
        return super.onConfirmKeyPressed(event);
    }

    @Override
    protected boolean onCLRKeyPressed(KeyEvent event) {
        if (DEBUG) {
            Log.d(TAG, "onCLRKeyPressed");
        }
        return true;
    }

    @Override
    public void onCenterLabelClick(View v) {
        super.onCenterLabelClick(v);
		// disable def activity
		Utils.enableComponentSetting(getActivity(), DefaultActivity.class,false);
		provision(getActivity());
        goHome();
    }

    private void goHome() {
        Intent intent = getHomeIntent();
        // query available home activity info
        List<ResolveInfo> resolveInfos = getActivity().getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
        String thisPackageName = getActivity().getPackageName();

        for (ResolveInfo resolveInfo : resolveInfos) {
            ActivityInfo activityInfo = resolveInfo.activityInfo;
            if (DEBUG) Log.d(TAG, "activity = " + activityInfo.name + " , package = " + activityInfo.packageName);
            if (!thisPackageName.equals(activityInfo.packageName)) { // package is not this
                // showGoHomeDialog(); // may wait,so show a dialog
                // Toast.makeText(getActivity(), "go home", Toast.LENGTH_SHORT).show();
                // component to lunch
                Intent i = new Intent("com.vcast.mediamanager.END_SETUP");
                i.setPackage("com.vcast.mediamanager");
                i.setFlags(Intent.FLAG_RECEIVER_FOREGROUND);
                getActivity().sendBroadcast(i, "com.vcast.mediamanager.CLOUD_PERMISSION");
                System.out.println("=================---=============END_SETUP");
                ComponentName component = new ComponentName(activityInfo.packageName, activityInfo.name);
                intent.setComponent(component);
                startActivity(intent);
				getActivity().finish();
                return;
            }
        }

        // if can go here , there is something wrong
        Log.e(TAG, "fatal error : no home found to lunch");
    }

    private Intent getHomeIntent() {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        return intent;
    }

    private void showGoHomeDialog() {
        mDialog = new ProgressDialog(getActivity());
        mDialog.setMessage("go home..");
        mDialog.show();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mDialog != null) {
            mDialog.dismiss();
        }
    }
}

