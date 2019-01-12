package com.scuttlebutt.bluetoothbridge.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;

import java.util.UUID;

import java.io.InputStream;
import java.io.OutputStream;
import android.bluetooth.BluetoothDevice;
import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import com.fasterxml.jackson.core.type.TypeReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scuttlebutt.bluetoothbridge.control.GetMetadataHandler;

import java.util.HashMap;
import java.util.Map;

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
            final String serviceName,
            final String serviceUUID,
            final String metadata,
            long timeSeconds) throws IOException {

        Log.d(TAG, String.format("Attempting to start metadata service with params %s %s %s %d", serviceName, serviceUUID, metadata, timeSeconds));

        UUID uuid = UUID.fromString(serviceUUID);

        final BluetoothServerSocket bluetoothServerSocket = BluetoothAdapter
                .getDefaultAdapter()
                .listenUsingRfcommWithServiceRecord(serviceName, uuid);

        Runnable runnable = new Runnable() {

            @Override
            public void run() {

                boolean closed = false;

                try {
                    while (true) {

                        if (!bluetoothAdapter.isEnabled() || closed) {
                            break;
                        }

                        try {
                            final BluetoothSocket socket = bluetoothServerSocket.accept();
                            Log.d(TAG, "Accepted incoming connection to bluetooth metadata service.");

                            try {
                                OutputStream outputStream = socket.getOutputStream();
                                outputStream.write(metadata.getBytes());
                                outputStream.flush();
                            } finally {
                                // socket.close();
                            }
                        } catch (IOException ex) {
                            Log.d(TAG, "IOException while writing payload: " + ex.getMessage());
                        }

                    }
                } finally {
                    Log.d(TAG, "Closing bluetooth metadata service socket.");

                    try {
                        bluetoothServerSocket.close();
                    } catch (IOException ex) {

                    } finally {
                        closed = true;
                    }

                }
            }

        };

        Thread thread = new Thread(runnable);
        thread.start();

        Thread stopServerThread = new Thread(stopServerThread(thread, timeSeconds * 1000));
        stopServerThread.start();

        return;
    }

    public void getInfoFromMetadataService(
            final String deviceAddress,
            final String serviceUUID,
            final GetMetadataHandler handler
    ) {
        Log.d(TAG, String.format("Attempting to get metadata with params %s %s", deviceAddress, serviceUUID));

        final UUID uuid = UUID.fromString(serviceUUID);

        Runnable runnable = new Runnable() {
            public void run() {

                BluetoothSocket bluetoothSocket = null;

                try {
                    BluetoothDevice remoteDevice = bluetoothAdapter.getRemoteDevice(deviceAddress);
                    bluetoothSocket = remoteDevice.createRfcommSocketToServiceRecord(uuid);

                    bluetoothSocket.connect();

                    InputStream inputStream = bluetoothSocket.getInputStream();

                    ObjectMapper objectMapper = new ObjectMapper();

                    TypeReference<HashMap<String,Object>> typeRef
                            = new TypeReference<HashMap<String,Object>>() {};

                    Map<String, Object> result = objectMapper.readValue(inputStream, typeRef);

                    handler.onSuccess(result);

                } catch (IOException ex) {
                    handler.onError(ex.getMessage());
                } finally {
                    try {
                        if (bluetoothSocket != null) {
                            bluetoothSocket.close();
                        }
                    } catch (IOException ex) {

                    }
                }

            }
        };

        Thread thread = new Thread(runnable);
        thread.start();
    }

    private Runnable stopServerThread(final Thread thread, final long stopAfter) {
        return new Runnable() {
            public void run() {

                try {
                    Thread.sleep(stopAfter);
                } catch (InterruptedException ex) {
                } finally {
                    Log.d(TAG, "Stopping bluetooth metadata service");
                    thread.stop();
                }

                return;
            }
        };
    }

}