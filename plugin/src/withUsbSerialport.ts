import { ConfigPlugin } from '@expo/config-plugins';

const pkg = require('react-native-usb-serialport-for-android/package.json')
console.log(pkg)

const withUsbSerialPort: ConfigPlugin = (config) => {
  console.log("My custom plugin")
  return config;
};

export default withUsbSerialPort;
