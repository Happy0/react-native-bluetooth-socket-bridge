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

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * A service that just sends connecting clients the same payload every time, and then
 * disconnects when it has finished sending.
 */
public class BluetoothMetadataService {

    private static String TAG = "bluetooth_metadata_service";

    private final BluetoothAdapter bluetoothAdapter;

    private ThreadPoolExecutor threadPoolExecutor;

    public BluetoothMetadataService(BluetoothAdapter bluetoothAdapter) {
        this.bluetoothAdapter = bluetoothAdapter;

        BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<Runnable>();
        this.threadPoolExecutor = new ThreadPoolExecutor(4, 10, 60, TimeUnit.SECONDS, workQueue);
        this.threadPoolExecutor.allowCoreThreadTimeOut(true);
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
                .listenUsingInsecureRfcommWithServiceRecord(serviceName, uuid);

        Runnable runnable = new Runnable() {

            @Override
            public void run() {

                try {
                    while (true) {

                        if (!bluetoothAdapter.isEnabled()) {
                            Log.d(TAG, "Bluetooth is not enabled. Not starting metadata service.");
                            break;
                        }

                        try {

                            BluetoothSocket socket = null;
                            try {
                                socket = bluetoothServerSocket.accept();
                                Log.d(TAG, "Accepted incoming connection to bluetooth metadata service.");
                            } catch (IOException ex) {
                                Log.d(TAG, "Metadata service thread interrupted. Closing. ");
                                break;
                            }

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

                    }

                }

                Log.d(TAG, "Leaving metadata service server run block...");
            }

        };

        Thread thread = new Thread(runnable);
        thread.start();

        Thread stopServerThread = new Thread(stopServerThread(bluetoothServerSocket, timeSeconds * 1000));
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
                    bluetoothSocket = remoteDevice.createInsecureRfcommSocketToServiceRecord(uuid);

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

        threadPoolExecutor.execute(runnable);


    }

    private Runnable stopServerThread(final BluetoothServerSocket serverSocket, final long stopAfter) {
        return new Runnable() {
            public void run() {

                try {
                    Thread.sleep(stopAfter);
                } catch (InterruptedException ex) {
                } finally {
                    Log.d(TAG, "Stopping bluetooth metadata service");

                    try {
                        serverSocket.close();
                    } catch (IOException ex) {
                        Log.d(TAG, "IOException while trying to close metadata server socket: " + ex.getMessage());
                    }

                }

                return;
            }
        };
    }

}