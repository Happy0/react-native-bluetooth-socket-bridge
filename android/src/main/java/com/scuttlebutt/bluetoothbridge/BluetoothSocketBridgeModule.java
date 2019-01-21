package com.scuttlebutt.bluetoothbridge;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;

import com.scuttlebutt.bluetoothbridge.bluetooth.BluetoothController;
import com.scuttlebutt.bluetoothbridge.bluetooth.BluetoothIncomingController;
import com.scuttlebutt.bluetoothbridge.bridge.ConnectionBridge;
import com.scuttlebutt.bluetoothbridge.bridge.ConnectionStatusNotifier;
import com.scuttlebutt.bluetoothbridge.control.ControlSocketConnectionStatusNotifier;
import com.scuttlebutt.bluetoothbridge.control.ControlUnixSocket;
import com.scuttlebutt.bluetoothbridge.control.MakeDeviceDiscoverableHandler;
import com.scuttlebutt.bluetoothbridge.dialog.YesNoPrompt;

import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import com.facebook.react.bridge.BaseActivityEventListener;


public class BluetoothSocketBridgeModule extends ReactContextBaseJavaModule implements ActivityEventListener {

  private final ReactApplicationContext reactContext;
  private final BluetoothSocketBridgeConfiguration configuration;

  private static final int REQUEST_MAKE_DISCOVERABLE = 333;

  private static String TAG = "socket_bridge_module";

  private MakeDeviceDiscoverableHandler makeDeviceDiscoverableHandler = null;

  public BluetoothSocketBridgeModule(ReactApplicationContext reactContext,
                                     BluetoothSocketBridgeConfiguration configuration) {
    super(reactContext);
    this.configuration = configuration;
    this.reactContext = reactContext;

    init();
  }

  private void init() {

    String outgoingSocketPath = configuration.getOutgoingSocketPath();
    String incomingSocketPath = configuration.getIncomingSocketPath();

    BluetoothAdapter defaultAdapter = BluetoothAdapter.getDefaultAdapter();

    UUID uuid = configuration.getServiceUUID();

    BluetoothController bluetoothController = new BluetoothController(this, reactContext, defaultAdapter);

    // Used to communicate remote devices to connect to between the control socket and the bridge socket,
    // and make the attempts to outgoing devices sequential
    BlockingQueue<String> awaitingOutgoingConnection = new LinkedBlockingQueue<>();

    ControlUnixSocket controlUnixSocket = new ControlUnixSocket(
            configuration,
            bluetoothController,
            awaitingOutgoingConnection);

    ConnectionStatusNotifier connectionStatusNotifier = new ControlSocketConnectionStatusNotifier(controlUnixSocket);

    ConnectionBridge connectionBridge = new ConnectionBridge(
            awaitingOutgoingConnection,
            outgoingSocketPath,
            incomingSocketPath,
            configuration.getServiceUUID(),
            connectionStatusNotifier,
            BluetoothAdapter.getDefaultAdapter());

    BluetoothIncomingController bluetoothIncomingController = new BluetoothIncomingController(
            connectionBridge,
            bluetoothController,
            new YesNoPrompt(this),
            configuration.getServiceName(),
            uuid
    );

    controlUnixSocket.start();
    connectionBridge.listenForOutgoingConnections();
    bluetoothIncomingController.start();

    this.reactContext.addActivityEventListener(this);
  }

  public synchronized void makeDeviceDiscoverable(int timeSeconds, MakeDeviceDiscoverableHandler handler) {

    Log.d(TAG, "Asking to make device discoverable for "  + timeSeconds + " seconds");

    Activity activity = getCurrentActivity();

    if (makeDeviceDiscoverableHandler != null) {
      handler.handleAlreadyInProgress();
    } else if (activity == null) {
      Log.d(TAG, "Cannot make device discoverable because activity is null");
      handler.handleAppNotAtFront();
    } else {
      // Make the device discoverable for a limited duration
      Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
      discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, timeSeconds);

      this.makeDeviceDiscoverableHandler = handler;
      activity.startActivityForResult(
              discoverableIntent,
              REQUEST_MAKE_DISCOVERABLE,
              Bundle.EMPTY);
    }

  }

  public synchronized void showYesNoDialog(final String message, final DialogInterface.OnClickListener dialogClickListener) {

    Log.d(TAG, "About to show yes/no dialog on ui thread.");

    Activity currentActivity = getCurrentActivity();

    if (currentActivity != null) {
      currentActivity.runOnUiThread(new Runnable() {
        @Override
        public void run() {
          AlertDialog.Builder builder = new AlertDialog.Builder(getCurrentActivity());
          builder.setMessage(message).setPositiveButton("Yes", dialogClickListener)
                  .setNegativeButton("No", dialogClickListener).show();
        }
      });
    } else {
      Log.d(TAG, "Current activity was null, cannot ask user to accept incoming connection.");
    }


  }

  @Override
  public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
    if (requestCode == REQUEST_MAKE_DISCOVERABLE) {

      // For some reason, the result code is the length of time the user permitted discoverability
      // for...
      if (resultCode > 0) {
        Log.d(TAG, "User allowed the device to be discovered.");

        if (makeDeviceDiscoverableHandler != null) {
          long currentTime = System.currentTimeMillis();
          long discoverableForMillis = resultCode * 1000;
          long estimatedDiscoverableUntil = currentTime + discoverableForMillis;

          makeDeviceDiscoverableHandler.handleSuccess(estimatedDiscoverableUntil);
        }

      } else {
        Log.d(TAG, "User did not allow the device to be discovered");
        Log.d(TAG, "Result code was: " + resultCode);

        if (makeDeviceDiscoverableHandler != null)
          makeDeviceDiscoverableHandler.handleUserDidNotAllow();
      }

      // Will be made non-null again on future discoverability requests
      makeDeviceDiscoverableHandler = null;
    }
  }

  @Override
  public void onNewIntent(Intent intent) {

  }

  @Override
  public void onCatalystInstanceDestroy() {
    // When does this happen? What does it mean?
  }

  @Override
  public void initialize() {
    super.initialize();
  }

  @Override
  public String getName() {
    return "RNReactNativeBluetoothSocketBridge";
  }
}