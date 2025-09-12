import UsbSerialportForAndroid from './native_module';
const DataReceivedEvent = 'usbSerialPortDataReceived';
export default class UsbSerial {
    deviceId;
    eventEmitter;
    listeners;
    subscriptions;
    constructor(deviceId, eventEmitter) {
        this.deviceId = deviceId;
        this.eventEmitter = eventEmitter;
        this.listeners = [];
        this.subscriptions = [];
    }
    /**
     * Send data with hex string.
     *
     * May return error with these codes:
     * * DEVICE_NOT_OPEN
     * * SEND_FAILED
     *
     * See {@link Codes}
     * @param hexStr
     * @returns
     */
    send(hexStr) {
        return UsbSerialportForAndroid.send(this.deviceId, hexStr);
    }
    /**
     * Listen to data received event.
     *
     * @param listener
     * @returns EventSubscription
     */
    onReceived(listener) {
        const listenerProxy = (event) => {
            if (event.deviceId !== this.deviceId) {
                return;
            }
            if (!event.data) {
                return;
            }
            listener(event);
        };
        this.listeners.push(listenerProxy);
        const sub = this.eventEmitter.addListener(DataReceivedEvent, listenerProxy);
        this.subscriptions.push(sub);
        return sub;
    }
    /**
     *
     * May return error with these codes:
     * * DEVICE_NOT_OPEN_OR_CLOSED
     *
     * See {@link Codes}
     * @returns Promise<null>
     */
    close() {
        for (const sub of this.subscriptions) {
            sub.remove();
        }
        return UsbSerialportForAndroid.close(this.deviceId);
    }
}
