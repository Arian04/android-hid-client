package me.arianb.usb_hid_client.report_senders

import me.arianb.usb_hid_client.hid_utils.CharacterDeviceManager

class KeySender :
    ReportSender(
        characterDevicePath = CharacterDeviceManager.KEYBOARD_DEVICE_PATH,
    ) {
    fun addStandardKey(modifier: Byte, key: Byte) {
        super.addReportToChannel(byteArrayOf(STANDARD_KEY, modifier, 0, key, 0))
    }

    fun addMediaKey(key: Byte) {
        super.addReportToChannel(byteArrayOf(MEDIA_KEY, key, 0))
    }

    // Every time we send a report, we only send the "key-down" event. This method will automatically send the "key-up"
    // event right afterwards
    override fun sendReport(report: ByteArray) {
        // Send "key-down" report
        writeBytes(report, characterDevicePath)

        // Send "key-up" report of all zeroes (preserving report ID) to release
        val releaseReport = ByteArray(report.size)
        releaseReport[0] = report[0]
        writeBytes(releaseReport, characterDevicePath)
    }

    companion object {
        // Report IDs
        private const val STANDARD_KEY: Byte = 0x01
        private const val MEDIA_KEY: Byte = 0x02
    }
}
