package com.qualcomm.qti.setuptemp.poa;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;

import com.qualcomm.qti.setuptemp.R;
import com.qualcomm.qti.setuptemp.fragments.FragmentCommon;

public  abstract class BasePoaFragment extends FragmentCommon implements  View.OnKeyListener {
	protected static final String TAG = BasePoaFragment.class.getSimpleName();
	private FrameLayout mContainer;

	private Button mEmergencyBtn;
	private Button mCenterFuncBtn;
	private Button mRightFuncBtn;
	private Context mContext;

	@Override
	protected int getLayoutResId() {
		return R.layout.fragment_poa_base;
	}

	@Override
	public String getTitleString() {
		return null;
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		mContext = activity.getApplicationContext();
	}

	@Override
	public void onInitContent(View root) {
		super.onInitContent(root);
		mContainer = (FrameLayout) root.findViewById(R.id.base_poa_container);
		mEmergencyBtn = (Button) root.findViewById(R.id.emergency_button_btn);
		mCenterFuncBtn = (Button) root.findViewById(R.id.function_center_button);
		mRightFuncBtn = (Button) root.findViewById(R.id.function_right_button);

		/// add child
		View child = LayoutInflater.from(getContext()).inflate(getContentLayoutResId(), null);
		FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT);
		mContainer.addView(child,params);

		mEmergencyBtn.setOnKeyListener(this);
		/// init content for child
		initContent(mContainer);
	}

	@Override
	public void onResume() {
		super.onResume();

	}

	protected abstract void initContent(ViewGroup container);

	protected abstract int getContentLayoutResId();

	public FrameLayout getContainer() {
		return mContainer;
	}

	public Context getContext() {
		return mContext;
	}

	@Override
	public boolean onKey(View v, int keyCode, KeyEvent event) {
		if (event.getAction() == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_ENTER) { // up
			switch (v.getId()) {
				case R.id.emergency_button_btn:
					/// call em
					Log.e(TAG, "emergency call 911");
					Intent intent = new Intent(Intent.ACTION_DIAL, Uri.fromParts(
							"tel", "911", null));
					startActivity(intent);
					return true;
			}
		}
		return false;
	}

	protected void clearIfNeeded() {
		if (DEBUG) {
			Log.d(TAG, "clear before page changes");
		}
		removeCallbacksAndMessages(null);
	}

	protected void setTitle(String title) {
		getActivity().setTitle(title);
	}

	@Override
	public void startFragmentPanel(String fragmentClass, Bundle args, int titleRes, CharSequence titleText, Fragment resultTo, int resultRequestCode) {
		clearIfNeeded(); // clear before page changes
		super.startFragmentPanel(fragmentClass, args, titleRes, titleText, resultTo, resultRequestCode);
		if (DEBUG) {
			Log.e(TAG, "start pref : " + fragmentClass);
		}
	}

	public void sendEmptyMessageDelayed(int what, long delayMillis) {
		getInternalHandler().sendEmptyMessageDelayed(what, delayMillis);
	}

	/***
	 *  invalid fragment state currently
	 * @return true, currently fragment state is invalid
	 */
	public boolean isInvalidFragmentState() {
		if (DEBUG) {
			Log.e(TAG, "isVisible=" + isVisible() + " ,isRemoving=" + isRemoving() + " ,isDetached=" + isDetached());
		}
		return !isVisible() || isRemoving() || isDetached();
	}

	public void setCenterFuncBtnOnClickListener(OnClickListener listener) {
		mCenterFuncBtn.setOnClickListener(listener);
	}

	public void setRightFuncBtnOnClickListener(OnClickListener listener) {
		mRightFuncBtn.setOnClickListener(listener);
	}

	protected void setEmergencyBtnVisibility(int visibility) {
		setButtonVisibility(mEmergencyBtn,visibility);
	}

	protected void setCenterFuncBtnVisibility(int visibility) {
		setButtonVisibility(mCenterFuncBtn,visibility);
	}

	protected void setRightFuncBtnVisibility(int visibility) {
		setButtonVisibility(mRightFuncBtn,visibility);
	}

	protected void setButtonVisibility(Button button, int visibility) {
		button.setVisibility(visibility);
	}

	public Button getEmergencyBtn() {
		return mEmergencyBtn;
	}

	public Button getCenterFuncBtn() {
		return mCenterFuncBtn;
	}

	public Button getRightFuncBtn() {
		return mRightFuncBtn;
	}
}
