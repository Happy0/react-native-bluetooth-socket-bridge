package com.scuttlebutt.bluetoothbridge.bridge;


public interface ConnectionStatusNotifier {

    void onConnectionSuccess(String remoteAddress, boolean incoming);

    void onConnectionFailure(String remoteAddress, String reason, boolean incoming);

    void onDisconnect(String remoteAddress, String reason);

}
