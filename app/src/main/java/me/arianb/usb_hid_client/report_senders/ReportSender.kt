package me.arianb.usb_hid_client.report_senders

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException

abstract class ReportSender(
    val transport: ReportTransport
) {
    private val reportsChannel = Channel<ByteArray>(Channel.UNLIMITED) {
        Timber.wtf("A channel with an unlimited buffer shouldn't be failing to receive elements")
    }

    suspend fun start(onSuccess: () -> Unit, onError: (error: ReportWriteError) -> Unit) = withContext(Dispatchers.IO) {
        for (report in reportsChannel) {
            try {
                Timber.v("REPORT HEX (len = %d): %s", report.size, report.toHexString())
                sendReport(report)
                onSuccess()
            } catch (e: IOException) {
                Timber.v(e)
                onError(transport.classifyError(e))
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

    protected open fun sendReport(report: ByteArray) {
        transport.write(report)
    }
}
