
package com.scuttlebutt.bluetoothbridge;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.facebook.react.ReactPackage;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.uimanager.ViewManager;
import com.facebook.react.bridge.JavaScriptModule;

public class BluetoothSocketBridgePackage implements ReactPackage {

    private final BluetoothSocketBridgeConfiguration configuration;

    public BluetoothSocketBridgePackage(
            BluetoothSocketBridgeConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public List<NativeModule> createNativeModules(
            ReactApplicationContext reactContext) {
      return Arrays.<NativeModule>asList(new BluetoothSocketBridgeModule(reactContext, configuration));
    }

    // Deprecated from RN 0.47
    public List<Class<? extends JavaScriptModule>> createJSModules() {
      return Collections.emptyList();
    }

    @Override
    public List<ViewManager> createViewManagers(ReactApplicationContext reactContext) {
      return Collections.emptyList();
    }
}