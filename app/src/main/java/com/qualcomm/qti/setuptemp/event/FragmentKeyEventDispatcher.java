package com.qualcomm.qti.setuptemp.event;

import android.content.Context;
import android.view.KeyEvent;

public interface FragmentKeyEventDispatcher {
    boolean dispatchKeyEvent(KeyEvent event);
    boolean isFragmentHidden();
}
