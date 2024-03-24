package me.arianb.usb_hid_client;

import static me.arianb.usb_hid_client.hid_utils.CharacterDevice.KEYBOARD_DEVICE_PATH;

import android.util.Log;
import android.view.View;

import com.google.android.material.snackbar.Snackbar;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import me.arianb.usb_hid_client.hid_utils.CharacterDevice;
import timber.log.Timber;

public class KeySender implements Runnable {
    // Constants that'll be passed in addKey to mark what type of key it is so I know which method
    // to use to send it. They are also the respective report IDs.
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
                // Send key
                writeHIDReport(KEYBOARD_DEVICE_PATH, new byte[]{STANDARD_KEY, modifier, 0, key, 0});

                // Release
                writeHIDReport(KEYBOARD_DEVICE_PATH, new byte[]{STANDARD_KEY, 0, 0, 0, 0});

                break;
            case MEDIA_KEY:
                writeHIDReport(KEYBOARD_DEVICE_PATH, new byte[]{MEDIA_KEY, key, 0}); // Send Key
                writeHIDReport(KEYBOARD_DEVICE_PATH, new byte[]{MEDIA_KEY, 0, 0}); // Release key
        }
    }

    // Writes HID report to character device
    private void writeHIDReport(String device, byte[] report) {
        // Check if character device exists
        if (CharacterDevice.characterDeviceMissing(device)) {
            Timber.e("ERROR: Character device doesn't exist");
            makeCreateCharDeviceSnackbar();
            return;
        }

        // Write HID report
        try (FileOutputStream outputStream = new FileOutputStream(device)) {
            outputStream.write(report);
        } catch (IOException e) {
            String stacktrace = Log.getStackTraceString(e);
            if (stacktrace.toLowerCase().contains("errno 108")) {
                makeSnackbar("ERROR: Your device seems to be disconnected. If not, try reseating the USB cable", Snackbar.LENGTH_LONG);
            } else if (stacktrace.toLowerCase().contains("permission denied")) {
                makeFixCharDevicePermissionsSnackbar(device);
            } else {
                makeSnackbar("ERROR: Failed to send key.", Snackbar.LENGTH_SHORT);
            }
            Timber.e(stacktrace);
        }
    }

    public void makeSnackbar(String message, int length) {
        Snackbar.make(parentLayout, message, length).show();
    }

    public void makeCreateCharDeviceSnackbar() {
        Snackbar snackbar = Snackbar.make(parentLayout, "ERROR: Character device doesn't exist.", Snackbar.LENGTH_INDEFINITE);
        snackbar.setAction("FIX", v -> MainActivity.characterDevice.createCharacterDevice());
        snackbar.show();
    }

    public void makeFixCharDevicePermissionsSnackbar(String devicePath) {
        Snackbar snackbar = Snackbar.make(parentLayout, "ERROR: Character device permissions seem incorrect.", Snackbar.LENGTH_INDEFINITE);
        snackbar.setAction("FIX", v -> MainActivity.characterDevice.fixCharacterDevicePermissions(devicePath));
        snackbar.show();
    }
}