package me.arianb.usb_hid_client.report_senders;

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

public abstract class ReportSender implements Runnable {
    private final String characterDevicePath;
    private final boolean usesReportIDs;

    private final Queue<byte[]> reportQueue;

    private final ReentrantLock queueLock = new ReentrantLock(true);
    private final Condition queueNotEmptyCondition = queueLock.newCondition();

    private final View parentLayout;

    public ReportSender(String characterDevicePath, boolean usesReportIDs, View parentLayout) {
        this.characterDevicePath = characterDevicePath;
        this.usesReportIDs = usesReportIDs;
        this.parentLayout = parentLayout;
        reportQueue = new LinkedList<>();
    }

    @Override
    public void run() {
        Timber.d("ReportSender thread started");
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
            if (usesReportIDs) {
                sendReportPreserveID(reportQueue.remove());
            } else {
                // TODO: figure out why one time this line ran and caused a crash because the queue was empty.
                //       couldn't reproduce it. maybe a race condition or something?
                sendReport(reportQueue.remove());
            }
            queueLock.unlock();
        }
    }

    // IMPORTANT: Implement this when extending this class. Parameter list can be any number of bytes.
    // public void addReport(byte foo, byte bar, byte baz) {
    //     super.addReportWithLock(new byte[]{foo, bar, baz});
    // }
    //
    // Of course, make sure the argument list matches what the character device is expecting.

    protected void addReportWithLock(byte[] report) {
        //Timber.d("trying to lock");
        queueLock.lock();

        reportQueue.add(report);

        queueNotEmptyCondition.signal();
        queueLock.unlock();
        //Timber.d("unlocked");
    }

    private void sendReport(byte[] report) {
        // Send report
        writeHIDReport(report);

        // Send report of all zeroes to release
        writeHIDReport(new byte[report.length]);
    }

    private void sendReportPreserveID(byte[] report) {
        // Send report
        writeHIDReport(report);

        // Send report of (almost) all zeroes to release, but preserve report ID
        byte[] releaseReport = new byte[report.length];
        releaseReport[0] = report[0];
        writeHIDReport(releaseReport);
    }

    // Writes HID report to character device
    private void writeHIDReport(byte[] report) {

        // Check if character device exists
        if (CharacterDevice.characterDeviceMissing(characterDevicePath)) {
            Timber.wtf("Character device doesn't exist. Its existence is verified on app start, so the only reason this should happen is if it was removed *after* the app started.");
            makeCreateCharDeviceSnackbar();
            return;
        }

        // Write HID report
        try (FileOutputStream outputStream = new FileOutputStream(characterDevicePath)) {
            outputStream.write(report);
        } catch (IOException e) {
            String stacktrace = Log.getStackTraceString(e);
            Timber.e(stacktrace);
            if (stacktrace.toLowerCase().contains("errno 108")) {
                showSnackbar("ERROR: Your device seems to be disconnected. If not, try reseating the USB cable", Snackbar.LENGTH_LONG);
            } else if (stacktrace.toLowerCase().contains("permission denied")) {
                makeFixCharDevicePermissionsSnackbar(characterDevicePath);
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

    private void makeFixCharDevicePermissionsSnackbar(String devicePath) {
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