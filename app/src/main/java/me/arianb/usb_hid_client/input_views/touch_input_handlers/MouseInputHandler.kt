package me.arianb.usb_hid_client.input_views.touch_input_handlers

import android.os.Build
import android.view.MotionEvent
import me.arianb.usb_hid_client.report_senders.pointer_device_senders.MouseSender
import me.arianb.usb_hid_client.report_senders.pointer_device_senders.PointerDeviceSender
import timber.log.Timber

class MouseInputHandler(
    private val mouseSender: MouseSender
) : PointerDeviceInputHandler() {
    private data class Coordinates<T>(val x: T, val y: T)

    private var previousCoordinates: Coordinates<Short>? = null
    private var currentTouchpadButtonState: PointerDeviceSender.TouchpadButtonState =
        PointerDeviceSender.TouchpadButtonState(
            isLeftButtonPressed = false,
            isRightButtonPressed = false,
        )

    /**
     * Value class to represent a pointer index.
     *
     * Its purpose is to avoid accidentally mixing up the pointer index with the pointer ID, as they're both ints.
     */
    @JvmInline
    private value class PointerIndex(val index: Int)

    /**
     * Represents the ID of the currently active pointer in a touch event sequence.
     *
     * This is null if no pointer is active.
     */
    private var activePointerId: Int? = null

    fun handleTouchEvent(motionEvent: MotionEvent): Boolean {
        val (pointerX, pointerY) = run {
            val pointerIndexWrapper = getActivePointerInfo(motionEvent)
            val pointerIndex = pointerIndexWrapper.index

            Timber.d("handleTouchEvent(): pointerIndex: $pointerIndex")

            // This just guards against a potential bug causing a complete app crash due to calling
            // getRawX() or getX() below with a negative pointer index.
            if (pointerIndex < 0) {
                Timber.wtf("handleTouchEvent(): pointerIndex of '$pointerIndex' is negative. This is probably a logic bug.")

                // Reset the active pointer ID so that the next event we receive will treat the first pointer as the new primary pointer.
                // Should prevent a stale pointerID from getting stuck as the active one in case of a bug.
                //
                // Haven't thoroughly tested this (which is why it's commented out)
                //activePointerId = null

                return false
            }

            val (rawPointerX, rawPointerY) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Pair(motionEvent.getRawX(pointerIndex), motionEvent.getRawY(pointerIndex))
            } else {
                Pair(motionEvent.getX(pointerIndex), motionEvent.getY(pointerIndex))
            }

            Pair<Int, Int>(rawPointerX.toInt(), rawPointerY.toInt())
        }

        this.sendAbsoluteMouseMovement(pointerX.toShort(), pointerY.toShort(), currentTouchpadButtonState)

        return true
    }

    /**
     * Retrieves the active pointer information based on the provided motion event. Allows us to specifically
     * handle pointer movements from the pointer that first initiated the sequence of touch events on the element.
     *
     * This avoids a bug where if the first finger starts on a TouchpadButton when doing a multifinger
     * "press button and drag finger" operation (ex: hold down left click, then drag something), it gets messed up because
     * it treats that first pointer that pressed down the button as the primary pointer and ignores the second. The
     * reverse of that worked properly though.
     */
    private fun getActivePointerInfo(motionEvent: MotionEvent): PointerIndex {
        Timber.d("getActivePointerInfo()")
        Timber.d("\tmotionEvent: $motionEvent")
        Timber.d("\tactionIndex: ${motionEvent.actionIndex}")
        Timber.d("\taction: ${MotionEvent.actionToString(motionEvent.actionMasked)}")

        // Capture activePointerId so we can trust that it doesn't change during the event sequence
        var thisActivePointerId = activePointerId

        val isPointerActionUp: Boolean =
            motionEvent.actionMasked == MotionEvent.ACTION_POINTER_UP || motionEvent.actionMasked == MotionEvent.ACTION_UP

        val pointerIndex = if (isPointerActionUp) {
            val activePointerIndex = motionEvent.actionIndex

            // Pointer is released, so let's set this to null so that the next iteration can treat the next pointer
            // that we receive an event from as the new primary pointer
            thisActivePointerId = null

            activePointerIndex
        } else if (thisActivePointerId == null) {
            val activePointerIndex = motionEvent.actionIndex

            // thisActivePointerId is null, so we're going to treat this pointer as the new primary pointer
            thisActivePointerId = motionEvent.getPointerId(activePointerIndex)

            activePointerIndex
        } else {
            // This is a normal pointer movement, so we can just use the active pointer ID
            val activePointerIndex = motionEvent.findPointerIndex(thisActivePointerId)

            activePointerIndex
        }

        Timber.d("\tpointerID: $thisActivePointerId")
        Timber.d("\tpointerIndex: $pointerIndex")

        activePointerId = thisActivePointerId

        return PointerIndex(pointerIndex)
    }

    fun sendAbsoluteMouseMovement(
        absoluteX: Short,
        absoluteY: Short,
        touchpadButtonState: PointerDeviceSender.TouchpadButtonState? = null,
    ) {
        // This uses relative movements, so if the "previous" coordinates haven't been set (var is null), then we have
        // (relatively) moved 0 distance. So in that case, just use (0,0) as relative distance. Otherwise, calculate
        // the difference and use it.
        val difference: Coordinates<Short> = previousCoordinates?.let {
            Coordinates((absoluteX - it.x).toShort(), (absoluteY - it.y).toShort())
        } ?: Coordinates(0, 0)

        previousCoordinates = Coordinates(absoluteX, absoluteY)

        if (touchpadButtonState != null) {
            currentTouchpadButtonState = touchpadButtonState
        }

        return mouseSender.sendMouseReport(difference.x.toByte(), difference.y.toByte(), currentTouchpadButtonState)
    }

    fun sendRelativeMouseMovement(
        relativeX: Byte,
        relativeY: Byte,
        touchpadButtonState: PointerDeviceSender.TouchpadButtonState?
    ) {
        val updatedCoordinates: Coordinates<Short> = previousCoordinates?.let {
            val x: Short = (it.x + relativeX).toShort()
            val y: Short = (it.y + relativeY).toShort()

            Coordinates(x, y)
        } ?: Coordinates(0, 0)

        previousCoordinates = updatedCoordinates

        if (touchpadButtonState != null) {
            currentTouchpadButtonState = touchpadButtonState
        }

        return mouseSender.sendMouseReport(relativeX, relativeY, currentTouchpadButtonState)
    }

    // Send new button state with 0 relative mouse movement
    fun sendButtonStateUpdate(touchpadButtonState: PointerDeviceSender.TouchpadButtonState) =
        this.sendRelativeMouseMovement(0, 0, touchpadButtonState)
}