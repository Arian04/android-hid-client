package me.arianb.usb_hid_client.report_senders

import me.arianb.usb_hid_client.hid_utils.DevicePath
import me.arianb.usb_hid_client.hid_utils.UHID
import java.io.FileOutputStream
import java.io.IOException

sealed interface ReportTransport {
    @Throws(IOException::class)
    fun write(report: ByteArray)
}

data class CharDeviceTransport(val devicePath: DevicePath) : ReportTransport {
    override fun write(report: ByteArray) {
        FileOutputStream(devicePath.path).use { outputStream ->
            outputStream.write(report)
        }
    }
}

data object UhidTransport : ReportTransport {
    override fun write(report: ByteArray) {
        UHID.sendHidEvent(report)
    }
}
