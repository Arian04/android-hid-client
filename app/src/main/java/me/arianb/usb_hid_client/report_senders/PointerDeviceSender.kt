package me.arianb.usb_hid_client.report_senders

import me.arianb.usb_hid_client.hid_utils.TouchpadDevicePath

sealed class PointerDeviceSender(
    touchpadDevicePath: TouchpadDevicePath
) : ReportSender(
    touchpadDevicePath
) {
    abstract fun send(contactID: Byte, tipSwitch: Boolean, x: Short, y: Short, scanTime: UShort, contactCount: Byte)
}
