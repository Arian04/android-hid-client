package me.arianb.usb_hid_client.ui.utils

import androidx.compose.runtime.Composable
import me.arianb.usb_hid_client.BuildConfig

@Composable
fun Experimental(
    content: @Composable () -> Unit
) {
    // TODO: add/implement user setting, maybe add red tint or something to differentiate experimental stuff
    if (BuildConfig.DEBUG) {
        content()
    }
}