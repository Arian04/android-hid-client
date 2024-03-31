package me.arianb.usb_hid_client.report_senders;

import static me.arianb.usb_hid_client.hid_utils.CharacterDevice.MOUSE_DEVICE_PATH;

import android.util.Log;
import android.view.View;

import com.google.android.material.snackbar.Snackbar;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import me.arianb.usb_hid_client.MainActivity;
import me.arianb.usb_hid_client.hid_utils.CharacterDevice;
import me.arianb.usb_hid_client.shell_utils.NoRootPermissionsException;
import timber.log.Timber;

public class MouseSender implements Runnable {
    public final static byte MOUSE_BUTTON_NONE = 0;
    public final static byte MOUSE_BUTTON_LEFT = 1;
    public final static byte MOUSE_BUTTON_RIGHT = 2;
    public final static byte MOUSE_BUTTON_MIDDLE = 3;

    private static Queue<byte[]> reportQueue;

    private static final ReentrantLock queueLock = new ReentrantLock(true);
    private static final Condition queueNotEmptyCondition = queueLock.newCondition();

    private final View parentLayout;

    public MouseSender(View parentLayout) {
        this.parentLayout = parentLayout;
        reportQueue = new LinkedList<>();
    }

    @Override
    public void run() {
        Timber.d("MouseSender thread started");
        while (!Thread.interrupted()) {
            queueLock.lock();
            // Wait for the queue(s) to actually contain mouse events
            if (reportQueue.isEmpty()) {
                //Timber.d("Waiting for queue to not be empty.");
                try {
                    queueNotEmptyCondition.await();
                } catch (InterruptedException e) {
                    Timber.e(Log.getStackTraceString(e));
                    queueLock.unlock();
                }
            }
            sendReport(reportQueue.remove());
            queueLock.unlock();
        }
    }

    public void addReport(byte button, byte x, byte y) {
        //Timber.d("trying to lock");
        queueLock.lock();

        reportQueue.add(new byte[]{button, x, y});

        queueNotEmptyCondition.signal();
        queueLock.unlock();
        //Timber.d("unlocked");
    }

    public void sendReport(byte[] report) {
        writeHIDReport(MOUSE_DEVICE_PATH, report); // Send
        writeHIDReport(MOUSE_DEVICE_PATH, new byte[]{MOUSE_BUTTON_NONE, 0, 0}); // Release
    }

    // Writes HID report to character device
    private void writeHIDReport(String device, byte[] report) {
        // Check if character device exists
        if (CharacterDevice.characterDeviceMissing(device)) {
            Timber.wtf("Character device doesn't exist. Its existence is verified on app start, so the only reason this should happen is if it was removed *after* the app started.");
            makeCreateCharDeviceSnackbar();
            return;
        }

        // Write HID report
        try (FileOutputStream outputStream = new FileOutputStream(device)) {
            outputStream.write(report);
        } catch (IOException e) {
            String stacktrace = Log.getStackTraceString(e);
            Timber.e(stacktrace);
            if (stacktrace.toLowerCase().contains("errno 108")) {
                showSnackbar("ERROR: Your device seems to be disconnected. If not, try reseating the USB cable", Snackbar.LENGTH_LONG);
            } else if (stacktrace.toLowerCase().contains("permission denied")) {
                makeFixCharDevicePermissionsSnackbar(device);
            } else {
                showSnackbar("ERROR: Failed to send mouse report.", Snackbar.LENGTH_SHORT);
            }
        }
    }

    private Snackbar getSnackbar(String message, int length) {
        return Snackbar.make(parentLayout, message, length);
    }

    private void showSnackbar(String message, int length) {
        getSnackbar(message, length).show();
    }

    private void makeCreateCharDeviceSnackbar() {
        Snackbar snackbar = getSnackbar("ERROR: Character device has disappeared since the app was started.", Snackbar.LENGTH_INDEFINITE);
        snackbar.setAction("RECREATE", v -> {
            try {
                MainActivity.characterDevice.createCharacterDevice();
            } catch (NoRootPermissionsException e) {
                Timber.e("Failed to create character device, missing root permissions");
                Snackbar.make(parentLayout, "ERROR: Missing root permissions.", Snackbar.LENGTH_INDEFINITE).show();
            }
        });
        snackbar.show();
    }

    public void makeFixCharDevicePermissionsSnackbar(String devicePath) {
        Snackbar snackbar = getSnackbar("ERROR: Character device permissions seem incorrect.", Snackbar.LENGTH_INDEFINITE);
        snackbar.setAction("FIX", v -> {
            try {
                MainActivity.characterDevice.fixCharacterDevicePermissions(devicePath);
            } catch (NoRootPermissionsException e) {
                Timber.e("Failed to create character device, missing root permissions");
                Snackbar.make(parentLayout, "ERROR: Missing root permissions.", Snackbar.LENGTH_INDEFINITE).show();
            }
        });
        snackbar.show();
    }
}