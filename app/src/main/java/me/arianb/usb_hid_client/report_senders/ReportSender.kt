package me.arianb.usb_hid_client.report_senders

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import timber.log.Timber
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

abstract class ReportSender(
    val characterDevicePath: String,
) {
    private val reportsChannel = Channel<ByteArray>(Channel.UNLIMITED) {
        Timber.wtf("A channel with an unlimited buffer shouldn't be failing to receive elements")
    }

    @OptIn(ExperimentalStdlibApi::class)
    suspend fun start(onSuccess: () -> Unit, onException: (e: IOException) -> Unit) {
        for (report in reportsChannel) {
            try {
                Timber.d("REPORT HEX (len = %d): %s", report.size, report.toHexString())
                sendReport(report)
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

    open fun sendReport(report: ByteArray) {
        writeBytes(report, characterDevicePath)
    }

    companion object {
        val dispatcher = Dispatchers.IO

        // Writes HID report to character device
        @Throws(IOException::class, FileNotFoundException::class)
        fun writeBytes(report: ByteArray, path: String) {
            FileOutputStream(path).use { outputStream ->
                outputStream.write(report)
            }
        }
    }
}
