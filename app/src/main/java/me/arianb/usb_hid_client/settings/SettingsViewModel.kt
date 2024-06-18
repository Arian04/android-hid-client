package me.arianb.usb_hid_client.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val userPreferencesRepository = UserPreferencesRepository.getInstance(application)
    val userPreferencesFlow = userPreferencesRepository.userPreferencesFlow

    fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        userPreferencesRepository.getBoolean(key, defaultValue)

    fun putBoolean(key: String, value: Boolean) =
        userPreferencesRepository.putBoolean(key, value)
}

