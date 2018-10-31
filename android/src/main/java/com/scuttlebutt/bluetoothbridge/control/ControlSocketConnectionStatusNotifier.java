package com.scuttlebutt.bluetoothbridge.control;

import com.scuttlebutt.bluetoothbridge.bridge.ConnectionStatusNotifier;

public class ControlSocketConnectionStatusNotifier implements ConnectionStatusNotifier {

    private final ControlUnixSocket controlUnixSocket;

    public ControlSocketConnectionStatusNotifier(ControlUnixSocket controlUnixSocket) {
        this.controlUnixSocket = controlUnixSocket;
    }

    public void onConnectionSuccess(String remoteAddress, boolean incoming) {
        controlUnixSocket.sendConnectedEvent(remoteAddress, incoming);
    }

    public void onConnectionFailure(String remoteAddress, String reason, boolean incoming) {
        controlUnixSocket.sendConnectionFailureEvent(remoteAddress, reason, incoming);
    }

    public void onDisconnect(String remoteAddress, String reason) {
        controlUnixSocket.sendDisconnectionEvent(remoteAddress, reason);
    }

}
