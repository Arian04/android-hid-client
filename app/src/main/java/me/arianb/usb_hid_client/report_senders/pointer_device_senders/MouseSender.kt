package me.arianb.usb_hid_client.report_senders.pointer_device_senders

import me.arianb.usb_hid_client.hid_utils.TouchpadDevicePath

class MouseSender(
    mouseDevicePath: TouchpadDevicePath,
) : PointerDeviceSender(
    mouseDevicePath
) {
    override fun send(
        contactID: Byte,
        tipSwitch: Boolean,
        x: Short,
        y: Short,
        scanTime: UShort,
        contactCount: Byte,
    ) {
        // Delegating this to another method to make it more obvious which arguments are unused
        return this.sendMouseReport(x.toByte(), y.toByte())
    }

    fun sendMouseReport(
        relativeX: Byte,
        relativeY: Byte,
    ) {
        super.addReportToChannel(
            getMouseReport(
                relativeX,
                relativeY,
            )
        )
    }

    private fun getMouseReport(
        x: Byte,
        y: Byte,
    ): ByteArray {
        val buttonByte: Byte = 0
        val trailingPaddingByteArray = ByteArray(5)

        return byteArrayOf(
            MOUSE_REPORT_ID,
            buttonByte,
            x,
            y,
        ) + trailingPaddingByteArray
    }

    companion object {
        private const val MOUSE_REPORT_ID: Byte = 1
    }
}
