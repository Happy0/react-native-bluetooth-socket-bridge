package com.scuttlebutt.bluetoothbridge.control;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

public class MakeDeviceDiscoverableHandler {

    private final BlockingQueue<BluetoothControlCommand> commandResponseQueue;

    private static final String DISCOVERABLE = "discoverable";

    public MakeDeviceDiscoverableHandler(BlockingQueue<BluetoothControlCommand> commandResponseQueue) {
        this.commandResponseQueue = commandResponseQueue;
    }

    public void handleSuccess(long untilEstimate) {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("error", false);
        arguments.put("discoverableUntil", untilEstimate);

        BluetoothControlCommand command = new BluetoothControlCommand(DISCOVERABLE, arguments);
        commandResponseQueue.add(command);
    }

    /**
     * If the app is not at the front, then the user cannot be shown the 'make device discoverable
     * for <n> seconds prompt.
     */
    public void handleAppNotAtFront() {

        Map<String, Object> error = new HashMap<>();
        error.put("error", true);
        error.put("errorCode", "appNotVisible");
        error.put("description", "App is not visible.");

        BluetoothControlCommand command = new BluetoothControlCommand(DISCOVERABLE, error);
        commandResponseQueue.add(command);
    }

    public void handleUserDidNotAllow() {

        Map<String, Object> error = new HashMap<>();
        error.put("error", true);
        error.put("errorCode", "userDidNotAllow");
        error.put("description", "Bluetooth is not enabled");

        BluetoothControlCommand command = new BluetoothControlCommand(DISCOVERABLE, error);
        commandResponseQueue.add(command);
    }

    public void handleAlreadyInProgress() {
        Map<String, Object> error = new HashMap<>();
        error.put("error", true);
        error.put("errorCode", "alreadyAttempting");
        error.put("description", "Already attempting to make device discoverable");

        BluetoothControlCommand command = new BluetoothControlCommand(DISCOVERABLE, error);
        commandResponseQueue.add(command);
    }


}
