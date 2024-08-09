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
sealed class PreferenceKey(val key: String) {
    data object OnboardingDoneKey : PreferenceKey("onboarding_done")
    data object ClearManualInputKey : PreferenceKey("clear_manual_input")
    data object VolumeButtonPassthroughKey : PreferenceKey("volume_button_passthrough")
    data object AppThemeKey : PreferenceKey("app_theme")
    data object DynamicColorKey : PreferenceKey("dynamic_color")
    data object LoopbackMode : PreferenceKey("loopback_mode")
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
)

class UserPreferencesRepository private constructor(application: Application) {
    private val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(application)

    private val _userPreferencesFlow = MutableStateFlow(userPreferences)
    val userPreferencesFlow: StateFlow<UserPreferences> = _userPreferencesFlow

    private val userPreferences: UserPreferences
        get() {
            return UserPreferences(
                isOnboardingDone = getBoolean(PreferenceKey.OnboardingDoneKey, false),
                clearManualInput = getBoolean(PreferenceKey.ClearManualInputKey, false),
                isVolumeButtonPassthroughEnabled = getBoolean(PreferenceKey.VolumeButtonPassthroughKey, false),
                appTheme = getAppTheme(),
                isDynamicColorEnabled = getBoolean(PreferenceKey.DynamicColorKey, false),
                isLoopbackModeEnabled = getBoolean(PreferenceKey.LoopbackMode, false),
            )
        }

    // TODO: maybe generalize this code so I can use it for any SealedString subclasses I come up with later on?
    private fun getAppTheme(): AppTheme {
        val key = PreferenceKey.AppThemeKey.key
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
        val key = PreferenceKey.AppThemeKey.key

        editAndUpdate {
            this.putString(key, value.key)
        }
    }

    fun getBoolean(key: PreferenceKey, defaultValue: Boolean): Boolean {
        return sharedPreferences.getBoolean(key.key, defaultValue)
    }

    fun putBoolean(key: PreferenceKey, value: Boolean) {
        editAndUpdate {
            this.putBoolean(key.key, value)
        }
    }

    private inline fun editAndUpdate(commit: Boolean = false, action: SharedPreferences.Editor.() -> Unit) {
        sharedPreferences.edit(commit = commit) {
            action()
        }
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
