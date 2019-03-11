package com.scuttlebutt.bluetoothbridge.bridge;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.util.Log;

import org.apache.commons.io.IOUtils;

import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A class for proxying incoming and outgoing bluetooth connections to another process / thread via
 * local unix sockets. This is for use cases where you have a thread / process, perhaps in another language,
 * that you want to leverage bluetooth functionality in.
 */
public class ConnectionBridge {


    private final String socketOutgoingPath;
    private final String socketIncomingPath;
    private final ConnectionStatusNotifier connectionStatusNotifier;
    private final BluetoothAdapter bluetoothAdapter;
    private final UUID serviceUUID;

    private static final String TAG = "bluetooth_bridge";

    private Map<String, BluetoothSocket> connectedDevices = new ConcurrentHashMap<>();

    private final BlockingQueue<String> awaitingOutgoingConnection;

    public ConnectionBridge(
                            BlockingQueue<String> awaitingOutgoingConnection,
                            String socketOutgoingPath,
                            String socketIncomingPath,
                            UUID serviceUUID,
                            ConnectionStatusNotifier notifier,
                            BluetoothAdapter bluetoothAdapter) {

        this.awaitingOutgoingConnection = awaitingOutgoingConnection;
        this.socketOutgoingPath = socketOutgoingPath;
        this.socketIncomingPath = socketIncomingPath;
        this.serviceUUID = serviceUUID;
        this.connectionStatusNotifier = notifier;
        this.bluetoothAdapter = bluetoothAdapter;
    }

    public void createIncomingServerConnection(final BluetoothSocket bluetoothSocket) {

        LocalSocket localSocket = new LocalSocket();
        LocalSocketAddress localSocketAddress = new LocalSocketAddress(
                this.socketIncomingPath,
                LocalSocketAddress.Namespace.FILESYSTEM
        );

        try {
            localSocket.connect(localSocketAddress);

            String remoteAddress = bluetoothSocket.getRemoteDevice().getAddress();
            if (connectedDevices.containsKey(remoteAddress)) {
                Log.d(TAG, "Stopping incoming connection from " + remoteAddress + " as we're already connected.");
                connectionStatusNotifier.onConnectionFailure(remoteAddress, "Already connected.", true);

                close(localSocket);
            } else {
                Runnable reader = readFromBluetoothAndSendToSocket(localSocket, bluetoothSocket);
                Runnable writer = readFromSocketAndSendToBluetooth(localSocket, bluetoothSocket);

                connectionStatusNotifier.onConnectionSuccess(remoteAddress, true);
                connectedDevices.put(remoteAddress, bluetoothSocket);

                Thread thread = new Thread(reader);
                Thread thread2 = new Thread(writer);

                thread.start();
                thread2.start();
            }

        } catch (IOException e) {
            Log.d(TAG, "IO err on connection to socket for incoming connection: " + e.getMessage());

            connectionStatusNotifier.onConnectionFailure(
                    bluetoothSocket.getRemoteDevice().getAddress(),
                    e.getMessage(),
                    true
            );
        }

    }

