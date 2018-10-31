package com.scuttlebutt.bluetoothbridge;

import java.util.UUID;

public class BluetoothSocketBridgeConfiguration {

    private final String socketFolderPath;
    private final UUID serviceUUID;
    private final String incomingSocketName;
    private final String getOutgoingSocketName;
    private final String controlSocketName;
    private String serviceName;

    public BluetoothSocketBridgeConfiguration(
            String socketFolderPath,
            String incomingSocketName,
            String outgoingSocketName,
            String controlSocketName,
            String serviceName,
            UUID serviceUUID) {
        this.serviceName = serviceName;
        this.socketFolderPath = socketFolderPath;
        this.serviceUUID = serviceUUID;

        this.incomingSocketName = incomingSocketName;
        this.getOutgoingSocketName = outgoingSocketName;
        this.controlSocketName = controlSocketName;
    }

    public String getSocketFolderPath() {
        return socketFolderPath;
    }

    public UUID getServiceUUID() {
        return serviceUUID;
    }

    private String getUnixSocketPath(String socketName) {
        return socketFolderPath + "/" + socketName;
    }

    public String getOutgoingSocketPath() {
        return getUnixSocketPath(getOutgoingSocketName);
    }

    public String getIncomingSocketPath() {
        return getUnixSocketPath(incomingSocketName);
    }

    public String getControlSocketPath() {
        return getUnixSocketPath(controlSocketName);
    }

    public String getServiceName() {
        return serviceName;
    }
}
