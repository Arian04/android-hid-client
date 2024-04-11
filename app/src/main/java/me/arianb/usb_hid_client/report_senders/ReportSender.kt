package me.arianb.usb_hid_client.report_senders

import android.util.Log
import android.view.View
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.runBlocking
import me.arianb.usb_hid_client.hid_utils.CharacterDevice
import me.arianb.usb_hid_client.hid_utils.CharacterDevice.Companion.characterDeviceMissing
import me.arianb.usb_hid_client.shell_utils.NoRootPermissionsException
import timber.log.Timber
import java.io.FileOutputStream
import java.io.IOException
import java.util.LinkedList
import java.util.Locale
import java.util.Queue
import java.util.concurrent.locks.ReentrantLock


abstract class ReportSender(private val characterDevicePath: String, private val usesReportIDs: Boolean, private val characterDevice: CharacterDevice, private val parentLayout: View) : Runnable {
    private val reportQueue: Queue<ByteArray> = LinkedList()
    private val queueLock = ReentrantLock(true)
    private val queueNotEmptyCondition = queueLock.newCondition()

    override fun run() {
        Timber.d("ReportSender thread started")
        while (!Thread.interrupted()) {
            queueLock.lock()
            // Wait for the queue(s) to actually contain mouse events
            if (reportQueue.isEmpty()) {
                //Timber.d("Waiting for queue to not be empty.");
                try {
                    queueNotEmptyCondition.await()
                } catch (e: InterruptedException) {
                    Timber.e(Log.getStackTraceString(e))
                    queueLock.unlock()
                }
            }
            if (usesReportIDs) {
                sendReportPreserveID(reportQueue.remove())
            } else {
                // TODO: figure out why one time this line ran and caused a crash because the queue was empty.
                //       couldn't reproduce it. maybe a race condition or something?
                sendReport(reportQueue.remove())
            }
            queueLock.unlock()
        }
    }

    // IMPORTANT: Implement this when extending this class. Parameter list can be any number of bytes.
    // public void addReport(byte foo, byte bar, byte baz) {
    //     super.addReportWithLock(new byte[]{foo, bar, baz});
    // }
    //
    // Of course, make sure the argument list matches what the character device is expecting.
    protected fun addReportWithLock(report: ByteArray) {
        //Timber.d("trying to lock");
        queueLock.lock()
        reportQueue.add(report)
        queueNotEmptyCondition.signal()
        queueLock.unlock()
        //Timber.d("unlocked");
    }

    private fun sendReport(report: ByteArray) {
        // Send report
        writeHIDReport(report)

        // Send report of all zeroes to release
        writeHIDReport(ByteArray(report.size))
    }

    private fun sendReportPreserveID(report: ByteArray) {
        // Send report
        writeHIDReport(report)

        // Send report of (almost) all zeroes to release, but preserve report ID
        val releaseReport = ByteArray(report.size)
        releaseReport[0] = report[0]
        writeHIDReport(releaseReport)
    }

    // Writes HID report to character device
    private fun writeHIDReport(report: ByteArray) {
        // Check if character device exists
        if (characterDeviceMissing(characterDevicePath)) {
            Timber.wtf("Character device doesn't exist. Its existence is verified on app start, so the only reason this should happen is if it was removed *after* the app started.")
            makeCreateCharDeviceSnackbar()
            return
        }

        // Write HID report
        try {
            FileOutputStream(characterDevicePath).use { outputStream -> outputStream.write(report) }
        } catch (e: IOException) {
            val stacktrace = Log.getStackTraceString(e)
            Timber.e(stacktrace)
            if (stacktrace.lowercase(Locale.getDefault()).contains("errno 108")) {
                showSnackbar("ERROR: Your device seems to be disconnected. If not, try reseating the USB cable", Snackbar.LENGTH_LONG)
            } else if (stacktrace.lowercase(Locale.getDefault()).contains("permission denied")) {
                makeFixCharDevicePermissionsSnackbar(characterDevicePath)
            } else {
                showSnackbar("ERROR: Failed to send mouse report.", Snackbar.LENGTH_SHORT)
            }
        }
    }

    private fun getSnackbar(message: String, length: Int): Snackbar {
        return Snackbar.make(parentLayout, message, length)
    }

    private fun showSnackbar(message: String, length: Int) {
        getSnackbar(message, length).show()
    }

    private fun makeCreateCharDeviceSnackbar() {
        val snackbar = getSnackbar("ERROR: Character device has disappeared since the app was started.", Snackbar.LENGTH_INDEFINITE)
        snackbar.setAction("RECREATE") {
            runBlocking {
                try {
                    characterDevice.createCharacterDevices()
                } catch (e: NoRootPermissionsException) {
                    Timber.e("Failed to create character device, missing root permissions")
                    Snackbar.make(parentLayout, "ERROR: Missing root permissions.", Snackbar.LENGTH_INDEFINITE).show()
                }
            }
        }
        snackbar.show()
    }

    private fun makeFixCharDevicePermissionsSnackbar(devicePath: String) {
        val snackbar = getSnackbar("ERROR: Character device permissions seem incorrect.", Snackbar.LENGTH_INDEFINITE)
        snackbar.setAction("FIX") { v: View? ->
            try {
                characterDevice.fixCharacterDevicePermissions(devicePath)
            } catch (e: NoRootPermissionsException) {
                Timber.e("Failed to create character device, missing root permissions")
                Snackbar.make(parentLayout, "ERROR: Missing root permissions.", Snackbar.LENGTH_INDEFINITE).show()
            }
        }
        snackbar.show()
    }
}