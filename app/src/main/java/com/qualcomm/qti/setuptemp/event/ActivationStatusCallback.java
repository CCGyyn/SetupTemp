package com.qualcomm.qti.setuptemp.event;

public interface ActivationStatusCallback {
	default void onSimCardStatusChanged(int status, String description){}

	default void onActivateSuccessful(String mdn, int pco){}

	default void onActivateFailed(int reason, int pco){}

	default void onActivateTimeout(){}

	default void onActivateWithMBB(String mdn, int pco){}

	default void onPcoReceived(String mdn, int pco){}
}
