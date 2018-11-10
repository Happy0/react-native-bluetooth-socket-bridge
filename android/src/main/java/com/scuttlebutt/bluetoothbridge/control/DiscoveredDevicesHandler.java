package com.scuttlebutt.bluetoothbridge.control;

import android.bluetooth.BluetoothDevice;

import com.scuttlebutt.bluetoothbridge.receivers.DiscoveredBluetoothDevicesHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

public class DiscoveredDevicesHandler implements DiscoveredBluetoothDevicesHandler {

    private final BlockingQueue<BluetoothControlCommand> commandResponseBuffer;

    public DiscoveredDevicesHandler(BlockingQueue<BluetoothControlCommand> commandResponseBuffer) {
        this.commandResponseBuffer = commandResponseBuffer;
    }

    public void onDiscovered(List<BluetoothDevice> devices) {

        Map<String, Object> properties = new HashMap<>();

        List<BluetoothDeviceProperties> deviceProperties = new ArrayList<>();

        for (BluetoothDevice bluetoothDevice: devices) {
            BluetoothDeviceProperties deviceProps = getDeviceProperties(bluetoothDevice);
            deviceProperties.add(deviceProps);
        }

        properties.put("devices", deviceProperties);

        BluetoothControlCommand bluetoothControlCommand =
                new BluetoothControlCommand("discovered", properties);

        commandResponseBuffer.add(bluetoothControlCommand);
    }

    @Override
    public void onBluetoothDisabled() {
        Map<String, Object> error = new HashMap<>();
        error.put("error", true);
        error.put("errorCode", "bluetoothDisabled");
        error.put("description", "Bluetooth is not enabled");

        BluetoothControlCommand errorCommand = new BluetoothControlCommand("discovered", error);

        commandResponseBuffer.add(errorCommand);
    }

    @Override
    public void onBluetoothNotSupported() {
        Map<String, Object> error = new HashMap<>();
        error.put("error", true);
        error.put("errorCode", "notSupported");
        error.put("description", "Bluetooth is not supported on this device.");

        BluetoothControlCommand errorCommand = new BluetoothControlCommand("discovered", error);

        commandResponseBuffer.add(errorCommand);
    }

    public BluetoothDeviceProperties getDeviceProperties(BluetoothDevice device) {

        // Unlikely, but since we're using pull-json-doubleline on the other end, a bluetooth device
        // name with new lines in it could make things crash

        String deviceName = device.getName() == null ? "" : device.getName();

        String name = deviceName.replace("\n", "");

        return new BluetoothDeviceProperties(device.getAddress(), name);
    }


}
