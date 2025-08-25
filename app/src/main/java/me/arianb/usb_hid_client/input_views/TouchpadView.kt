package me.arianb.usb_hid_client.input_views

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.Gravity
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import me.arianb.usb_hid_client.MainViewModel
import me.arianb.usb_hid_client.R
import me.arianb.usb_hid_client.report_senders.PointerDeviceSender
import me.arianb.usb_hid_client.ui.utils.getColorByTheme
import timber.log.Timber

// LEGACY: migrate this to Compose

// From my understanding of this lint warning, I don't think it applies here.
@SuppressLint("ClickableViewAccessibility")
class TouchpadView : AppCompatTextView {
    private var currentScanTime: UShort = getScanTime()

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    fun setTouchListeners(touchpadSender: PointerDeviceSender) {
        setOnTouchListener { _: View?, motionEvent: MotionEvent ->
            val (pointerID, pointerX, pointerY) = getPointerTriple(motionEvent, pointerIndex = motionEvent.actionIndex)

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
                        val (thisID, thisX, thisY) = getPointerTriple(motionEvent, index)

                        touchpadSender.send(thisID, true, thisX, thisY, currentScanTime, pointerCount)
                    }
                }

                MotionEvent.ACTION_UP -> {
                    Timber.v("Action Up")
                    touchpadSender.send(pointerID, false, pointerX, pointerY, currentScanTime, pointerCount)
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
            true
        }
    }
}

private fun getPointerTriple(motionEvent: MotionEvent, pointerIndex: Int): Triple<Int, Int, Int> {
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

    // Get device rotation because if the device is suddenly a wide rectangle instead of a tall rectangle, then the
    // math changes.
    val isRotated = motionEvent.orientation != 0f

    val (pointerX, pointerY) = adjustRange(
        point = Pair(rawPointerX.toInt(), rawPointerY.toInt()),
        max = Pair(xMax, yMax),
        isRotated
    )

    return Triple(pointerID, pointerX, pointerY)
}

// "Stretches" the values of the points to use up the entire logical range.
private fun adjustRange(point: Pair<Int, Int>, max: Pair<Float, Float>, isRotated: Boolean): Pair<Int, Int> {
    Timber.d("DEVICE COORDINATE MAX = (%f, %f)", max.first, max.second)

    val (logicalMaxX, logicalMaxY) = if (isRotated) {
        // This works, but I'm not sure if it's okay to just be sending values higher than the logical maximum
        Pair(5000, 2500)
    } else {
        Pair(2500, 5000)
    }

    val (pointerMaxX, pointerMaxY) = if (isRotated) {
        Pair(max.second, max.first)
    } else {
        max
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

fun getScanTime(): UShort {
    // Convert nanoseconds to microseconds
    val microTime = System.nanoTime() / 1000

    // Convert microseconds to 100s of microseconds
    val hundredMicroTime = microTime / 100

    return hundredMicroTime.toUShort()
}

/**
 * Helper function to convert between types.
 */
fun PointerDeviceSender.send(
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

@Composable
fun Touchpad(mainViewModel: MainViewModel = viewModel()) {
    val touchpadText = stringResource(R.string.touchpad_label)
    val touchpadSender by mainViewModel.touchpadSender.collectAsState()

    val textColor = getColorByTheme()

    Surface(
        modifier = Modifier
//            .pointerInput(Unit) {
//
//            }
            .fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    ) {
        // For when I want to Compose-ify this
//        Text(
//            text = stringResource(R.string.touchpad_label),
//            modifier = Modifier.wrapContentHeight(Alignment.CenterVertically),
//            textAlign = TextAlign.Center
//        )
        AndroidView(
            factory = { context ->
                TouchpadView(context).apply {
                    text = touchpadText
                    textSize = 22f
                    gravity = Gravity.CENTER
                    setTouchListeners(touchpadSender)
                }
            },
            update = {
                if (textColor != null) {
                    it.setTextColor(textColor)
                }
            }
        )
    }
}
