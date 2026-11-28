package me.arianb.usb_hid_client

import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Derives a [StateFlow] from a selected slice of [T], invoking [transform] only when the
 * selected key changes (avoiding duplicate construction on startup or unrelated updates).
 */
fun <T, K, R> StateFlow<T>.mapState(
    scope: CoroutineScope,
    keySelector: (value: T) -> K,
    transform: (key: K) -> R,
): StateFlow<R> {
    var currentKey = keySelector(value)
    var currentValue = transform(currentKey)
    return map { value ->
        val key = keySelector(value)
        if (key != currentKey) {
            currentKey = key
            currentValue = transform(key)
        }
        currentValue
    }.stateIn(scope, SharingStarted.Eagerly, currentValue)
}

inline fun <reified T : Parcelable> Bundle.getParcelableCompat(key: String?): T? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        return getParcelable(key, T::class.java)
    } else {
        // Suppress deprecation because the alternative is only supported on SDKs >= the one that this
        // was deprecated in, so wrapping this in an SDK version conditional is the best I can do.
        @Suppress("DEPRECATION")
        return getParcelable<T>(key)
    }
}

inline fun <reified T : Parcelable> Bundle.getParcelableArrayCompat(key: String?): Array<T>? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        return getParcelableArray<T>(key, T::class.java)
    } else {
        // Suppress deprecation because the alternative is only supported on SDKs >= the one that this
        // was deprecated in, so wrapping this in an SDK version conditional is the best I can do.
        // NOTE: in SDKs before Tiramisu, Bundle.getParcelableArray returns Parcelable[] (not T[]), so we have to
        //  copy into a typed Array<T> (and return null on element type mismatch to match API >= Tiramisu behavior).
        @Suppress("DEPRECATION")
        val rawArray = getParcelableArray(key) ?: return null
        return Array(rawArray.size) { index ->
            rawArray[index] as? T ?: return null
        }
    }
}