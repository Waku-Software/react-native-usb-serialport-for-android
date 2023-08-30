const { withUsb } = require('@expo/config-plugins');
import { ConfigPlugin, createRunOncePlugin } from '@expo/config-plugins';

const pkg = require('react-native-usb-serialport-for-android/package.json')

const withUsbSerialPort: ConfigPlugin = (config) => {
  return withUsb(config);
};

export default createRunOncePlugin(withUsbSerialPort, pkg.name, pkg.version);