    public void listenForOutgoingConnections() {

        Log.d(TAG, "Outgoing connections thread. Sock path: " + this.socketOutgoingPath);

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {

                while (true) {
                    try {
                        String address = awaitingOutgoingConnection.take();
                        Log.d(TAG, "Dequeue awaiting connection: " + address);

                        if (!bluetoothAdapter.isEnabled()) {
                            connectionStatusNotifier.onConnectionFailure(address, "Bluetooth is not enabled", false);
                            continue;
                        }

                            Log.d(TAG, "Opening unix socket connection to proxy the bluetooth connection.");

                        LocalSocket localSocket = new LocalSocket();
                        LocalSocketAddress localSocketAddress = new LocalSocketAddress(
                                socketOutgoingPath, LocalSocketAddress.Namespace.FILESYSTEM
                        );

                        try {
                            localSocket.connect(localSocketAddress);
                        } catch (IOException e) {
                            Log.d(TAG, "Could not connect to unix socket to proxy bluetooth connection");
                            e.printStackTrace();
                            connectionStatusNotifier.onConnectionFailure(address, e.getMessage(), false);

                            // Go to the top to await the next connection
                            continue;
                        }

                        Log.d(TAG, "Attempting bluetooth connection to " + address);

                        if (connectedDevices.containsKey(address)) {
                            Log.d(TAG, "Stopping incoming connection from " + address + " as we're already connected.");

                            close(localSocket);
                            connectionStatusNotifier.onConnectionFailure(address, "Already connected.", false);

                            // Go to the top to await the next connection
                            continue;
                        }

                        BluetoothDevice remoteDevice = bluetoothAdapter.getRemoteDevice(address);

                        try {
                            BluetoothSocket bluetoothSocket =
                                    remoteDevice.createRfcommSocketToServiceRecord(serviceUUID);

                            bluetoothSocket.connect();

                            Log.d(TAG, "Connection successful to " + address);

                            connectedDevices.put(address, bluetoothSocket);
                            connectionStatusNotifier.onConnectionSuccess(address, false);

                            Runnable reader = readFromSocketAndSendToBluetooth(localSocket, bluetoothSocket);
                            Runnable writer = readFromBluetoothAndSendToSocket(localSocket, bluetoothSocket);
                            
                            Thread readerThread = new Thread(reader);
                            Thread writerThread = new Thread(writer);

                            readerThread.start();
                            writerThread.start();

                            Log.d(TAG, "Started reader and writer threads");
                        } catch (Exception ex) {
                            Log.d(TAG, "Exception while connecting to " + address + ": " + ex.getMessage());
                            connectionStatusNotifier.onConnectionFailure(address, ex.getMessage(), false);
                            close(localSocket);
                        }

                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

            }
        });

        thread.start();
    }

    public void closeAllOpenConnections() {
        for (String address: connectedDevices.keySet()) {
            BluetoothSocket bluetoothSocket = connectedDevices.get(address);
            close(bluetoothSocket);
        }
    }


    private Runnable readFromSocketAndSendToBluetooth(final LocalSocket localSocket,
                                                      final BluetoothSocket bluetoothSocket)
    {

        return new Runnable() {
            @Override
            public void run() {
                copyStream(localSocket, bluetoothSocket, true);
            }
        };

    }

    private Runnable readFromBluetoothAndSendToSocket(final LocalSocket localSocket, final BluetoothSocket bluetoothSocket) {

        return new Runnable() {
            @Override
            public void run() {
                copyStream(localSocket, bluetoothSocket, false);

                String remoteAddress = bluetoothSocket.getRemoteDevice().getAddress();
                connectedDevices.remove(remoteAddress);

                connectionStatusNotifier.onDisconnect(remoteAddress, "Connection lost.");
            }
        };

    }

    private void copyStream(LocalSocket localSocket, BluetoothSocket bluetoothSocket, boolean socketToBluetooth) {

        FileDescriptor socketFd = localSocket.getFileDescriptor();

        Log.d(TAG, "Local socket connection fd: " + socketFd.toString());

        try {
            // TODO: measure throughput / investigate whether intermediate buffering or more concurrency
            // could improve this. I'm curious...
            if (socketToBluetooth) {
                IOUtils.copyLarge(localSocket.getInputStream(), bluetoothSocket.getOutputStream());
            } else {
                IOUtils.copyLarge(bluetoothSocket.getInputStream(), localSocket.getOutputStream());
            }

        } catch (IOException e) {
            Log.d(TAG, "IO err " + e.getMessage());
            Log.d(TAG, "Socket fd: " + socketFd.toString());
        } finally {
            close(bluetoothSocket);
            close(localSocket);
        }

    }

    private void close(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
