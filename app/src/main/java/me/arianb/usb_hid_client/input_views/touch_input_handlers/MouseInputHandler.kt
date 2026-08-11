package me.arianb.usb_hid_client.input_views.touch_input_handlers

import android.os.Build
import android.view.MotionEvent
import me.arianb.usb_hid_client.report_senders.pointer_device_senders.MouseSender

class MouseInputHandler(
    private val mouseSender: MouseSender
) : PointerDeviceInputHandler() {
    private data class Coordinates<T>(val x: T, val y: T)

    private var previousCoordinates: Coordinates<Short>? = null

    fun handleTouchEvent(motionEvent: MotionEvent): Boolean {
        val (pointerID, pointerX, pointerY) = motionEvent.let {
            val pointerIndex = it.actionIndex

            val pointerID = it.getPointerId(pointerIndex)

            val (rawPointerX, rawPointerY) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Pair(motionEvent.getRawX(pointerIndex), motionEvent.getRawY(pointerIndex))
            } else {
                Pair(motionEvent.getX(pointerIndex), motionEvent.getY(pointerIndex))
            }

            Triple<Int, Int, Int>(pointerID, rawPointerX.toInt(), rawPointerY.toInt())
        }

        if (pointerID != 0) {
            return false
        }

        this.sendAbsoluteMouseMovement(pointerX.toShort(), pointerY.toShort())

        return true
    }

    fun sendAbsoluteMouseMovement(
        absoluteX: Short,
        absoluteY: Short,
    ) {
        // This uses relative movements, so if the "previous" coordinates haven't been set (var is null), then we have
        // (relatively) moved 0 distance. So in that case, just use (0,0) as relative distance. Otherwise, calculate
        // the difference and use it.
        val difference: Coordinates<Short> = previousCoordinates?.let {
            Coordinates((absoluteX - it.x).toShort(), (absoluteY - it.y).toShort())
        } ?: Coordinates(0, 0)

        previousCoordinates = Coordinates(absoluteX, absoluteY)

        return mouseSender.sendMouseReport(difference.x.toByte(), difference.y.toByte())
    }

    fun sendRelativeMouseMovement(
        relativeX: Byte,
        relativeY: Byte,
    ) {
        val updatedCoordinates: Coordinates<Short> = previousCoordinates?.let {
            val x: Short = (it.x + relativeX).toShort()
            val y: Short = (it.y + relativeY).toShort()

            Coordinates(x, y)
        } ?: Coordinates(0, 0)

        previousCoordinates = updatedCoordinates

        return mouseSender.sendMouseReport(relativeX, relativeY)
    }
}