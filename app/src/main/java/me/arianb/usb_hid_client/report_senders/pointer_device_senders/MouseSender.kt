package me.arianb.usb_hid_client.report_senders.pointer_device_senders

import me.arianb.usb_hid_client.hid_utils.TouchpadDevicePath
import me.arianb.usb_hid_client.report_senders.safeBitSetToByte
import java.util.BitSet

class MouseSender(
    mouseDevicePath: TouchpadDevicePath,
) : PointerDeviceSender(
    mouseDevicePath
) {
    private data class Coordinates<T>(val x: T, val y: T)

    private enum class MouseButtonClicked {
        None,
        Left,
        Right,
    }

    private var previousCoordinates: Coordinates<Short>? = null

    override fun send(contactID: Byte, tipSwitch: Boolean, x: Short, y: Short, scanTime: UShort, contactCount: Byte) {
        val mouseButtonClicked: MouseButtonClicked = if (tipSwitch) {
            getButtonClicked(Coordinates(x, y))
        } else {
            MouseButtonClicked.None
        }

        // This uses relative movements, so if the "previous" coordinates haven't been set (var is null), then we have
        // (relatively) moved 0 distance. So in that case, just use (0,0) as relative distance. Otherwise, calculate
        // the difference and use it.
        val relativeCoordinates: Coordinates<Short> = previousCoordinates?.let {
            Coordinates((x - it.x).toShort(), (y - it.y).toShort())
        } ?: Coordinates(0, 0)

        previousCoordinates = Coordinates(x, y)

        val isLeftButtonClicked = mouseButtonClicked == MouseButtonClicked.Left
        val isRightButtonClicked = mouseButtonClicked == MouseButtonClicked.Right
        super.addReportToChannel(
            getMouseReport(
                isLeftButtonClicked,
                isRightButtonClicked,
                relativeCoordinates.x.toByte(),
                relativeCoordinates.y.toByte()
            )
        )
    }

    private fun getButtonClicked(clickCoordinates: Coordinates<Short>): MouseButtonClicked {
        // TODO: implement this
        // if clickCoordinates puts this click in left half of touchpad, consider it a left click
        // else consider it a right click

        return MouseButtonClicked.None
    }

    private fun getMouseReport(
        isLeftButtonClicked: Boolean,
        isRightButtonClicked: Boolean,
        x: Byte,
        y: Byte,
    ): ByteArray {
        val firstByteBitSet = BitSet(8).apply {
            set(0, isLeftButtonClicked)
            set(1, isRightButtonClicked)

            // Padding
            clear(2, 8)
        }

        val buttonByte = safeBitSetToByte(firstByteBitSet)

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
