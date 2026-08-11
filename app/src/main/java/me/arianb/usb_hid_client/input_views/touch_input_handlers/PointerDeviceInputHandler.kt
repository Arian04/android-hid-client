package me.arianb.usb_hid_client.input_views.touch_input_handlers

import me.arianb.usb_hid_client.report_senders.pointer_device_senders.PointerDeviceSender

sealed class PointerDeviceInputHandler {
    protected companion object {
        /**
         * Helper function to convert between types.
         */
        @JvmStatic
        protected fun PointerDeviceSender.send(
            pointerID: Int,
            tipSwitch: Boolean,
            x: Int,
            y: Int,
            currentScanTime: UShort,
            pointerCount: Int,
        ) = send(
            pointerID.toByte(),
            tipSwitch,
            x.toShort(),
            y.toShort(),
            currentScanTime,
            pointerCount.toByte(),
        )
    }
}