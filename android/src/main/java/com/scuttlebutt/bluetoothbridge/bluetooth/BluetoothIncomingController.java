package com.scuttlebutt.bluetoothbridge.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.DialogInterface;
import android.util.Log;

import com.scuttlebutt.bluetoothbridge.bridge.ConnectionBridge;
import com.scuttlebutt.bluetoothbridge.dialog.YesNoPrompt;

import java.io.IOException;
import java.util.UUID;

public class BluetoothIncomingController {

    private static String TAG = "listen_incoming";
    private final ConnectionBridge connectionBridge;
    private ServerListenThread mServerListenThread;
    private YesNoPrompt yesNoPrompt;

    public BluetoothIncomingController(ConnectionBridge connectionBridge, YesNoPrompt yesNoPrompt) {
        this.connectionBridge = connectionBridge;
        this.yesNoPrompt = yesNoPrompt;
    }

    /**
     * Creates a server connection to listen for incoming connections.
     * return true if a server was not running and a new server was started, false is a server was already running.
     */
    public synchronized boolean startServerSocket(String serviceName, UUID serviceUUID) {

        BluetoothServerSocket bluetoothServerSocket = null;
        try {
            bluetoothServerSocket = BluetoothAdapter
                    .getDefaultAdapter()
                    .listenUsingRfcommWithServiceRecord(serviceName, serviceUUID);
        } catch (IOException e) {
            // TODO: how to handle this?
            Log.d(TAG, "Could not listen for incoming bluetooth connections");
        }

        // Listen for incoming connections on a new thread and put new entries into the
        // connected devices map
        this.mServerListenThread = new ServerListenThread(bluetoothServerSocket);
        mServerListenThread.start();
        return true;
    }

    /**
     * Stop accepting connections on the server socket.
     *
     * Synchronized for exclusive access to the mServerListenThread object
     *
     * @throws IOException
     */
    public synchronized void stopServerSocket() throws IOException {

        // Close the listen socket;
        mServerListenThread.closeListenSocket();

        // Stop the thread
        mServerListenThread.interrupt();

        mServerListenThread = null;
    }

    /**
     * This thread listens for new incoming
     */
    private class ServerListenThread extends Thread {

        private final BluetoothServerSocket serverSocket;
        private boolean stopped = false;

        ServerListenThread(BluetoothServerSocket serverSocket) {
            Log.d(TAG, "Created server listen thread");

            this.serverSocket = serverSocket;
        }

        @Override
        public void run() {
            while (true) {
                // Block until there is a new incoming connection, then add it to the connected devices
                // then block again until there is a new connection. This loop exits when the thread is
                // stopped and an interrupted exception is thrown
                try {

                    Log.d(TAG, "Awaiting a new incoming connection");

                    final BluetoothSocket newConnection = this.serverSocket.accept();

                    Log.d(TAG, "New connection from: " + newConnection.getRemoteDevice().getAddress());

                    if (newConnection.getRemoteDevice().getBondState() != BluetoothDevice.BOND_BONDED) {
                        DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                switch (which) {
                                    case DialogInterface.BUTTON_POSITIVE:
                                        Log.d(TAG, "Accepted incoming connection from: " + newConnection.getRemoteDevice().getAddress() + " bond state " + newConnection.getRemoteDevice().getBondState());

                                        connectionBridge.createIncomingServerConnection(newConnection);
                                        break;

                                    case DialogInterface.BUTTON_NEGATIVE:
                                        //No button clicked
                                        Log.d(TAG, "User did not accept the incoming connection. Closing socket.");
                                        try {
                                            newConnection.close();
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }
                                        break;
                                }
                            }
                        };

                        String message = "Accept incoming connection from: " + newConnection.getRemoteDevice().getName() + "(" + newConnection.getRemoteDevice().getAddress() + ")";
                        yesNoPrompt.showYesNoDialog(message, dialogClickListener);
                    } else {
                        String address = newConnection.getRemoteDevice().getAddress();
                        Log.d(TAG, "Accepted incoming connection from " + address + " which has pre-existing bond.");

                        connectionBridge.createIncomingServerConnection(newConnection);
                    }


                } catch (IOException e) {
                    Log.d(TAG, "Error while accepting incoming connection: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }

        public void closeListenSocket() throws IOException {
            this.serverSocket.close();
        }
    }
}
