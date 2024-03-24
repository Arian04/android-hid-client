package me.arianb.usb_hid_client;

import static me.arianb.usb_hid_client.CharacterDevice.KEYBOARD_DEVICE_PATH;
import static me.arianb.usb_hid_client.CharacterDevice.MOUSE_DEVICE_PATH;

import android.util.Log;
import android.view.View;

import com.google.android.material.snackbar.Snackbar;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import timber.log.Timber;

public class KeySender implements Runnable {
    // Constants that'll be passed in addKey to mark what type of key it is so I know which method
    // to use to send it
    public static final byte STANDARD_KEY = 0x01;
    public static final byte MEDIA_KEY = 0x02;

    private static Queue<Byte> modQueue;
    private static Queue<Byte> keyQueue;
    private static Queue<Byte> keyTypeQueue;

    private static final ReentrantLock queueLock = new ReentrantLock(true);
    private static final Condition queueNotEmptyCondition = queueLock.newCondition();

    private final View parentLayout;

    public KeySender(View parentLayout) {
        this.parentLayout = parentLayout;
        modQueue = new LinkedList<>();
        keyQueue = new LinkedList<>();
        keyTypeQueue = new LinkedList<>();
    }

    @Override
    public void run() {
        Timber.d("keySender thread started");
        while (!Thread.interrupted()) {
            queueLock.lock();
            // Wait for the queue(s) to actually contain keys
            if (keyQueue.isEmpty()) {
                //Timber.d("Waiting for queue to not be empty.");
                try {
                    queueNotEmptyCondition.await();
                } catch (InterruptedException e) {
                    Timber.e(Log.getStackTraceString(e));
                    queueLock.unlock();
                }
            }
            sendKey(modQueue.remove(), keyQueue.remove(), keyTypeQueue.remove());
            //Timber.d("sending key");
            queueLock.unlock();
        }
    }

    public void addKey(byte modifier, byte key, byte keyType) {
        //Timber.d("trying to lock");
        queueLock.lock();
        modQueue.add(modifier);
        keyQueue.add(key);
        keyTypeQueue.add(keyType);
        queueNotEmptyCondition.signal();
        queueLock.unlock();
        //Timber.d("unlocked");
    }

    public void sendKey(byte modifier, byte key, byte keyType) {
        switch (keyType) {
            case STANDARD_KEY:
                writeKeyHIDReport(modifier, key); // Send key
                writeKeyHIDReport((byte) 0, (byte) 0); // Release key
                break;
            case MEDIA_KEY:
                writeMediaHIDReport(key); // Send Key
                writeMediaHIDReport((byte) 0); // Release key
        }
    }

    // Writes HID reports for standard keys (a,b,%,@, etc.)
    private void writeKeyHIDReport(byte modifier, byte key) {
        //Timber.d("hid report: %s - %s", modifier, key);

        byte[] report = new byte[]{(byte) 0x01, modifier, (byte) 0, key, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0};
        writeHIDReport(KEYBOARD_DEVICE_PATH, report);
    }

    // Writes HID reports for media keys (play-pause,volume-up,volume-down, etc.)
    public void writeMediaHIDReport(byte key) {
        byte[] report = new byte[]{(byte) 0x02, key, (byte) 0x00};
        writeHIDReport(KEYBOARD_DEVICE_PATH, report);
    }

    // Writes HID report to character device
    private void writeHIDReport(String device, byte[] report) {
        // TODO generalize this to make it work with other character devices
        // Check if character device exists
        if (CharacterDevice.characterDeviceMissing(KEYBOARD_DEVICE_PATH)) {
            Timber.e("ERROR: Character device doesn't exist");
            makeCreateKeyboardCharDeviceSnackbar();
            return;
        }

        // Write HID report
        try (FileOutputStream outputStream = new FileOutputStream(device)) {
            outputStream.write(report);
        } catch (IOException e) {
            String stacktrace = Log.getStackTraceString(e);
            if (stacktrace.toLowerCase().contains("errno 108")) {
                makeSnackbar("ERROR: Your device seems to be disconnected. If not, try reseating the usb cable", Snackbar.LENGTH_LONG);
            } else if (stacktrace.toLowerCase().contains("permission denied")) {
                if (device.equals(CharacterDevice.KEYBOARD_DEVICE_PATH)) {
                    makeFixKeyboardPermissionsSnackbar();
                } else if (device.equals(CharacterDevice.MOUSE_DEVICE_PATH)) {
                    makeFixMousePermissionsSnackbar();
                } else {
                    Timber.e("ERROR: permission denied and writeHIDReport called with invalid device path");
                }
            } else {
                makeSnackbar("ERROR: Failed to send key.", Snackbar.LENGTH_SHORT);
            }
            Timber.e(stacktrace);
        }
    }

    public void makeSnackbar(String message, int length) {
        Snackbar.make(parentLayout, message, length).show();
    }

    public void makeCreateKeyboardCharDeviceSnackbar() {
        Snackbar snackbar = Snackbar.make(parentLayout, "ERROR: Character device doesn't exist.", Snackbar.LENGTH_INDEFINITE);
        snackbar.setAction("FIX", v -> MainActivity.characterDevice.createCharacterDevice());
        snackbar.show();
    }

    public void makeFixKeyboardPermissionsSnackbar() {
        Snackbar snackbar = Snackbar.make(parentLayout, "ERROR: Character device permissions seem incorrect.", Snackbar.LENGTH_INDEFINITE);
        snackbar.setAction("FIX", v -> MainActivity.characterDevice.fixCharacterDevicePermissions(KEYBOARD_DEVICE_PATH));
        snackbar.show();
    }

    public void makeFixMousePermissionsSnackbar() {
        Snackbar snackbar = Snackbar.make(parentLayout, "ERROR: Character device permissions seem incorrect.", Snackbar.LENGTH_INDEFINITE);
        snackbar.setAction("FIX", v -> MainActivity.characterDevice.fixCharacterDevicePermissions(MOUSE_DEVICE_PATH));
        snackbar.show();
    }
}