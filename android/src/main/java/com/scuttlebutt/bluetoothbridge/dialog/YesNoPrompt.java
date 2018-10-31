package com.scuttlebutt.bluetoothbridge.dialog;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.util.Log;

import com.facebook.react.bridge.ReactApplicationContext;
import com.scuttlebutt.bluetoothbridge.BluetoothSocketBridgeModule;

public class YesNoPrompt {

    private static String TAG = "yesNoDialog";

    private final BluetoothSocketBridgeModule module;

    public YesNoPrompt(BluetoothSocketBridgeModule module) {
        this.module = module;
    }

    public void showYesNoDialog(final String message, final DialogInterface.OnClickListener dialogClickListener) {
        Log.d(TAG, "Showing yes/no dialog for incoming connection");

        module.showYesNoDialog(message, dialogClickListener);
    }

}
