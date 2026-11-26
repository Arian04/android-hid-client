package me.arianb.usb_hid_client.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import me.arianb.usb_hid_client.R
import me.arianb.usb_hid_client.settings.AppSettings.AppThemePreference
import me.arianb.usb_hid_client.settings.AppSettings.ClearManualInputOnSend
import me.arianb.usb_hid_client.settings.AppSettings.DynamicColors
import me.arianb.usb_hid_client.settings.AppSettings.EnablePrecisionTouchpad
import me.arianb.usb_hid_client.settings.AppSettings.EnableScriptingSupport
import me.arianb.usb_hid_client.settings.AppSettings.ExperimentalMode
import me.arianb.usb_hid_client.settings.AppSettings.GadgetConfigScreenNavigation
import me.arianb.usb_hid_client.settings.AppSettings.MediaKeyPassthrough
import me.arianb.usb_hid_client.settings.AppSettings.TouchpadFullscreenInLandscape
import me.arianb.usb_hid_client.settings.AppSettings.TouchpadLoopbackMode
import me.arianb.usb_hid_client.ui.theme.PaddingNormal
import me.arianb.usb_hid_client.ui.theme.isDynamicColorAvailable
import me.arianb.usb_hid_client.ui.utils.BasicPage
import me.arianb.usb_hid_client.ui.utils.DarkLightModePreviews
import me.arianb.usb_hid_client.ui.utils.Experimental
import me.arianb.usb_hid_client.ui.utils.SimpleNavTopBar

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
            EnableScriptingSupport()
        }
        PreferenceCategory(
            title = stringResource(R.string.touchpad_label),
        ) {
            TouchpadFullscreenInLandscape()
            TouchpadLoopbackMode()
            EnablePrecisionTouchpad()
        }

        // only set `showDivider = false` for the last category.
        // haven't found a nice way to do that implicitly yet.

        PreferenceCategory(
            title = stringResource(R.string.misc_header),
            showDivider = false
        ) {
            ExperimentalMode()
            Experimental {
                GadgetConfigScreenNavigation()
            }
        }
    }
}

@Composable
private fun SettingsTopBar() {
    SimpleNavTopBar(
        title = stringResource(R.string.settings)
    )
}

// Specializations of the generic preference composable "helper" functions
private object AppSettings {
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
    fun EnablePrecisionTouchpad() {
        SwitchPreference(
            title = stringResource(R.string.enable_precision_touchpad_title),
            summary = stringResource(R.string.enable_precision_touchpad_summary),
            preference = AppPreference.EnablePrecisionTouchpad
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
    fun GadgetConfigScreenNavigation() {
        val navigator = LocalNavigator.currentOrThrow
        OnClickPreference(
            title = stringResource(R.string.gadget_config_title),
            summary = stringResource(R.string.gadget_config_summary),
            onClick = {
                if (navigator.lastItem !is GadgetConfigScreen) {
                    navigator.push(GadgetConfigScreen())
                }
            },
            trailingContent = {
                Icon(
                    imageVector = Icons.AutoMirrored.Default.KeyboardArrowRight,
                    contentDescription = null
                )
            }
        )
    }

    @Composable
    fun EnableScriptingSupport() {
        SwitchPreference(
            title = stringResource(R.string.enable_scripting_support_title),
            summary = stringResource(R.string.enable_scripting_support_summary),
            preference = AppPreference.EnableScriptingSupport
        )
    }
}

@DarkLightModePreviews
@Composable
private fun SettingsScreenPreview() {
    Navigator(SettingsScreen())
}
