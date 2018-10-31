package com.scuttlebutt.bluetoothbridge.control;

public class BluetoothDeviceProperties {

    private final String remoteAddress;
    private final String displayName;

    public BluetoothDeviceProperties(String remoteAddress, String displayName) {
        this.remoteAddress = remoteAddress;
        this.displayName = displayName;
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }

    public String getDisplayName() {
        return displayName;
    }
}
