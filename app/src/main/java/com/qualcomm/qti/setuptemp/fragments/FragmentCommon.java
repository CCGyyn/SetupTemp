package com.qualcomm.qti.setuptemp.fragments;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.app.Fragment;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.qualcomm.qti.setuptemp.DefaultActivity;
import com.qualcomm.qti.setuptemp.event.FragmentKeyEventDispatcher;
import com.qualcomm.qti.setuptemp.event.OnKeyLabelClickListener;
import com.qualcomm.qti.setuptemp.utils.Utils;

/**
 * base fragment class for activation ui
 * @author qinyu
 * @version 1.0
 */
public abstract class FragmentCommon extends Fragment implements FragmentKeyEventDispatcher, OnKeyLabelClickListener {
    private static final String TAG = FragmentCommon.class.getSimpleName();
    protected static final boolean DEBUG = Utils.DEBUG;
    public static final String PHONE_ACTIVATED = "phone_activated";
    public static final String AGREE_TERMS_CONTIDIONS = "agree_terms_contidions";
    public static final String ACTIVATED_PHONE_NUMBER = "activated_phone_number";

    private boolean mHidden;
    private final Handler mInternalHandler = new Handler(new HC());

    public FragmentCommon() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        int layoutResId = getLayoutResId();
        if (layoutResId == 0) return super.onCreateView(inflater, container, savedInstanceState);
        View view = inflater.inflate(layoutResId, container, false);
        onInitContent(view);
        return view;
    }

    public void onInitContent(View root) {

    }

    protected abstract int getLayoutResId();

    @Override
    public void onResume() {
        super.onResume();
        Activity activity = getActivity();
        activity.setTitle(getTitleString());
        if (activity instanceof DefaultActivity) {
            // label click listener
            ((DefaultActivity) activity).setOnKeyLabelClickListener(this);
            // fragment key event dispatcher
            ((DefaultActivity) activity).setFragmentKeyEventDispatcher(this);
        }

        clearLabels();
    }

    private void clearLabels() {
        setLeftLabel("");
        setCenterLabel("");
        setRightLabel("");
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        mHidden = hidden;
    }

    @Override
    public void onStop() {
        super.onStop();
        if (getActivity() instanceof DefaultActivity) {
            ((DefaultActivity) getActivity()).removeFragmentKeyEventDispatcher(this);
        }

        if (isRemoving() && !isVisible()) { // clear when change page
            getInternalHandler().removeCallbacksAndMessages(null);
        }
    }

    class HC implements Handler.Callback {

        @Override
        public boolean handleMessage(Message msg) {
            return FragmentCommon.this.handleMessage(msg);
        }

    }
    public boolean handleMessage(Message msg){
        return false;
    }

    public final boolean sendMessage(Message msg){
        return getInternalHandler().sendMessage(msg);
    }

    public final boolean sendMessageDelayed(Message msg, long delayMillis){
        return getInternalHandler().sendMessageDelayed(msg, delayMillis);
    }

    public final boolean sendEmptyMessage(int what){
        return getInternalHandler().sendEmptyMessage(what);
    }

    public void post(Runnable r){
        getInternalHandler().post(r);
    }

    public void postDelayed(Runnable r,long delayMillis){
        getInternalHandler().postDelayed(r,delayMillis);
    }

    public void removeCallbacksAndMessages(Object token) {
        getInternalHandler().removeCallbacksAndMessages(token);
    }

    public void removeCallbacks(Runnable r) {
        getInternalHandler().removeCallbacks(r);
    }

    public void removeMessages(int what) {
        getInternalHandler().removeMessages(what);
    }


    protected Handler getInternalHandler() {
        return mInternalHandler;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mInternalHandler != null) {
            if (DEBUG) Log.d(TAG, "onDestroy removeCallbacksAndMessages");
            mInternalHandler.removeCallbacksAndMessages(null);
        }
    }

    public void setActivated(boolean activated) {
        Settings.Secure.putInt(getActivity().getContentResolver(),
                PHONE_ACTIVATED, activated ? 1 : 0);
    }

    public boolean isActivated() {
        int activated = Settings.Secure.getInt(getActivity().getContentResolver(),
                PHONE_ACTIVATED, 0);
        return activated == 1;
    }

    public void setLeftLabel(String leftLabel) {
        if (getActivity() instanceof DefaultActivity) {
            ((DefaultActivity) getActivity()).setLeftLabel(leftLabel);
        }
    }

    public void setCenterLabel(String centerLabel) {
        if (getActivity() instanceof DefaultActivity) {
            ((DefaultActivity) getActivity()).setCenterLabel(centerLabel);
        }
    }

    public void setRightLabel(String rightLabel) {
        if (getActivity() instanceof DefaultActivity) {
            ((DefaultActivity) getActivity()).setRightLabel(rightLabel);
        }
    }

    /**
     * dispatch key event from activity ,this method will be called when down and up ( twice )
     * @param event
     * @return
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            return onKeyDown(event);
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            return onKeyUp(event);
        }
        return false;
    }

    /**
     * handle key up event in sub class
     *
     * @param event
     * @return
     */
    public boolean onKeyUp(KeyEvent event) {
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_SOFT_LEFT:
                if (onBackKeyPressd(event)) return true;
                break;
            case KeyEvent.KEYCODE_ENTER:
                if (onConfirmKeyPressed(event)) return true;
                break;
            case KeyEvent.KEYCODE_SOFT_RIGHT:
                if (onNextKeyPressed(event)) return true;
                break;
            case KeyEvent.KEYCODE_BACK:
                if (onCLRKeyPressed(event)) return true;
                break;
        }

        return false;
    }

    /**
     * handle the CLR key event
     *
     * @param event
     * @return
     */
    protected  boolean onCLRKeyPressed(KeyEvent event){
        return false;
    }

    /**
     * handle the next key event
     *
     * @param event
     * @return
     */
    protected boolean onNextKeyPressed(KeyEvent event) {
        return false;
    }

    /**
     * handle the confirm key event
     *
     * @param event
     * @return
     */
    protected boolean onConfirmKeyPressed(KeyEvent event) {
        return false;
    }

    /**
     * handle the back key event
     *
     * @param event
     * @return
     */
    protected boolean onBackKeyPressd(KeyEvent event) {
        return false;
    }

    /**
     * handle key down event in sub class
     *
     * @param event
     * @return
     */
    public boolean onKeyDown(KeyEvent event) {
        return false;
    }

    @Override
    public boolean isFragmentHidden() {
        return mHidden;
    }

    public abstract String getTitleString();

    @Override
    public void onLeftLabelClick(View v) {

    }

    @Override
    public void onCenterLabelClick(View v) {

    }

    @Override
    public void onRightLabelClick(View v) {

    }

    public void sendBroadcast(Intent intent) {
        getActivity().sendBroadcast(intent);
    }

    public void registerReceiver(BroadcastReceiver receiver, IntentFilter filter) {
        getActivity().registerReceiver(receiver, filter);
    }

    public void unregisterReceiver(BroadcastReceiver receiver) {
        getActivity().unregisterReceiver(receiver);
    }

    public void startFragmentPanel(String fragmentClass,Bundle args){
        startFragmentPanel(fragmentClass,
                args,0,null,null,0);
    }

    public void startFragmentPanel(String fragmentClass, Bundle args, int titleRes,
                                   CharSequence titleText, Fragment resultTo, int resultRequestCode) {
        if (getActivity() instanceof DefaultActivity) {
            ((DefaultActivity) getActivity()).startFragmentPanel(fragmentClass, args, titleRes,
                    titleText, resultTo, resultRequestCode);
        }
    }


    public Object getSystemService(String name) {
        return getActivity().getSystemService(name);
    }


    public static void provision(Activity activity) {
        // Add a persistent setting to allow other apps to know the device has been provisioned.
        ContentResolver resolver = activity.getContentResolver();
        Settings.Global.putInt(resolver, Settings.Global.DEVICE_PROVISIONED, 1);
        Settings.Secure.putInt(resolver, Settings.Secure.USER_SETUP_COMPLETE, 1);
        Settings.Secure.putInt(resolver, Settings.Secure.TV_USER_SETUP_COMPLETE, 1);
    }

    public static void finishSetup(Activity activity) {
        if (DEBUG) Log.e(TAG, "finishSetup..");

        Utils.enableComponentSetting(activity,DefaultActivity.class,false);
        provision(activity);

        if (DEBUG){
            ContentResolver resolver = activity.getContentResolver();
            Log.e(TAG, "activated = " + (Settings.Secure.getInt(resolver, PHONE_ACTIVATED, 0) == 1) +
                    ", agree terms & contidions = " + (Settings.Secure.getInt(resolver, AGREE_TERMS_CONTIDIONS, 0) == 1) +
                    ", activated phone number = " + (Settings.Secure.getString(resolver, ACTIVATED_PHONE_NUMBER)));
        }

        // terminate the activity.
        activity.finish();
    }
}
