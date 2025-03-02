package me.arianb.usb_hid_client.report_senders

import me.arianb.usb_hid_client.hid_utils.TouchpadDevicePath
import me.arianb.usb_hid_client.hid_utils.UHID

class LoopbackTouchpadSender(
    touchpadDevicePath: TouchpadDevicePath
) : TouchpadSender(
    touchpadDevicePath
) {
    override fun sendReport(report: ByteArray) =
        UHID.sendHidEvent(report)
}
