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
        touchpadButtonState: TouchpadButtonState
    ) {
        // Delegating this to another method to make it more obvious which arguments are unused
        return this.sendMouseReport(x.toByte(), y.toByte(), touchpadButtonState)
    }

    fun sendMouseReport(
        relativeX: Byte,
        relativeY: Byte,
        touchpadButtonState: TouchpadButtonState
    ) {
        super.addReportToChannel(
            getMouseReport(
                relativeX,
                relativeY,
                touchpadButtonState.toByte()
            )
        )
    }

    private fun getMouseReport(
        x: Byte,
        y: Byte,
        buttonByte: Byte
    ): ByteArray {
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
