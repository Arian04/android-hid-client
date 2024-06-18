package me.arianb.usb_hid_client.settings

import android.app.Application
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

// keys
const val ONBOARDING_DONE_KEY = "onboarding_done"
const val CLEAR_MANUAL_INPUT_KEY = "clear_manual_input"
const val VOLUME_BUTTON_PASSTHROUGH_KEY = "volume_button_passthrough"
const val DEBUG_MODE_KEY = "debug_mode"

data class UserPreferences(
    val isOnboardingDone: Boolean,
    val clearManualInput: Boolean,
    val isVolumeButtonPassthroughEnabled: Boolean,
    val isDebugModeEnabled: Boolean,
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
                isDebugModeEnabled = getBoolean(DEBUG_MODE_KEY, false)
            )
        }

    fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return sharedPreferences.getBoolean(key, defaultValue)
    }

    fun putBoolean(key: String, value: Boolean) {
        sharedPreferences.edit {
            putBoolean(key, value)
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
