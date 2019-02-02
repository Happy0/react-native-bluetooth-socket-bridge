package com.scuttlebutt.bluetoothbridge.control;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import com.scuttlebutt.bluetoothbridge.receivers.BluetoothEnablednessHandler;

public class BluetoothStateUpdateHandler implements BluetoothEnablednessHandler {

    private final BlockingQueue<BluetoothControlCommand> commandResponseQueue;

    public BluetoothStateUpdateHandler(
            BlockingQueue<BluetoothControlCommand> commandResponseQueue
    ) {
        this.commandResponseQueue = commandResponseQueue;
    }

    public void onEnabled() {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("error", false);
        arguments.put("isEnabled", true);

        BluetoothControlCommand command = new BluetoothControlCommand(
                "bluetoothState", arguments);

        commandResponseQueue.add(command);
    }

    public void onDisabled() {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("error", false);
        arguments.put("isEnabled", false);

        BluetoothControlCommand command = new BluetoothControlCommand(
                "bluetoothState", arguments);

        commandResponseQueue.add(command);
    }

}