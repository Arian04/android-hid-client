package me.arianb.usb_hid_client.settings

import android.app.Application
import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import me.arianb.usb_hid_client.R

// keys
// TODO: refactor to handle it in a similar way to AppTheme to ensure it's impossible to pass an unknown key to my
//       preference getters/setters here.
const val ONBOARDING_DONE_KEY = "onboarding_done"
const val CLEAR_MANUAL_INPUT_KEY = "clear_manual_input"
const val VOLUME_BUTTON_PASSTHROUGH_KEY = "volume_button_passthrough"
const val DEBUG_MODE_KEY = "debug_mode"
const val APP_THEME_KEY = "app_theme"
const val DYNAMIC_COLOR_KEY = "dynamic_color"

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
    val isDebugModeEnabled: Boolean,
    val appTheme: AppTheme,
    val isDynamicColorEnabled: Boolean,
)

class UserPreferencesRepository private constructor(application: Application) {
    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)

    private val _userPreferencesFlow = MutableStateFlow(userPreferences)
    val userPreferencesFlow: StateFlow<UserPreferences> = _userPreferencesFlow

    private val userPreferences: UserPreferences
        get() {
            return UserPreferences(
                isOnboardingDone = getBoolean(ONBOARDING_DONE_KEY, false),
                clearManualInput = getBoolean(CLEAR_MANUAL_INPUT_KEY, false),
                isVolumeButtonPassthroughEnabled = getBoolean(VOLUME_BUTTON_PASSTHROUGH_KEY, false),
                isDebugModeEnabled = getBoolean(DEBUG_MODE_KEY, false),
                appTheme = getAppTheme(),
                isDynamicColorEnabled = getBoolean(DYNAMIC_COLOR_KEY, false),
            )
        }

    // TODO: maybe generalize this code so I can use it for any SealedString subclasses I come up with later on?
    private fun getAppTheme(): AppTheme {
        val key = APP_THEME_KEY
        val defaultValue = AppTheme.System

        val stringPreference = sharedPreferences.getString(key, defaultValue.key)
        return when (stringPreference) {
            AppTheme.System.key -> AppTheme.System
            AppTheme.DarkMode.key -> AppTheme.DarkMode
            AppTheme.LightMode.key -> AppTheme.LightMode
            else -> defaultValue
        }
    }

    fun putAppTheme(value: AppTheme) {
        val key = APP_THEME_KEY

        editAndUpdate {
            putString(key, value.key)
        }
    }

    fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return sharedPreferences.getBoolean(key, defaultValue)
    }

    fun putBoolean(key: String, value: Boolean) {
        editAndUpdate {
            putBoolean(key, value)
        }
    }

    private fun editAndUpdate(commit: Boolean = false, action: SharedPreferences.Editor.() -> Unit) {
        sharedPreferences.edit(commit = commit, action = action)
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
