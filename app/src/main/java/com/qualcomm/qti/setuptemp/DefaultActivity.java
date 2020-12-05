/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.qualcomm.qti.setuptemp;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;

import com.qualcomm.qti.setuptemp.event.ActivationTracker;
import com.qualcomm.qti.setuptemp.event.FragmentKeyEventDispatcher;
import com.qualcomm.qti.setuptemp.event.OnKeyLabelClickListener;
import com.qualcomm.qti.setuptemp.event.PcoDataObserver;
import com.qualcomm.qti.setuptemp.fragments.FragmentCommon;
import com.qualcomm.qti.setuptemp.fragments.VzwSimCheckFragment;
import com.qualcomm.qti.setuptemp.utils.Utils;

import java.util.ArrayList;
import java.util.List;


import static com.qualcomm.qti.setuptemp.fragments.FragmentCommon.PHONE_ACTIVATED;
import static com.qualcomm.qti.setuptemp.fragments.FragmentCommon.finishSetup;


/**
 * Application that sets the provisioned bit, like SetupWizard does.
 */

public class DefaultActivity extends Activity implements View.OnClickListener {
    public static final boolean DEBUG = true;

    private static final String TAG = DefaultActivity.class.getSimpleName();
    private static final String BACK_STACK_PREFS = "provision:prefs";
    public static final String START_FROM_DIALER = "start_from_dialer";
    public static final String START_FROM_NOTIFICATION = "start_from_notification";
    public static final String SAVED_FRAGMENT = "saved_fragment";
    public static final String SPECIFIC_PREF = "specific_pref";
    public static final String ARGS = "args";
    private TextView mLeftLabel, mCenterLabel, mRightLabel;
    private boolean mIsLabelClickable = false;
    private boolean isCallerNotification = false;
    private boolean isCallDialer = false;
    private String mSpecificPref;

    public boolean isCallerNotification() {
        return isCallerNotification;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getActionBar() != null) {  // hide home icon
            getActionBar().setDisplayShowHomeEnabled(false);
        }
        setContentView(R.layout.activity_main);
        // start observing if needed
        PcoDataObserver.startListen(this);
        ActivationTracker.getInstance(this).startTracking();

        initLabels();
        handleIntent(getIntent());
        registerFragmentLifecycleCallbacks();
        lunchFragment(savedInstanceState);

