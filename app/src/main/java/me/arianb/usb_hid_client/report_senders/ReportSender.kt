package me.arianb.usb_hid_client.report_senders

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import timber.log.Timber
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

abstract class ReportSender(
    val characterDevicePath: String,
    val usesReportIDs: Boolean,
) {
    private val reportsChannel = Channel<ByteArray>(Channel.UNLIMITED) {
        Timber.wtf("A channel with an unlimited buffer shouldn't be failing to receive elements")
    }

    suspend fun start(onSuccess: () -> Unit, onException: (e: IOException) -> Unit) {
        for (report in reportsChannel) {
            try {
                sendReport(report, characterDevicePath, usesReportIDs)
                onSuccess()
            } catch (e: IOException) {
                Timber.d(e)

                // TODO: map exception to a sealed error type and pass that to lambda?
                onException(e)
            }
        }
    }

    // IMPORTANT: Implement this when extending this class. Parameter list can be any number of bytes.
    // public void addReport(byte foo, byte bar, byte baz) {
    //     super.addReportToChannel(new byte[]{foo, bar, baz});
    // }
    //
    // Of course, make sure the argument list matches what the character device is expecting.
    protected fun addReportToChannel(report: ByteArray) {
        // This should always succeed since the Channel's buffer is unlimited
        reportsChannel.trySend(report)
    }

    private fun sendReport(
        report: ByteArray,
        characterDevicePath: String,
        preserveReportID: Boolean = false
    ) {
        // Send report
        writeHIDReport(report, characterDevicePath)

        // Send report of all zeroes (preserving report ID if necessary) to release
        val releaseReport = ByteArray(report.size)
        if (preserveReportID) {
            releaseReport[0] = report[0]
        }
        writeHIDReport(releaseReport, characterDevicePath)
    }

    companion object {
        val dispatcher = Dispatchers.IO

        // Writes HID report to character device
        @Throws(IOException::class, FileNotFoundException::class)
        private fun writeHIDReport(report: ByteArray, characterDevicePath: String) {
            FileOutputStream(characterDevicePath).use { outputStream ->
                outputStream.write(report)
            }
        }
    }
}
