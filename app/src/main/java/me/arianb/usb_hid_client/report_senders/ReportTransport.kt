package me.arianb.usb_hid_client.report_senders

import me.arianb.usb_hid_client.hid_utils.DevicePath
import me.arianb.usb_hid_client.hid_utils.UHID
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

sealed interface ReportWriteError {
    data class MissingCharDevice(val devicePath: DevicePath) : ReportWriteError
    data class BrokenPermissions(val devicePath: DevicePath) : ReportWriteError
    data object DeviceUnplugged : ReportWriteError
    data object GadgetDisabled : ReportWriteError
    data class Unknown(val cause: IOException) : ReportWriteError
}

sealed interface ReportTransport {
    @Throws(IOException::class)
    fun write(report: ByteArray)

    fun classifyError(e: IOException): ReportWriteError
}

data class CharDeviceTransport(val devicePath: DevicePath) : ReportTransport {
    override fun write(report: ByteArray) {
        FileOutputStream(devicePath.path).use { outputStream ->
            outputStream.write(report)
        }
    }

    override fun classifyError(e: IOException): ReportWriteError {
        // FileOutputStream throws FileNotFoundException for both ENOENT and EACCES, so check existence first.
        if (e is FileNotFoundException && !devicePath.exists()) {
            return ReportWriteError.MissingCharDevice(devicePath)
        }

        val message = (e.message ?: e.stackTraceToString()).lowercase()
        return when {
            message.contains("errno 108") -> ReportWriteError.DeviceUnplugged
            message.contains("permission denied") -> ReportWriteError.BrokenPermissions(devicePath)
            message.contains("enxio") -> ReportWriteError.GadgetDisabled
            else -> ReportWriteError.Unknown(e)
        }
    }
}

data object UhidTransport : ReportTransport {
    override fun write(report: ByteArray) {
        UHID.sendHidEvent(report)
    }

    override fun classifyError(e: IOException): ReportWriteError = ReportWriteError.Unknown(e)
}
