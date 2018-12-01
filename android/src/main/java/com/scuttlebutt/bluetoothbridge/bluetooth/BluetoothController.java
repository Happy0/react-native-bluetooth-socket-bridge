package com.scuttlebutt.bluetoothbridge.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import com.facebook.react.bridge.ReactApplicationContext;
import com.scuttlebutt.bluetoothbridge.BluetoothSocketBridgeModule;
import com.scuttlebutt.bluetoothbridge.control.DiscoveredDevicesHandler;
import com.scuttlebutt.bluetoothbridge.control.MakeDeviceDiscoverableHandler;
import com.scuttlebutt.bluetoothbridge.receivers.BluetoothEnablednessHandler;


import java.util.ArrayList;
import java.util.List;

import android.util.Log;

public class BluetoothController {

    private static final String TAG = "bluetooth_bridge_ctrl";

    private final BluetoothSocketBridgeModule module;
    private final BluetoothAdapter adapter;
    private final ReactApplicationContext reactApplicationContext;

    public BluetoothController(
            BluetoothSocketBridgeModule module,
            ReactApplicationContext reactApplicationContext,
            BluetoothAdapter adapter
    ) {
        this.module = module;
        this.reactApplicationContext = reactApplicationContext;
        this.adapter = adapter;
    }

    public void discoverNearbyDevices(DiscoveredDevicesHandler devicesHandler) {
        Log.d(TAG, "Discover nearby called");

        // todo: handle error?
        registerBluetoothDeviceDiscoveryReceiver(devicesHandler);

        if (adapter == null ) {
            devicesHandler.onBluetoothNotSupported();
        } else if (!adapter.isEnabled()) {
            devicesHandler.onBluetoothDisabled();
        }

        if (adapter != null) {
            adapter.startDiscovery();
        }
    }

    public void registerBluetoothEnablednessListener(final BluetoothEnablednessHandler handler) {

        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);

        final BroadcastReceiver mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();

                if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                    final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                            BluetoothAdapter.ERROR);
                    switch (state) {
                        case BluetoothAdapter.STATE_OFF:
                            handler.onDisabled();
                            break;
                        case BluetoothAdapter.STATE_TURNING_OFF:
                            break;
                        case BluetoothAdapter.STATE_ON:
                            handler.onEnabled();
                            break;
                        case BluetoothAdapter.STATE_TURNING_ON:
                            break;
                    }
                }
            }
        };

        reactApplicationContext.registerReceiver(mReceiver, filter);
    }

    public void makeDeviceDiscoverable(int timeDiscoverable, MakeDeviceDiscoverableHandler responseHandler) {
        module.makeDeviceDiscoverable(timeDiscoverable, responseHandler);
    }

    public boolean isEnabled() {
        return adapter.isEnabled();
    }

    public String getOwnMacAddress() {
        return adapter.getAddress();
    }

    /**
     * Register receiver for bluetooth device discovery
     * @param handler
     */
    private void registerBluetoothDeviceDiscoveryReceiver(final DiscoveredDevicesHandler handler) {
        IntentFilter intentFilter = new IntentFilter();

        intentFilter.addAction(BluetoothDevice.ACTION_FOUND);
        intentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);

        final BroadcastReceiver deviceDiscoveryReceiver = new BroadcastReceiver() {
            private List<BluetoothDevice> devices = new ArrayList<>();
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                Log.d(TAG, "onReceive called");

                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    devices.add(device);
                } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                    Log.d(TAG, "Discovery finished");

                    handler.onDiscovered(devices);

                    try {
                        reactApplicationContext.unregisterReceiver(this);
                    } catch (Exception e) {
                        Log.e(TAG, "Unable to unregister receiver", e);
                    }
                }
            }
        };

        reactApplicationContext.registerReceiver(deviceDiscoveryReceiver, intentFilter);
    }
}
