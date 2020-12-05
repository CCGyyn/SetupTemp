package com.qualcomm.qti.setuptemp.fragments;

import android.text.method.ScrollingMovementMethod;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;

import com.qualcomm.qti.setuptemp.R;

public class TermsAndContidions extends FragmentCommon {
    @Override
    public String getTitleString() {
        return getString(R.string.terms_conditions_title);
    }

    @Override
    protected int getLayoutResId() {
        return R.layout.simple_text_with_scroll;
    }

    @Override
    public void onInitContent(View root) {
        super.onInitContent(root);
        TextView textView = (TextView) root.findViewById(R.id.id_info);
        textView.setText(R.string.terms_conditions);
        textView.setTextSize(22);
        textView.setMovementMethod(ScrollingMovementMethod.getInstance());

    }

    @Override
    public void onResume() {
        super.onResume();
        setLeftLabel(getString(R.string.label_back));
    }

    @Override
    public void onLeftLabelClick(View v) {
        super.onLeftLabelClick(v);
        getActivity().onBackPressed();  // back
    }

    @Override
    protected boolean onBackKeyPressd(KeyEvent event) {
        getActivity().onBackPressed();
        return true;
    }
}
