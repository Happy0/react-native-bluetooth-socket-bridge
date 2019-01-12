package com.scuttlebutt.bluetoothbridge.control;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

public class StartMetadataServiceHandler {

    private final BlockingQueue<BluetoothControlCommand> commandResponseQueue;

    private final String metadataService = "metadataService";


    public StartMetadataServiceHandler(BlockingQueue<BluetoothControlCommand> commandResponseQueue) {
        this.commandResponseQueue = commandResponseQueue;
    }

    public void onSuccess(long untilEstimate) {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("error", false);
        arguments.put("availableUntil", untilEstimate);

        BluetoothControlCommand command = new BluetoothControlCommand(metadataService, arguments);
        commandResponseQueue.add(command);
    }

    public void onError(String errorDescription) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", true);
        error.put("errorCode", "errorStarting");
        error.put("description", errorDescription);

        BluetoothControlCommand command = new BluetoothControlCommand(metadataService, error);
        commandResponseQueue.add(command);
    }

}