package me.arianb.usb_hid_client.settings

import android.content.SharedPreferences
import androidx.core.content.edit

sealed class PreferenceKey<T>(val key: String, val defaultValue: T) {
    abstract fun getValue(sharedPreferences: SharedPreferences): T
    abstract fun setValue(sharedPreferences: SharedPreferences, value: T)

    fun edit(
        sharedPreferences: SharedPreferences,
        commit: Boolean = false,
        action: SharedPreferences.Editor.() -> Unit
    ) {
        sharedPreferences.edit(commit = commit) {
            action()
        }
    }

    fun resetToDefault(sharedPreferences: SharedPreferences) {
        edit(sharedPreferences) {
            remove(key)
        }
    }
}

sealed class BooleanPreferenceKey : PreferenceKey<Boolean> {
    constructor(key: String, defaultValue: Boolean) : super(key, defaultValue)

    override fun getValue(sharedPreferences: SharedPreferences): Boolean {
        return sharedPreferences.getBoolean(key, defaultValue)
    }

    override fun setValue(sharedPreferences: SharedPreferences, value: Boolean) {
        edit(sharedPreferences) {
            putBoolean(key, value)
        }
    }
}

sealed class StringPreferenceKey : PreferenceKey<String> {
    constructor(key: String, defaultValue: String) : super(key, defaultValue)

    override fun getValue(sharedPreferences: SharedPreferences): String {
        return sharedPreferences.getString(key, defaultValue) ?: defaultValue
    }

    override fun setValue(sharedPreferences: SharedPreferences, value: String) {
        edit(sharedPreferences) {
            putString(key, value)
        }
    }
}

sealed class ObjectPreferenceKey<T>(
    key: String,
    defaultValue: T,
    val fromStringPreference: (value: String) -> T,
    val toStringPreference: (value: T) -> String
) : PreferenceKey<T>(key, defaultValue) {
    override fun getValue(sharedPreferences: SharedPreferences): T {
        val stringValue = sharedPreferences.getString(key, null)

        return if (stringValue == null) {
            defaultValue
        } else {
            fromStringPreference(stringValue)
        }
    }

    override fun setValue(sharedPreferences: SharedPreferences, value: T) {
        val stringValue = toStringPreference(value)

        edit(sharedPreferences) {
            putString(key, stringValue)
        }
    }
}
