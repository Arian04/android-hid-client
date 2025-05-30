package me.arianb.usb_hid_client

import android.os.Build
import android.os.Bundle
import android.os.Parcelable
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

inline fun <reified T : Parcelable> Bundle.getParcelableCompat(key: String): T? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        return getParcelable(key, T::class.java)
    } else {
        // Suppress deprecation because the alternative is only supported on SDKs >= the one that this
        // was deprecated in, so wrapping this in an SDK version conditional is the best I can do.
        @Suppress("DEPRECATION")
        return getParcelable<T>(key)
    }
}
