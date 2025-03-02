package me.arianb.usb_hid_client

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

inline fun <T, R> StateFlow<T>.mapState(
    scope: CoroutineScope,
    crossinline transform: (value: T) -> R
): StateFlow<R> {
    return map { transform(it) }
        .stateIn(scope, SharingStarted.Eagerly, transform(this.value))
}