        if (DEBUG) Log.d(TAG, TAG + " -> onCreate");
    }

    FragmentManager.FragmentLifecycleCallbacks mFLCallbacks = new FragmentManager.FragmentLifecycleCallbacks() {
        public void onFragmentDetached(FragmentManager fm, Fragment f) {
            if (DEBUG) {
                Log.d(TAG, "onFragmentDetached=" + f);
            }

            int backStackEntryCount = fm.getBackStackEntryCount();
            if (backStackEntryCount == 0) {
                handleLastFragmentDetached();
            }
        }
    };

    private void registerFragmentLifecycleCallbacks() {
        getFragmentManager().registerFragmentLifecycleCallbacks(mFLCallbacks,false);
    }

    private void unregisterFragmentLifecycleCallbacks() {
        getFragmentManager().unregisterFragmentLifecycleCallbacks(mFLCallbacks);
    }

    private void handleLastFragmentDetached() {
        if (isCallDialer || isCallerNotification) {
            FragmentCommon.finishSetup(this);
        } else {
            lunchFragment(null);
        }
    }

    private void lunchFragment(Bundle savedInstanceState) {
        // restore state if needed, such as language switch
        String lunchFragment = null;
        Bundle extras = null;
        if (savedInstanceState != null && (lunchFragment = savedInstanceState.getString(SAVED_FRAGMENT)) != null) {
            if (!TextUtils.isEmpty(lunchFragment)) { // try to resume fragment
                FragmentManager fm = getFragmentManager();
                Fragment fragment = fm.findFragmentByTag(lunchFragment);  // tag is the name of the fragment
                if (fragment != null) { // fragment  instance  exists
                    if (DEBUG) {
                        Log.e(TAG, "lunchFragment=" + lunchFragment + " ,fragment=" + fragment.toString());
                    }
                    fm.beginTransaction().replace(R.id.id_content_container, fragment, lunchFragment).commit();
                    return;
                }
            }
        } else {
            if (mSpecificPref != null) {
                lunchFragment = mSpecificPref;
                extras = getIntent().getBundleExtra(ARGS);
                if (DEBUG) {
                    Log.d(TAG, "extras=" + extras);
                }
            } else {
                // start def fragment
                String defLunchFragmentClass = getString(R.string.def_lunch_fragment_class);
                boolean spec = TextUtils.isEmpty(defLunchFragmentClass) || isCallerNotification;
                lunchFragment = spec ? VzwSimCheckFragment.class.getName() : defLunchFragmentClass.trim();
            }
        }
        Log.d(TAG, "lunch fragment :" + lunchFragment);

        startFragmentPanel(lunchFragment, extras);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) {
            finishIfNeeded(true);
            return;
        }

        // handle dialer req
        isCallDialer = intent.getBooleanExtra(START_FROM_DIALER, false);
        if (DEBUG) Log.d(TAG, "isDialerReq=" + isCallDialer);

        // handle notification req
        isCallerNotification = intent.getBooleanExtra(START_FROM_NOTIFICATION, false);
        if (DEBUG) Log.d(TAG, "isNotiReq=" + isCallerNotification);

        mSpecificPref = intent.getStringExtra(SPECIFIC_PREF);
        if (DEBUG) Log.e(TAG, "mSpecificPref=" + mSpecificPref);

        boolean caller = isCallDialer || isCallerNotification;
        finishIfNeeded(!caller); // if not dialer or notification req

        if (caller) {
			resetSetupFlags();
		}
    }

    // due to the sys sync delay , we need check if the setup is complete
    // but the this activity component is active
    private void finishIfNeeded(boolean check) {
        if (check) {
            if (Utils.isSetupComplete(this)) {
                if (Utils.DEBUG) {
                    Log.e(TAG, "finishIfNeeded : the setup is complete" +
                            " but the activity component is active");
                }

                ActivityInfo info = Utils.getHomeActivityInfo(this);
                if (info != null) {
                    Intent intent = new Intent();
                    intent.setComponent(new ComponentName(info.packageName, info.name));
                    startActivity(intent);
                    if (Utils.DEBUG) Log.e(TAG, "start home page " + info.name);
                } else {
                    startActivity(Utils.getHomeIntent());
                }
                finishSetup(this);
            }
        }
    }

    private void initLabels() {
        mLeftLabel = findViewById(R.id.lsk_label);
        mCenterLabel = findViewById(R.id.csk_label);
        mRightLabel = findViewById(R.id.rsk_label);

        if (mIsLabelClickable) {
            mLeftLabel.setOnClickListener(this);
            mCenterLabel.setOnClickListener(this);
            mRightLabel.setOnClickListener(this);
        }
    }

    private void resetSetupFlags() {
        ContentResolver resolver = getContentResolver();
        Settings.Global.putInt(resolver, Settings.Global.DEVICE_PROVISIONED, 0);
        Settings.Secure.putInt(resolver, Settings.Secure.USER_SETUP_COMPLETE, 0);
        Settings.Secure.putInt(resolver, Settings.Secure.TV_USER_SETUP_COMPLETE, 0);
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Fragment current = getFragmentManager().findFragmentById(R.id.id_content_container);
        if (current != null && current instanceof com.qualcomm.qti.setuptemp.fragments.VerizonCloudFragment){
            startFragmentPanel(com.qualcomm.qti.setuptemp.fragments.ReadyFragment.class.getName(), null);
        }
        
    }
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Fragment fragment = getFragmentManager().findFragmentById(R.id.id_content_container);
        if (fragment != null) { // save state
            Log.d(TAG, "saved state fragment = " + fragment.getClass().getName());
            outState.putString("saved_fragment", fragment.getClass().getName());
        }
    }

    public void startFragmentPanel(String fragmentClass, Bundle args) {
        startFragmentPanel(fragmentClass, args, 0,
                null, null, 0);
    }

    public void startFragmentPanel(String fragmentClass, Bundle args, int titleRes,
                                   CharSequence titleText, Fragment resultTo, int resultRequestCode) {
        Fragment fragment = Fragment.instantiate(this, fragmentClass, args);
        if (resultTo != null) {
            fragment.setTargetFragment(resultTo, resultRequestCode);
        }
        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        transaction.replace(R.id.id_content_container, fragment, fragmentClass); // fragmentClass tag to identify which fragment it is
        if (titleRes != 0) {
            transaction.setBreadCrumbTitle(titleRes);
        } else if (titleText != null) {
            transaction.setBreadCrumbTitle(titleText);
        }
        //transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
        transaction.addToBackStack(BACK_STACK_PREFS);
        transaction.commitAllowingStateLoss();
    }

    private List<FragmentKeyEventDispatcher> mFragmentKeyEventDispatchers = new ArrayList<FragmentKeyEventDispatcher>();

    public void setFragmentKeyEventDispatcher(FragmentKeyEventDispatcher dispatcher) {
        if (!mFragmentKeyEventDispatchers.contains(dispatcher)) {
            mFragmentKeyEventDispatchers.add(dispatcher);
        }
    }

    public void removeFragmentKeyEventDispatcher(FragmentKeyEventDispatcher dispatcher) {
        if (mFragmentKeyEventDispatchers.contains(dispatcher)) {
            mFragmentKeyEventDispatchers.remove(dispatcher);
        }
    }


    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        Log.d(TAG, TAG + " dispatchKeyEvent : " + event.getKeyCode());
        if (mFragmentKeyEventDispatchers.size() > 0) {
            boolean consumed = false;
            for (FragmentKeyEventDispatcher dispatcher : mFragmentKeyEventDispatchers) {
                if (!dispatcher.isFragmentHidden() && dispatcher.dispatchKeyEvent(event)) {
                    consumed = true;
                }
            }

            if (consumed) return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterFragmentLifecycleCallbacks();
        ActivationTracker.getInstance(this).stopTracking();
    }

    public void setLeftLabel(String leftLabel) {
        if (leftLabel == null)
            throw new NullPointerException("leftLabel is null");

        mLeftLabel.setText(leftLabel);
    }

    public void setCenterLabel(String centerLabel) {
        if (centerLabel == null)
            throw new NullPointerException("centerLabel is null");

        mCenterLabel.setText(centerLabel);
    }

    public void setRightLabel(String rightLabel) {
        if (rightLabel == null)
            throw new NullPointerException("rightLabel is null");

        mRightLabel.setText(rightLabel);
    }

    @Override
    public void onClick(View view) {
        if (mKeyLabelClickListener != null) {
            switch (view.getId()) {
                case R.id.lsk_label:
                    mKeyLabelClickListener.onLeftLabelClick(view);
                    break;
                case R.id.csk_label:
                    mKeyLabelClickListener.onCenterLabelClick(view);
                    break;
                case R.id.rsk_label:
                    mKeyLabelClickListener.onRightLabelClick(view);
                    break;
            }
        }
    }

    private OnKeyLabelClickListener mKeyLabelClickListener;

    public void setOnKeyLabelClickListener(OnKeyLabelClickListener keyLabelClickListener) {
        this.mKeyLabelClickListener = keyLabelClickListener;
    }
}
