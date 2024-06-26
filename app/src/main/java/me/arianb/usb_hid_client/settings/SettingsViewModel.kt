package me.arianb.usb_hid_client.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val userPreferencesRepository = UserPreferencesRepository.getInstance(application)
    val userPreferencesFlow = userPreferencesRepository.userPreferencesFlow

    fun putAppTheme(value: AppTheme) =
        userPreferencesRepository.putAppTheme(value)

    fun getBoolean(key: PreferenceKey, defaultValue: Boolean): Boolean =
        userPreferencesRepository.getBoolean(key, defaultValue)

    fun putBoolean(key: PreferenceKey, value: Boolean) =
        userPreferencesRepository.putBoolean(key, value)
}
