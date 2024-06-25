package me.arianb.usb_hid_client.input_views

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import androidx.appcompat.widget.AppCompatTextView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import me.arianb.usb_hid_client.MainViewModel
import me.arianb.usb_hid_client.R
import me.arianb.usb_hid_client.report_senders.MouseSender
import me.arianb.usb_hid_client.ui.theme.getColorByTheme
import timber.log.Timber
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

// LEGACY: migrate this to Compose

// TODO: address the linting issue below
@SuppressLint("ClickableViewAccessibility")
class TouchpadView : AppCompatTextView {
    private var mVelocityTracker: VelocityTracker? = null

    // Vars for tracking a single touch event, which I'm defining as: ACTION_DOWN, (anything), ACTION_UP
    private var currentPointerCount = 0

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    fun setTouchListeners(mouseSender: MouseSender) {
        // TODO:
        //  - add gestures
        //      - on double click and drag, send report without "release" until sending release on finger up
        //      - on long press and drag, send report without "release" until sending release on finger up
        //      - on two finger scroll, send scroll events
        setOnTouchListener { _: View?, motionEvent: MotionEvent ->
            val action = motionEvent.actionMasked
            val index = motionEvent.actionIndex
            val pointerId = motionEvent.getPointerId(index)
            when (action) {
                MotionEvent.ACTION_DOWN -> {
                    currentPointerCount = 1
                    Timber.d("Action Down")
                    if (mVelocityTracker == null) {
                        mVelocityTracker = VelocityTracker.obtain()
                    } else {
                        mVelocityTracker!!.clear()
                    }
                    mVelocityTracker!!.addMovement(motionEvent)
                }

                MotionEvent.ACTION_MOVE -> {
                    //Timber.d("Action Move");
                    mVelocityTracker!!.addMovement(motionEvent)

                    // Compute velocity (cap it to byte because the report uses a byte per axis)
                    mVelocityTracker!!.computeCurrentVelocity(10, Byte.MAX_VALUE.toFloat())
                    var xVelocity = mVelocityTracker!!.getXVelocity(pointerId)
                    var yVelocity = mVelocityTracker!!.getYVelocity(pointerId)

                    // Scale up velocities < 1 in magnitude (accounting for deadzone) to allow for precise movements
                    xVelocity = scaleWithDeadzone(xVelocity)
                    yVelocity = scaleWithDeadzone(yVelocity)
                    val x = xVelocity.toInt().toByte()
                    val y = yVelocity.toInt().toByte()
                    mouseSender.move(x, y)
                }

                MotionEvent.ACTION_UP -> {
                    Timber.d("Action Up (max pointer count = %d)", currentPointerCount)
                    val pointerDownTimeMillis = motionEvent.eventTime - motionEvent.downTime
                    Timber.d("Pointer was down for %d milliseconds", pointerDownTimeMillis)

                    // If user has been holding down for a while, don't send any clicks, they're probably dragging
                    if (pointerDownTimeMillis > 300) {
                        return@setOnTouchListener false
                    }
                    when (currentPointerCount) {
                        1 -> mouseSender.click(MouseSender.MOUSE_BUTTON_LEFT)
                        2 -> mouseSender.click(MouseSender.MOUSE_BUTTON_RIGHT)
                        3 -> mouseSender.click(MouseSender.MOUSE_BUTTON_MIDDLE)
                    }
                    currentPointerCount = 0
                }

                MotionEvent.ACTION_POINTER_DOWN -> currentPointerCount++
                MotionEvent.ACTION_POINTER_UP -> Timber.d("Action Pointer Up")
                MotionEvent.ACTION_CANCEL -> {
                    Timber.d("Action Cancel")
                    mVelocityTracker!!.recycle() // Return a VelocityTracker object back to be re-used by others.
                    currentPointerCount = 0
                }
            }
            true
        }
    }

    private fun scaleWithDeadzone(inputVelocity: Float): Float {
        return if (abs(inputVelocity.toDouble()) != 0.0 && abs(inputVelocity.toDouble()) > DEADZONE) {
            if (inputVelocity < 0) {
                floor(inputVelocity.toDouble()).toFloat()
            } else {
                ceil(inputVelocity.toDouble()).toFloat()
            }
        } else {
            inputVelocity
        }
    }

    companion object {
        private const val DEADZONE = 0.3f
    }
}

@Composable
fun Touchpad(mainViewModel: MainViewModel = viewModel()) {
    val touchpadText = stringResource(R.string.touchpad_label)
    val mouseSender = mainViewModel.mouseSender

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
                    setTouchListeners(mouseSender)
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
