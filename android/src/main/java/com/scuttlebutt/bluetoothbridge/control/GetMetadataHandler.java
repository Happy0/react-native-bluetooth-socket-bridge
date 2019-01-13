package com.scuttlebutt.bluetoothbridge.control;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

public class GetMetadataHandler {

    private final BlockingQueue<BluetoothControlCommand> commandResponseQueue;
    private final String requestId;

    private final String commandName = "getMetadata";

    public GetMetadataHandler(
            BlockingQueue<BluetoothControlCommand> commandResponseQueue,
            String requestId) {
        this.commandResponseQueue = commandResponseQueue;
        this.requestId = requestId;
    }

    public void onSuccess(Map<String, Object> metadata) {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("error", false);
        arguments.put("metadata", metadata);

        BluetoothControlCommand command = new BluetoothControlCommand(commandName, arguments, requestId);
        commandResponseQueue.add(command);
    }

    public void onError(String errorDescription) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", true);
        error.put("errorCode", "errorGettingMetadata");
        error.put("description", errorDescription);

        BluetoothControlCommand command = new BluetoothControlCommand(commandName, error, requestId);
        commandResponseQueue.add(command);
    }

}