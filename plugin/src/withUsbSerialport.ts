import type { ConfigPlugin } from '@expo/config-plugins';

const withUsbSerialPort: ConfigPlugin = (config) => {
  console.log('My custom plugin');
  return config;
};

export default withUsbSerialPort;
