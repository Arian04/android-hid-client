package me.arianb.usb_hid_client.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import me.arianb.usb_hid_client.R
import me.arianb.usb_hid_client.settings.AppSettings.AppThemePreference
import me.arianb.usb_hid_client.settings.AppSettings.ClearManualInputOnSend
import me.arianb.usb_hid_client.settings.AppSettings.DynamicColors
import me.arianb.usb_hid_client.settings.AppSettings.ExperimentalMode
import me.arianb.usb_hid_client.settings.AppSettings.KeyboardCharacterDevicePath
import me.arianb.usb_hid_client.settings.AppSettings.MediaKeyPassthrough
import me.arianb.usb_hid_client.settings.AppSettings.PreferenceCategory
import me.arianb.usb_hid_client.settings.AppSettings.TouchpadCharacterDevicePath
import me.arianb.usb_hid_client.settings.AppSettings.TouchpadFullscreenInLandscape
import me.arianb.usb_hid_client.settings.AppSettings.TouchpadLoopbackMode
import me.arianb.usb_hid_client.settings.AppSettings.UsbGadgetPath
import me.arianb.usb_hid_client.ui.theme.PaddingNormal
import me.arianb.usb_hid_client.ui.theme.isDynamicColorAvailable
import me.arianb.usb_hid_client.ui.utils.BasicPage
import me.arianb.usb_hid_client.ui.utils.DarkLightModePreviews
import me.arianb.usb_hid_client.ui.utils.Experimental
import me.arianb.usb_hid_client.ui.utils.SimpleNavTopBar
import me.arianb.usb_hid_client.ui.utils.isExperimentalModeEnabled

class SettingsScreen : Screen {
    @Composable
    override fun Content() {
        SettingsPage()
    }
}

@Composable
fun SettingsPage() {
    val padding = PaddingNormal

    BasicPage(
        topBar = { SettingsTopBar() },
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(padding, Alignment.Top),
        scrollable = true
    ) {
        PreferenceCategory(
            title = stringResource(R.string.theme_header),
        ) {
            AppThemePreference()

            if (isDynamicColorAvailable()) {
                DynamicColors()
            }
        }
        PreferenceCategory(
            title = stringResource(R.string.direct_input),
        ) {
            MediaKeyPassthrough()
        }
        PreferenceCategory(
            title = stringResource(R.string.manual_input),
        ) {
            ClearManualInputOnSend()
        }
        PreferenceCategory(
            title = stringResource(R.string.touchpad_label),
        ) {
            TouchpadFullscreenInLandscape()
            TouchpadLoopbackMode()
        }

        // only set `showDivider = false` for the last category.
        // haven't found a nice way to do that implicitly yet.

        PreferenceCategory(
            title = stringResource(R.string.misc_header),
            showDivider = isExperimentalModeEnabled()
        ) {
            ExperimentalMode()
        }
        Experimental {
            PreferenceCategory(
                title = stringResource(R.string.device_specific_quirks_header),
                showDivider = false
            ) {
                UsbGadgetPath()
                KeyboardCharacterDevicePath()
                TouchpadCharacterDevicePath()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTopBar() {
    SimpleNavTopBar(
        title = stringResource(R.string.settings)
    )
}

// Specializations of the generic preference composable "helper" functions
private object AppSettings {
    @Composable
    fun PreferenceCategory(
        title: String,
        showDivider: Boolean = true,
        preferences: @Composable (() -> Unit)
    ) {
        val paddingModifier = Modifier.padding(horizontal = PaddingNormal)

        PreferenceCategory(
            title = title,
            modifier = paddingModifier,
            showDivider = showDivider,
            preferences = preferences,
        )
    }

    @Composable
    fun AppThemePreference(
        enabled: Boolean = true,
        settingsViewModel: SettingsViewModel = viewModel()
    ) {
        val preferencesState by settingsViewModel.userPreferencesFlow.collectAsState()

        val selectedTheme = preferencesState.appTheme
        val options = AppTheme.values
        BasicListPreference(
            title = stringResource(R.string.app_theme_title),
            options = options,
            enabled = enabled,
            selected = selectedTheme,
            onPreferenceClicked = { thisAppTheme ->
                settingsViewModel.setPreference(AppPreference.AppThemeKey, thisAppTheme)
            }
        )
    }

    @Composable
    fun TouchpadLoopbackMode() {
        SwitchPreference(
            title = stringResource(R.string.touchpad_loopback_mode_title),
            summary = stringResource(R.string.touchpad_loopback_mode_summary),
            preference = AppPreference.LoopbackMode
        )
    }

    @Composable
    fun DynamicColors() {
        SwitchPreference(
            title = stringResource(R.string.dynamic_colors_title),
            preference = AppPreference.DynamicColorKey
        )
    }

    @Composable
    fun MediaKeyPassthrough() {
        SwitchPreference(
            title = stringResource(R.string.volume_button_passthrough_title),
            summary = stringResource(R.string.volume_button_passthrough_summary),
            preference = AppPreference.VolumeButtonPassthroughKey
        )
    }

    @Composable
    fun ClearManualInputOnSend() {
        SwitchPreference(
            title = stringResource(R.string.clear_manual_input_title),
            preference = AppPreference.ClearManualInputKey
        )
    }

    @Composable
    fun TouchpadFullscreenInLandscape() {
        SwitchPreference(
            title = stringResource(R.string.touchpad_fullscreen_in_landscape_title),
            summary = stringResource(R.string.touchpad_fullscreen_in_landscape_summary),
            preference = AppPreference.TouchpadFullscreenInLandscape
        )
    }

    @Composable
    fun ExperimentalMode() {
        SwitchPreference(
            title = stringResource(R.string.experimental_mode_title),
            summary = stringResource(R.string.experimental_mode_summary),
            preference = AppPreference.ExperimentalMode
        )
    }

    @Composable
    fun UsbGadgetPath() {
        TextDialogPreference(
            title = stringResource(R.string.usb_gadget_path_title),
            preference = AppPreference.UsbGadgetPathPref,
            property = UserPreferences::usbGadgetPath
        )
    }

    @Composable
    fun KeyboardCharacterDevicePath() {
        TextDialogPreference(
            title = stringResource(R.string.keyboard_character_device_path),
            preference = AppPreference.KeyboardCharacterDevicePath,
            property = UserPreferences::keyboardCharacterDevicePath
        )
    }

    @Composable
    fun TouchpadCharacterDevicePath() {
        TextDialogPreference(
            title = stringResource(R.string.touchpad_character_device_path),
            preference = AppPreference.TouchpadCharacterDevicePath,
            property = UserPreferences::touchpadCharacterDevicePath
        )
    }
}

@DarkLightModePreviews
@Composable
private fun SettingsScreenPreview() {
    Navigator(SettingsScreen())
}
