package me.arianb.usb_hid_client.report_senders

import me.arianb.usb_hid_client.hid_utils.CharacterDeviceManager

class MouseSender :
    ReportSender(
        characterDevicePath = CharacterDeviceManager.MOUSE_DEVICE_PATH,
        usesReportIDs = false,
    ) {
    fun click(button: Byte) {
        super.addReportToChannel(byteArrayOf(button, 0, 0))
    }

    fun move(x: Byte, y: Byte) {
        super.addReportToChannel(byteArrayOf(0, x, y))
    }

    companion object {
        const val MOUSE_BUTTON_LEFT: Byte = 0b001
        const val MOUSE_BUTTON_RIGHT: Byte = 0b010
        const val MOUSE_BUTTON_MIDDLE: Byte = 0b100
    }
}
