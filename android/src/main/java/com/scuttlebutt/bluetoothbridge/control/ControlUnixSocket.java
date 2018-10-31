package com.scuttlebutt.bluetoothbridge.control;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.util.Log;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scuttlebutt.bluetoothbridge.BluetoothSocketBridgeConfiguration;
import com.scuttlebutt.bluetoothbridge.bluetooth.BluetoothController;
import com.scuttlebutt.bluetoothbridge.bridge.ConnectionBridge;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class ControlUnixSocket {

    private final String controlSocketPath;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BluetoothController bluetoothController;

    private final static String TAG = "bt_control_socket";

    /**
     * The commands to be written to the output stream in a thread safe way.
     */
    private final BlockingQueue<BluetoothControlCommand> commandResponseQueue = new LinkedBlockingQueue();
    private final BlockingQueue<String> awaitingOutgoingConnection;

    private ConnectionBridge connectionBridge = null;

    public ControlUnixSocket(
            BluetoothSocketBridgeConfiguration configuration,
            BluetoothController bluetoothController,
            BlockingQueue<String> awaitingOutgoingConnection) {
        this.controlSocketPath = configuration.getControlSocketPath();
        this.bluetoothController = bluetoothController;

        this.awaitingOutgoingConnection = awaitingOutgoingConnection;

        // Don't close the mapper as we will be reading and writing multiple incoming and outgoing
        // JSON objects
        objectMapper.configure(JsonParser.Feature.AUTO_CLOSE_SOURCE, false);
    }

    /**
     * Start in a new thread.
     */
    public void start() {
        // TODO: handle IO errors? Restart the thread?

        Thread thread = new Thread(controlSocketThread());
        thread.start();
    }

    public void sendConnectedEvent(String remoteAddress, boolean isIncoming) {
        HashMap<String, Object> params = new HashMap<>();
        params.put("remoteAddress", remoteAddress);
        params.put("isIncoming", isIncoming);

        BluetoothControlCommand command = new BluetoothControlCommand("connected", params);

        commandResponseQueue.add(command);
    }

    public void sendConnectionFailureEvent(String remoteAddress, String reason, boolean isIncoming) {
        sendLifeCycleEvent("connectionFailure", remoteAddress, reason, isIncoming);
    }

    public void sendDisconnectionEvent(String remoteAddress, String reason) {
        sendLifeCycleEvent("disconnected", remoteAddress, null, null);
    }

    private void sendLifeCycleEvent(String state, String remoteAddress, String reason, Boolean isIncoming) {
        HashMap<String, Object> params = new HashMap<>();
        params.put("remoteAddress", remoteAddress);
        params.put("reason", reason);
        params.put("isIncoming", isIncoming);

        BluetoothControlCommand command = new BluetoothControlCommand(state, params);

        commandResponseQueue.add(command);
    }

    private Runnable controlSocketThread() {
        return new Runnable() {
            @Override
            public void run() {
                LocalSocket localSocket = establishConnection(10);

                Thread responseWriter =  new Thread(responseWriterThread(localSocket));
                responseWriter.start();

                handleCommands(localSocket);
            }
        };
    }

    private Runnable responseWriterThread(final LocalSocket localSocket) {

        return new Runnable() {
            @Override
            public void run() {
                boolean error = false;
                while (!error) {
                    try {
                        OutputStream outputStream = localSocket.getOutputStream();
                        BluetoothControlCommand commandResponse = commandResponseQueue.take();

                        Log.d(TAG, "Sending response" + commandResponse.getCommand());
                        Log.d(TAG, "Response arguments: " + commandResponse.getArguments());

                        Log.d(TAG, "Attempting to write command to control socket.");
                        byte[] bytes = objectMapper.writeValueAsBytes(commandResponse);
                        outputStream.write(bytes);

                        Log.d(TAG, "Attempting to write double line to control socket.");
                        // For convenience on the other side of the socket using pull-json-doubleline
                        String doubleNewLine = "\n\n";
                        outputStream.write(doubleNewLine.getBytes());

                        Log.d(TAG, "Successfully sent response");
                    } catch (InterruptedException e) {
                        Log.d(TAG, "interrupted exception while writing: " + e.getMessage());

                        e.printStackTrace();
                        error = true;
                    } catch (JsonGenerationException e) {
                        Log.d(TAG, "json generation exception while writing: " + e.getMessage());

                        e.printStackTrace();
                        error = true;
                    } catch (JsonMappingException e) {
                        Log.d(TAG, "json mapping exception while writing: " + e.getMessage());

                        e.printStackTrace();
                        error = true;
                    } catch (IOException e) {
                        e.printStackTrace();

                        Log.d(TAG, "IO exception while writing: " + e.getMessage());
                        error = true;
                    }
                }
            }
        };
    }

    private void handleCommands(LocalSocket socket) {
        try {
            InputStream inputStream = socket.getInputStream();

            while (true) {
                // Each command is sent as a JSON payload, so we continue reading new command
                // objects while the thread is open
                // TODO: more fine grained / well typed deserialization ?

                BluetoothControlCommand bluetoothControlCommand =
                        objectMapper.readValue(inputStream, BluetoothControlCommand.class);

                Log.d(TAG, "Socket is connected? " + socket.isConnected());

                doCommand(bluetoothControlCommand);
            }

        } catch (IOException e) {

            // TODO: reconnect?

            e.printStackTrace();
        }


    }

    private void doCommand(BluetoothControlCommand bluetoothControlCommand) {

        String commandName = bluetoothControlCommand.getCommand();

        Log.d(TAG, "Performing command: " + commandName);
        Log.d(TAG, "Command arguments" + bluetoothControlCommand.getArguments() );


        if (commandName.equals("connect")) {
            String remoteAddress = bluetoothControlCommand.getArgumentAsString("remoteAddress");
            Log.d(TAG, "Connecting to remote address: " + remoteAddress);
            Log.d(TAG, "adding to queue of awaiting connections: " + remoteAddress);

            awaitingOutgoingConnection.add (remoteAddress);
        }
        else if (commandName.equals("discoverDevices")) {
            Log.d(TAG, "Discovering nearby devices");

            DiscoveredDevicesHandler devicesHandler = new DiscoveredDevicesHandler(commandResponseQueue);
            bluetoothController.discoverNearbyDevices(devicesHandler);
        } else if (commandName.equals("makeDiscoverable")) {

            int timeDiscoverable = bluetoothControlCommand.getArgumentAsInt("forTime");

            MakeDeviceDiscoverableHandler responseHandler
                    = new MakeDeviceDiscoverableHandler(commandResponseQueue);

            bluetoothController.makeDeviceDiscoverable(timeDiscoverable, responseHandler);
        } else if (commandName.equals("isEnabled")) {
            Log.d(TAG, "Checking if bluetooth is enabled");

            boolean bluetoothIsEnabled = bluetoothController.isEnabled();

            Map<String, Object> arguments = new HashMap<>();
            arguments.put("enabled", bluetoothIsEnabled);

            BluetoothControlCommand command = new BluetoothControlCommand("isEnabled", arguments);

            commandResponseQueue.add(command);
        }

    }


    private LocalSocket establishConnection(int retries) {

        if (retries < 0) {

            return null;
        }

        Log.d(TAG, "connecting to control socket");

        LocalSocketAddress localSocketAddress = new LocalSocketAddress(this.controlSocketPath,
                LocalSocketAddress.Namespace.FILESYSTEM);

        LocalSocket localSocket = new LocalSocket();

        try {
            localSocket.connect(localSocketAddress);
            Log.d(TAG, "Established connection to control socket.");
        } catch (IOException e) {
            e.printStackTrace();

            Log.d(TAG, "Error establishing connection to control socket: " + e.getMessage() + " retries remaining: " + retries);

            try {
                // Retry after 10 seconds
                Thread.sleep(10000);

                return establishConnection(retries -1);
            } catch (InterruptedException e1) {
                e1.printStackTrace();
            }

        }

        return localSocket;
    }



}
