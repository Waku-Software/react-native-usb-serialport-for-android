# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a React Native library that enables USB serial port communication on Android devices. It wraps the Java usb-serial-for-android library and provides a JavaScript API for React Native apps to communicate with USB serial devices. The library is specifically configured for HKA80 devices using CdcAcmSerialDriver.

## Key Commands

### Development
```bash
# Build the module
yarn build

# Type checking
yarn typescript

# Linting
yarn lint

# Run the example app
yarn example android

# Install dependencies for both library and example
yarn bootstrap
```

### Testing & Quality
```bash
# Run tests (currently minimal)
yarn test

# Type checking with TypeScript
yarn typescript

# Lint code
yarn lint
```

### Release
```bash
# Create and publish a new release
yarn release
```

## Architecture

The library has three main layers:

1. **JavaScript API** (`src/`): Exposes `UsbSerialManager` for device management and `UsbSerial` class for port operations. All data is transmitted as hex strings.

2. **Native Bridge** (`android/src/main/java/`): 
   - `UsbSerialportForAndroidModule.java` - Main React Native module handling device enumeration, permissions, and port management
   - `UsbSerialPortWrapper.java` - Manages individual port connections and I/O operations
   - Uses `CdcAcmSerialDriver` specifically for HKA80 devices

3. **Event System**: Uses `NativeEventEmitter` for asynchronous data reception from serial ports.

## Important Technical Details

- **Android Only**: This library only works on Android (platform checks in `src/index.tsx`)
- **Data Format**: All serial data is transmitted as hexadecimal strings
- **Permissions**: Uses `FLAG_IMMUTABLE` for Android permission intents (required for newer Android versions)
- **SDK Versions**: Targets SDK 30, min SDK 16 (configurable in `android/build.gradle`)
- **Hardware Specific**: Modified to use CdcAcmSerialDriver for HKA80 devices

## Common Development Tasks

When modifying the native Android code:
1. Changes go in `android/src/main/java/com/usbserialportforandroid/`
2. After changes, rebuild with `yarn build`
3. Test in example app with `yarn example android`

When modifying the JavaScript API:
1. Main API is in `src/index.tsx` and `src/usb_serial.tsx`
2. Type definitions are in `src/native_module.tsx`
3. Run `yarn typescript` to check types after changes

## Build Configuration

- Android build config: `android/build.gradle`
- TypeScript config: `tsconfig.json` 
- Metro bundler: `metro.config.js` in example app
- Babel transpilation: `babel.config.js`