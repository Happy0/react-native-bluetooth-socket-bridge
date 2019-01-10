package com.scuttlebutt.bluetoothbridge.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;

import java.util.UUID;

import java.io.OutputStream;

import android.os.Handler;
import android.util.Log;


/**
 * A service that just sends connecting clients the same payload every time, and then
 * disconnects when it has finished sending.
 */
public class BluetoothMetadataService {

    private static String TAG = "bluetooth_metadata_service";

    private final BluetoothAdapter bluetoothAdapter;

    public BluetoothMetadataService(BluetoothAdapter bluetoothAdapter) {
        this.bluetoothAdapter = bluetoothAdapter;
    }

    public synchronized void startMetadataService(
            String serviceName,
            String serviceUUID,
            String metadata,
            long timeSeconds) {

        Log.d(TAG, String.format("Attempting to start metadata service with params %s %s %s %d", serviceName, serviceUUID, metadata, timeSeconds));

        UUID uuid = UUID.fromString(serviceUUID);

        BluetoothServerSocket bluetoothServerSocket = BluetoothAdapter
                .getDefaultAdapter()
                .listenUsingRfcommWithServiceRecord(serviceName, uuid);

        Runnable runnable = new Runnable() {

            @Override
            public void run() {

                try {
                    while (true) {

                        if (!bluetoothAdapter.isEnabled()) {
                            break;
                        }

                        final BluetoothSocket socket = bluetoothServerSocket.accept();
                        Log.d(TAG, "Accepted incoming connection to bluetooth metadata service.");

                        try {
                            OutputStream outputStream = socket.getOutputStream();
                            outputStream.write(metadata.getBytes());
                            outputStream.flush();

                            outputStream.close();
                        } finally {
                            socket.close();
                        }
                    }
                } finally {
                    Log.d(TAG, "Closing bluetooth metadata service socket.");
                    bluetoothServerSocket.close();
                }
            }

        };

        Thread thread = new Thread(runnable);
        thread.start();

        Handler handler = new Handler();
        handler.postDelayed(stopServerThread(thread), timeSeconds * 1000);
    }

    public Runnable stopServerThread(Thread thread) {
        Log.d(TAG, "Stopping bluetooth metadata service");
        thread.stop();
    }

}