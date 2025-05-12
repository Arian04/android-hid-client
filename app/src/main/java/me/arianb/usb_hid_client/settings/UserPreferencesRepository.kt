package me.arianb.usb_hid_client.settings

import android.app.Application
import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.preference.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import me.arianb.usb_hid_client.R
import me.arianb.usb_hid_client.hid_utils.CharacterDeviceManager
import me.arianb.usb_hid_client.hid_utils.KeyboardDevicePath
import me.arianb.usb_hid_client.hid_utils.TouchpadDevicePath
import me.arianb.usb_hid_client.hid_utils.UsbGadgetPath

sealed class AppPreference(val preference: PreferenceKey<*>) {
    data object OnboardingDoneKey : BooleanPreferenceKey("onboarding_done", false)
    data object ClearManualInputKey : BooleanPreferenceKey("clear_manual_input", false)
    data object VolumeButtonPassthroughKey : BooleanPreferenceKey("volume_button_passthrough", false)
    data object AppThemeKey : ObjectPreferenceKey<AppTheme>(
        "app_theme", AppTheme.System,
        fromStringPreference = {
            val defaultValue = AppTheme.System

            when (it) {
                AppTheme.System.key -> AppTheme.System
                AppTheme.DarkMode.key -> AppTheme.DarkMode
                AppTheme.LightMode.key -> AppTheme.LightMode
                else -> defaultValue
            }
        },
        toStringPreference = { it.key }
    )

    data object DynamicColorKey : BooleanPreferenceKey("dynamic_color", false)
    data object LoopbackMode : BooleanPreferenceKey("loopback_mode", false)
    data object ExperimentalMode : BooleanPreferenceKey("experimental_mode", false)
    data object TouchpadFullscreenInLandscape : BooleanPreferenceKey("touchpad_fullscreen_in_landscape", false)
    data object UsbGadgetPathPref : ObjectPreferenceKey<UsbGadgetPath>(
        "usb_gadget_path", UsbGadgetPath("/config/usb_gadget/g1"),
        fromStringPreference = { UsbGadgetPath(it) },
        toStringPreference = { it.path }
    )

    data object KeyboardCharacterDevicePath : ObjectPreferenceKey<KeyboardDevicePath>(
        "keyboard_character_device_path", CharacterDeviceManager.Companion.DevicePaths.DEFAULT_KEYBOARD_DEVICE_PATH,
        fromStringPreference = { KeyboardDevicePath(it) },
        toStringPreference = { it.path }
    )

    data object TouchpadCharacterDevicePath : ObjectPreferenceKey<TouchpadDevicePath>(
        "touchpad_character_device_path", CharacterDeviceManager.Companion.DevicePaths.DEFAULT_TOUCHPAD_DEVICE_PATH,
        fromStringPreference = { TouchpadDevicePath(it) },
        toStringPreference = { it.path }
    )

    data object CreateNewGadgetForFunctions : BooleanPreferenceKey("create_new_gadget_for_functions", false)
}

sealed class SealedString(val key: String, @StringRes val id: Int)

sealed class AppTheme(key: String, @StringRes id: Int) : SealedString(key, id) {
    data object System : AppTheme("system", R.string.app_theme_system)
    data object LightMode : AppTheme("light", R.string.app_theme_light_mode)
    data object DarkMode : AppTheme("dark", R.string.app_theme_dark_mode)

    companion object {
        val values: List<AppTheme>
            get() = listOf(
                System,
                LightMode,
                DarkMode
            )
    }
}

data class UserPreferences(
    val isOnboardingDone: Boolean,
    val clearManualInput: Boolean,
    val isVolumeButtonPassthroughEnabled: Boolean,
    val appTheme: AppTheme,
    val isDynamicColorEnabled: Boolean,
    val isLoopbackModeEnabled: Boolean,
    val isTouchpadFullscreenInLandscape: Boolean,
    val isExperimentalModeEnabled: Boolean,
    val usbGadgetPath: UsbGadgetPath,
    val keyboardCharacterDevicePath: KeyboardDevicePath,
    val touchpadCharacterDevicePath: TouchpadDevicePath,
    val createNewGadgetForFunctions: Boolean,
)

class UserPreferencesRepository private constructor(application: Application) {
    private val sharedPreferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)

    private val _userPreferencesFlow = MutableStateFlow(userPreferences)
    val userPreferencesFlow: StateFlow<UserPreferences> = _userPreferencesFlow

    private fun <T> PreferenceKey<T>.getValue() = this.getValue(sharedPreferences)
    private fun <T> PreferenceKey<T>.setValue(value: T) = this.setValue(sharedPreferences, value)
    private fun <T> PreferenceKey<T>.resetToDefault() = this.resetToDefault(sharedPreferences)

    private val userPreferences: UserPreferences
        get() {
            return UserPreferences(
                isOnboardingDone = AppPreference.OnboardingDoneKey.getValue(),
                clearManualInput = AppPreference.ClearManualInputKey.getValue(),
                isVolumeButtonPassthroughEnabled = AppPreference.VolumeButtonPassthroughKey.getValue(),
                appTheme = AppPreference.AppThemeKey.getValue(),
                isDynamicColorEnabled = AppPreference.DynamicColorKey.getValue(),
                isLoopbackModeEnabled = AppPreference.LoopbackMode.getValue(),
                isTouchpadFullscreenInLandscape = AppPreference.TouchpadFullscreenInLandscape.getValue(),
                isExperimentalModeEnabled = AppPreference.ExperimentalMode.getValue(),
                usbGadgetPath = AppPreference.UsbGadgetPathPref.getValue(),
                keyboardCharacterDevicePath = AppPreference.KeyboardCharacterDevicePath.getValue(),
                touchpadCharacterDevicePath = AppPreference.TouchpadCharacterDevicePath.getValue(),
                createNewGadgetForFunctions = AppPreference.CreateNewGadgetForFunctions.getValue()
            )
        }

    fun <T> getPreference(key: PreferenceKey<T>): T =
        key.getValue()

    fun <T> setPreference(key: PreferenceKey<T>, value: T) {
        key.setValue(value)
        _userPreferencesFlow.update { userPreferences }
    }

    fun <T> resetPreferenceToDefault(key: PreferenceKey<T>) {
        key.resetToDefault()
        _userPreferencesFlow.update { userPreferences }
    }

    companion object {
        @Volatile
        private var INSTANCE: UserPreferencesRepository? = null

        fun getInstance(application: Application): UserPreferencesRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE?.let {
                    return it
                }

                val instance = UserPreferencesRepository(application)
                INSTANCE = instance
                instance
            }
        }
    }
}
