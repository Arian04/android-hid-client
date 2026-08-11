package me.arianb.usb_hid_client.input_views

import android.view.MotionEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import me.arianb.usb_hid_client.MainViewModel
import me.arianb.usb_hid_client.R
import me.arianb.usb_hid_client.input_views.touch_input_handlers.MouseInputHandler
import me.arianb.usb_hid_client.input_views.touch_input_handlers.TouchInputHandler
import me.arianb.usb_hid_client.report_senders.pointer_device_senders.MouseSender
import me.arianb.usb_hid_client.report_senders.pointer_device_senders.TouchpadSender

@Composable
fun Touchpad(
    mainViewModel: MainViewModel = viewModel()
) {
    val pointerDeviceSender by mainViewModel.touchpadSender.collectAsState()

    when (val it = pointerDeviceSender) {
        is TouchpadSender -> {
            val touchInputHandler = remember { TouchInputHandler(it) }

            TouchpadForTouchpad(touchInputHandler)
        }

        is MouseSender -> {
            val mouseInputHandler = remember { MouseInputHandler(it) }

            TouchpadForMouse(mouseInputHandler)
        }
    }
}

@Composable
private fun TouchpadForTouchpad(
    touchInputHandler: TouchInputHandler,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(),
        verticalArrangement = Arrangement.SpaceAround,
    ) {
        val deviceOrientation = LocalConfiguration.current.orientation

        TouchpadContactSurfaceArea(
            modifier = Modifier.weight(1f),
            onTouchEvent = { motionEvent ->
                touchInputHandler.handleTouchMotionEvent(
                    motionEvent,
                    deviceOrientation,
                )
            },
        )
    }
}

@Composable
private fun TouchpadForMouse(
    mouseInputHandler: MouseInputHandler
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(),
        verticalArrangement = Arrangement.SpaceAround,
    ) {
        TouchpadContactSurfaceArea(
            modifier = Modifier.weight(1f),
            onTouchEvent = { motionEvent ->
                mouseInputHandler.handleTouchEvent(motionEvent)
            }
        )
    }
}

@Composable
private fun TouchpadContactSurfaceArea(
    modifier: Modifier = Modifier,
    onTouchEvent: (MotionEvent) -> Boolean = { false }
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .pointerInteropFilter { motionEvent: MotionEvent ->
                onTouchEvent(motionEvent)
            }
            .then(modifier),
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    ) {
        Text(
            text = stringResource(R.string.touchpad_label),
            modifier = Modifier.wrapContentHeight(Alignment.CenterVertically),
            textAlign = TextAlign.Center
        )
    }
}