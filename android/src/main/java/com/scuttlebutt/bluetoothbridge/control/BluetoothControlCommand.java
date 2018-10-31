package com.scuttlebutt.bluetoothbridge.control;

import java.util.Map;

/**
 * TODO: Can we be fancier about deserialization? i.e. deserialize into well typed subclasses
 */
public class BluetoothControlCommand {

    private String command;
    private Map<String, Object> arguments;

    public BluetoothControlCommand() {

    }

    public BluetoothControlCommand(String command, Map<String, Object> arguments) {
        this.command = command;
        this.arguments = arguments;
    }

    public String getCommand() {
        return command;
    }

    public Map<String, Object> getArguments() {
        return arguments;
    }

    public String getArgumentAsString(String key) {
        return String.valueOf(arguments.get(key));
    }

    public int getArgumentAsInt(String key) {
        String argumentAsString = getArgumentAsString(key);

        // todo: handle exception
        return Integer.parseInt(argumentAsString);
    }
}
