package com.qualcomm.qti.setuptemp.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ScrollView;

public class ScrollViewExt extends ScrollView {

	private boolean isScrolledToTop;
	private boolean isScrolledToBottom;
	private IScrollViewChangedListener mScrollChangedListener;


	public ScrollViewExt(Context context) {
		super(context);
	}

	public ScrollViewExt(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	public ScrollViewExt(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public void setScrollViewListener(IScrollViewChangedListener scrollViewListener) {
		this.mScrollChangedListener = scrollViewListener;
	}

	@Override
	protected void onScrollChanged(int l, int t, int oldl, int oldt) {
		super.onScrollChanged(l, t, oldl, oldt);
		// We take the only child in the scrollview
		View view = getChildAt(0);
		if (view == null) {
			return;
		}

		int dy = (view.getBottom() - (getHeight() + getScrollY()));
		// if dy is zero, then the bottom has been reached
		isScrolledToBottom = false;
		isScrolledToTop = false;
		if (dy <= 50) {
			isScrolledToBottom = true;
			if (mScrollChangedListener != null)
				mScrollChangedListener.onScrolledToBottom();
		} else if (getScrollY() == 0) {
			isScrolledToTop = true;
			if (mScrollChangedListener != null)
				mScrollChangedListener.onScrolledToTop();
		}
	}

	public boolean isTopReached() {
		return isScrolledToTop;
	}

	public boolean isBottomReached() {
		return isScrolledToBottom;
	}

	/**
	 * listener for listen top/bottom state
	 */
	public interface IScrollViewChangedListener {
		default void onScrolledToBottom() {

		}

		default void onScrolledToTop() {

		}
	}

}
