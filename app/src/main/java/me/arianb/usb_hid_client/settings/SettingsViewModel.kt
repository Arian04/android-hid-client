package me.arianb.usb_hid_client.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val userPreferencesRepository = UserPreferencesRepository.getInstance(application)
    val userPreferencesFlow = userPreferencesRepository.userPreferencesFlow

    fun <T> getPreference(key: PreferenceKey<T>): T =
        userPreferencesRepository.getPreference(key)

    fun <T> setPreference(key: PreferenceKey<T>, value: T) =
        userPreferencesRepository.setPreference(key, value)
}
