package me.arianb.usb_hid_client.input_views.touch_input_handlers

import android.content.res.Configuration
import android.os.Build
import android.view.InputDevice
import android.view.MotionEvent
import me.arianb.usb_hid_client.report_senders.pointer_device_senders.PointerDeviceSender
import timber.log.Timber

class TouchInputHandler {
    private var currentScanTime: UShort = getScanTime()

    fun handleTouchMotionEvent(
        touchpadSender: PointerDeviceSender,
        motionEvent: MotionEvent,
        deviceOrientation: Int,
    ): Boolean {
        val (pointerID, pointerX, pointerY) = getPointerTriple(
            motionEvent,
            motionEvent.actionIndex,
            deviceOrientation
        )

        // Scan time is reset when pointer 0 is sent
        if (pointerID == 0) {
            currentScanTime = getScanTime()
        }

        val pointerCount = motionEvent.pointerCount
        when (val action = motionEvent.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                Timber.v("Action Down")
                touchpadSender.send(pointerID, true, pointerX, pointerY, currentScanTime, pointerCount)
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                Timber.v("Action Pointer Down")
                touchpadSender.send(pointerID, true, pointerX, pointerY, currentScanTime, pointerCount)
            }

            MotionEvent.ACTION_MOVE -> {
                Timber.v("Action Move")
                for (index in 0..<pointerCount) {
                    val (thisID, thisX, thisY) = getPointerTriple(motionEvent, index, deviceOrientation)

                    touchpadSender.send(thisID, true, thisX, thisY, currentScanTime, pointerCount)
                }
            }

            MotionEvent.ACTION_UP -> {
                Timber.v("Action Up")
                touchpadSender.send(
                    pointerID, false, pointerX, pointerY, currentScanTime, pointerCount
                )
            }

            MotionEvent.ACTION_POINTER_UP -> {
                Timber.v("Action Pointer Up")
                touchpadSender.send(pointerID, false, pointerX, pointerY, currentScanTime, pointerCount)
            }

            MotionEvent.ACTION_CANCEL -> {
                Timber.v("Action Cancel")
                touchpadSender.send(pointerID, false, pointerX, pointerY, currentScanTime, pointerCount)
            }

            else -> {
                Timber.w("UNHANDLED ACTION CONSTANT: %s", action)
            }
        }

        return true
    }

    private companion object {
        private fun getPointerTriple(
            motionEvent: MotionEvent,
            pointerIndex: Int,
            deviceOrientation: Int
        ): Triple<Int, Int, Int> {
            val pointerID = motionEvent.getPointerId(pointerIndex)

            val (rawPointerX, rawPointerY) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Pair(motionEvent.getRawX(pointerIndex), motionEvent.getRawY(pointerIndex))
            } else {
                Pair(motionEvent.getX(pointerIndex), motionEvent.getY(pointerIndex))
            }

            // NOTE: MotionEvent has a bunch of properties (and properties of those properties) which are platform types.
            //       I'm writing this note to be explicit about the fact that the following code should be treated cautiously so
            //       as to not cause NPEs.
            // --- start of unsafe code ---
            val device: InputDevice? = motionEvent.device

            // If null, just use some hardcoded safe-ish values
            val xMax: Float = device?.getMotionRange(MotionEvent.AXIS_X)?.max ?: 1500f
            val yMax: Float = device?.getMotionRange(MotionEvent.AXIS_Y)?.max ?: 3000f
            // --- end of unsafe code ---

            // The underlying touchpad report descriptor says it's physically "portrait" (taller than it is wide, like a phone).
            // If the device itself is actually wider than it is tall ("landscape"), we need to know, so we can adjust the math.
            //
            // Note:
            //  We cannot just swap x and y values to fix things, because the target device needs to know what physical
            //  directions the user is inputting. Otherwise, things like an upward swipe gesture, might be registered as a swipe
            //  to the right or left instead.
            //
            // If orientation is ORIENTATION_PORTRAIT or ORIENTATION_UNKNOWN or just anything other than landscape, treat it
            // as being in portrait.
            val isPortrait = deviceOrientation != Configuration.ORIENTATION_LANDSCAPE

            Timber.d("motionEvent.orientation = %f", motionEvent.orientation)

            val (pointerX, pointerY) = adjustRange(
                point = Pair(rawPointerX.toInt(), rawPointerY.toInt()),
                max = Pair(xMax, yMax),
                isPortrait
            )

            return Triple(pointerID, pointerX, pointerY)
        }

        // "Stretches" the values of the points to use up the entire logical range.
        private fun adjustRange(point: Pair<Int, Int>, max: Pair<Float, Float>, isPortrait: Boolean): Pair<Int, Int> {
            Timber.d("--- adjustRange ---")
            Timber.d("Input point: %s", point)
            Timber.d("isPortrait: %b", isPortrait)
            Timber.d("DEVICE COORDINATE MAX = (%f, %f)", max.first, max.second)

            val (logicalMaxX, logicalMaxY) = if (isPortrait) {
                Pair(2500, 5000)
            } else {
                // This works, but I'm not sure if it's okay to just be sending values higher than the logical maximum
                Pair(5000, 2500)
            }

            val (pointerMaxX, pointerMaxY) = if (isPortrait) {
                max
            } else {
                Pair(max.second, max.first)
            }

            val xRatio: Float = logicalMaxX / pointerMaxX
            val yRatio: Float = logicalMaxY / pointerMaxY

            val adjustedX = (point.first * xRatio).toInt()
            val adjustedY = (point.second * yRatio).toInt()

            // This will probably never actually be necessary, but might as well do it just in case.
            val finalX = adjustedX.coerceIn(0, logicalMaxX)
            val finalY = adjustedY.coerceIn(0, logicalMaxY)

            return Pair(finalX, finalY)
        }

        private fun getScanTime(): UShort {
            // Convert nanoseconds to microseconds
            val microTime = System.nanoTime() / 1000

            // Convert microseconds to 100s of microseconds
            val hundredMicroTime = microTime / 100

            return hundredMicroTime.toUShort()
        }

        /**
         * Helper function to convert between types.
         */
        private fun PointerDeviceSender.send(
            pointerID: Int,
            tipSwitch: Boolean,
            x: Int,
            y: Int,
            currentScanTime: UShort,
            pointerCount: Int
        ) = send(
            pointerID.toByte(),
            tipSwitch,
            x.toShort(),
            y.toShort(),
            currentScanTime,
            pointerCount.toByte()
        )
    }
}