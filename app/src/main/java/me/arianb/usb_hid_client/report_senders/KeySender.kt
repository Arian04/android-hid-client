package me.arianb.usb_hid_client.report_senders

import me.arianb.usb_hid_client.hid_utils.CharacterDeviceManager

class KeySender :
    ReportSender(
        characterDevicePath = CharacterDeviceManager.KEYBOARD_DEVICE_PATH,
        usesReportIDs = true,
    ) {
    fun addStandardKey(modifier: Byte, key: Byte) {
        super.addReportToChannel(byteArrayOf(STANDARD_KEY, modifier, 0, key, 0))
    }

    fun addMediaKey(key: Byte) {
        super.addReportToChannel(byteArrayOf(MEDIA_KEY, key, 0))
    }

    companion object {
        // Report IDs
        private const val STANDARD_KEY: Byte = 0x01
        private const val MEDIA_KEY: Byte = 0x02
    }
}
