package com.bastengao.usbserialport;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.util.Log;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.module.annotations.ReactModule;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@ReactModule(name = UsbSerialportForAndroidModule.NAME)
public class UsbSerialportForAndroidModule extends ReactContextBaseJavaModule implements EventSender {
    public static final String NAME = "UsbSerialportForAndroid";
    private static final String INTENT_ACTION_GRANT_USB = BuildConfig.LIBRARY_PACKAGE_NAME + ".GRANT_USB";

    public static final String CODE_DEVICE_NOT_FOND = "device_not_found";
    public static final String CODE_DRIVER_NOT_FOND = "driver_not_found";
    public static final String CODE_NOT_ENOUGH_PORTS = "not_enough_ports";
    public static final String CODE_PERMISSION_DENIED = "permission_denied";
    public static final String CODE_OPEN_FAILED = "open_failed";
    public static final String CODE_DEVICE_NOT_OPEN = "device_not_open";
    public static final String CODE_SEND_FAILED = "send_failed";
    public static final String CODE_DEVICE_NOT_OPEN_OR_CLOSED = "device_not_open_or_closed";

    private final ReactApplicationContext reactContext;
    private final Map<Integer, UsbSerialPortWrapper> usbSerialPorts = new HashMap<Integer, UsbSerialPortWrapper>();

    public UsbSerialportForAndroidModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
    }

    @Override
    @NonNull
    public String getName() {
        return NAME;
    }

    @Override
    public Map<String, Object> getConstants() {
        final Map<String, Object> constants = new HashMap<>();
        constants.put("CODE_DEVICE_NOT_FOND", CODE_DEVICE_NOT_FOND);
        constants.put("CODE_DRIVER_NOT_FOND", CODE_DRIVER_NOT_FOND);
        constants.put("CODE_NOT_ENOUGH_PORTS", CODE_NOT_ENOUGH_PORTS);
        constants.put("CODE_PERMISSION_DENIED", CODE_PERMISSION_DENIED);
        constants.put("CODE_OPEN_FAILED", CODE_OPEN_FAILED);
        constants.put("CODE_DEVICE_NOT_OPEN", CODE_DEVICE_NOT_OPEN);
        constants.put("CODE_SEND_FAILED", CODE_SEND_FAILED);
        constants.put("CODE_DEVICE_NOT_OPEN_OR_CLOSED", CODE_DEVICE_NOT_OPEN_OR_CLOSED);
        return constants;
    }

    @ReactMethod
    public void list(Promise promise) {
        WritableArray devices = Arguments.createArray();
        UsbManager usbManager = (UsbManager) reactContext.getSystemService(Context.USB_SERVICE);
        if (usbManager == null) {
            promise.resolve(devices);
            return;
        }
        for (UsbDevice device : usbManager.getDeviceList().values()) {
            WritableMap d = Arguments.createMap();
            d.putInt("deviceId", device.getDeviceId());
            d.putInt("vendorId", device.getVendorId());
            d.putInt("productId", device.getProductId());
            devices.pushMap(d);
        }
        promise.resolve(devices);
    }

    @ReactMethod
    public void tryRequestPermission(int deviceId, Promise promise) {
        UsbManager usbManager = (UsbManager) reactContext.getSystemService(Context.USB_SERVICE);
        if (usbManager == null) {
            promise.reject(CODE_OPEN_FAILED, "UsbManager is null");
            return;
        }
        UsbDevice device = findDevice(deviceId);
        if (device == null) {
            promise.reject(CODE_DEVICE_NOT_FOND, "device not found");
            return;
        }

        if (usbManager.hasPermission(device)) {
            // Ya tiene permiso
            promise.resolve(1);
            return;
        }

        // Registrar receiver para esperar la respuesta del sistema
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent != null && INTENT_ACTION_GRANT_USB.equals(intent.getAction())) {
                    boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                    try {
                        reactContext.unregisterReceiver(this);
                    } catch (IllegalArgumentException ignored) {}
                    if (granted) {
                        promise.resolve(1);
                    } else {
                        promise.reject(CODE_PERMISSION_DENIED, "permission denied by user");
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter(INTENT_ACTION_GRANT_USB);
        reactContext.registerReceiver(receiver, filter);

        // PendingIntent con FLAG_IMMUTABLE (requerido Android 12+)
        int flags = PendingIntent.FLAG_IMMUTABLE;
        PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(
                reactContext,
                0,
                new Intent(INTENT_ACTION_GRANT_USB),
                flags
        );

        // Mostrar diálogo de permiso
        usbManager.requestPermission(device, usbPermissionIntent);
        // NOTE: NO resolvemos el promise aquí; se resuelve en el receiver.
    }

    @ReactMethod
    public void hasPermission(int deviceId, Promise promise) {
        UsbManager usbManager = (UsbManager) reactContext.getSystemService(Context.USB_SERVICE);
        if (usbManager == null) {
            promise.reject(CODE_OPEN_FAILED, "UsbManager is null");
            return;
        }
        UsbDevice device = findDevice(deviceId);
        if (device == null) {
            promise.reject(CODE_DEVICE_NOT_FOND, "device not found");
            return;
        }
        promise.resolve(usbManager.hasPermission(device));
    }

    @ReactMethod
    public void open(int deviceId, int baudRate, int dataBits, int stopBits, int parity, Promise promise) {
        UsbSerialPortWrapper wrapper = usbSerialPorts.get(deviceId);
        if (wrapper != null) {
            promise.resolve(deviceId);
            return;
        }

        UsbManager usbManager = (UsbManager) reactContext.getSystemService(Context.USB_SERVICE);
        if (usbManager == null) {
            promise.reject(CODE_OPEN_FAILED, "UsbManager is null");
            return;
        }
        UsbDevice device = findDevice(deviceId);
        if (device == null) {
            promise.reject(CODE_DEVICE_NOT_FOND, "device not found");
            return;
        }

        // UsbSerialDriver driver = UsbSerialProber.getDefaultProber().probeDevice(device);
        UsbSerialDriver driver = new CdcAcmSerialDriver(device); // driver específico, ajusta si corresponde
        if (driver == null) {
            promise.reject(CODE_DRIVER_NOT_FOND, "no driver for device");
            return;
        }
        if (driver.getPorts().size() < 1) { // fix: era < 0
            promise.reject(CODE_NOT_ENOUGH_PORTS, "not enough ports at device");
            return;
        }
        Log.i("DEBUG", "El driver se generó exitosamente");

        if (!usbManager.hasPermission(device)) {
            promise.reject(CODE_PERMISSION_DENIED, "connection failed: permission denied");
            return;
        }

        UsbDeviceConnection connection = usbManager.openDevice(device);
        if (connection == null) {
            promise.reject(CODE_OPEN_FAILED, "connection failed: open failed");
            return;
        }
        Log.i("DEBUG", "Se conectó exitosamente!");

        UsbSerialPort port = driver.getPorts().get(0);
        try {
            port.open(connection);
            port.setParameters(baudRate, dataBits, stopBits, parity);
        } catch (IOException e) {
            try { port.close(); } catch (IOException ignored) {}
            promise.reject(CODE_OPEN_FAILED, "connection failed", e);
            return;
        }

        wrapper = new UsbSerialPortWrapper(deviceId, port, this);
        usbSerialPorts.put(deviceId, wrapper);
        Log.i("DEBUG", "open se ejecutó exitosamente");
        promise.resolve(deviceId);
    }

    @ReactMethod
    public void send(int deviceId, String hexStr, Promise promise) {
        UsbSerialPortWrapper wrapper = usbSerialPorts.get(deviceId);
        if (wrapper == null) {
            promise.reject(CODE_DEVICE_NOT_OPEN, "device not open");
            return;
        }

        byte[] data = hexStringToByteArray(hexStr);
        try {
            wrapper.send(data);
            promise.resolve(null);
        } catch (IOException e) {
            promise.reject(CODE_SEND_FAILED, "send failed", e);
        }
    }

    @ReactMethod
    public void close(int deviceId, Promise promise) {
        UsbSerialPortWrapper wrapper = usbSerialPorts.get(deviceId);
        if (wrapper == null) {
            promise.reject(CODE_DEVICE_NOT_OPEN_OR_CLOSED, "serial port not open or closed");
            return;
        }

        wrapper.close();
        usbSerialPorts.remove(deviceId);
        promise.resolve(null);
    }

    public void sendEvent(final String eventName, final WritableMap event) {
        reactContext.runOnUiQueueThread(new Runnable() {
            @Override
            public void run() {
                reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                        .emit(eventName, event);
            }
        });
    }

    private UsbDevice findDevice(int deviceId) {
        UsbManager usbManager = (UsbManager) reactContext.getSystemService(Context.USB_SERVICE);
        if (usbManager == null) return null;
        for (UsbDevice device : usbManager.getDeviceList().values()) {
            if (device.getDeviceId() == deviceId) {
                return device;
            }
        }
        return null;
    }

    public static byte[] hexStringToByteArray(String s) {
        if (s.length() % 2 == 1) {
            throw new IllegalArgumentException("Invalid hexadecimal String supplied.");
        }
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }
}