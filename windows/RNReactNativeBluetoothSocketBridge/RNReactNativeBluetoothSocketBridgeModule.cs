using ReactNative.Bridge;
using System;
using System.Collections.Generic;
using Windows.ApplicationModel.Core;
using Windows.UI.Core;

namespace React.Native.Bluetooth.Socket.Bridge.RNReactNativeBluetoothSocketBridge
{
    /// <summary>
    /// A module that allows JS to share data.
    /// </summary>
    class RNReactNativeBluetoothSocketBridgeModule : NativeModuleBase
    {
        /// <summary>
        /// Instantiates the <see cref="RNReactNativeBluetoothSocketBridgeModule"/>.
        /// </summary>
        internal RNReactNativeBluetoothSocketBridgeModule()
        {

        }

        /// <summary>
        /// The name of the native module.
        /// </summary>
        public override string Name
        {
            get
            {
                return "RNReactNativeBluetoothSocketBridge";
            }
        }
    }
}
