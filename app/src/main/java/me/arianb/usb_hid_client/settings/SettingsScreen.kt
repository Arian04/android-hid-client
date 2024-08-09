package me.arianb.usb_hid_client.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import me.arianb.usb_hid_client.R
import me.arianb.usb_hid_client.ui.theme.PaddingNormal
import me.arianb.usb_hid_client.ui.theme.isDynamicColorAvailable
import me.arianb.usb_hid_client.ui.utils.BasicPage
import me.arianb.usb_hid_client.ui.utils.DarkLightModePreviews
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
    val paddingModifier = Modifier.padding(horizontal = padding)

    BasicPage(
        topBar = { SettingsTopBar() },
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(padding, Alignment.Top),
        scrollable = true
    ) {
        PreferenceCategory(
            title = stringResource(R.string.theme_header),
            modifier = paddingModifier
        ) {
            AppThemePreference(
                title = stringResource(R.string.app_theme_title),
            )
            if (isDynamicColorAvailable()) {
                SwitchPreference(
                    title = stringResource(R.string.dynamic_colors_title),
                    key = PreferenceKey.DynamicColorKey
                )
            }
        }
        PreferenceCategory(
            title = stringResource(R.string.direct_input),
            modifier = paddingModifier
        ) {
            SwitchPreference(
                title = stringResource(R.string.volume_button_passthrough_title),
                summary = stringResource(R.string.volume_button_passthrough_summary),
                key = PreferenceKey.VolumeButtonPassthroughKey
            )
        }
        PreferenceCategory(
            title = stringResource(R.string.manual_input),
            modifier = paddingModifier,
        ) {
            SwitchPreference(
                title = stringResource(R.string.clear_manual_input_title),
                key = PreferenceKey.ClearManualInputKey
            )
        }
        PreferenceCategory(
            title = stringResource(R.string.touchpad_label),
            modifier = paddingModifier,
            showDivider = false
        ) {
            SwitchPreference(
                title = stringResource(R.string.touchpad_loopback_mode_title),
                summary = stringResource(R.string.touchpad_loopback_mode_summary),
                key = PreferenceKey.LoopbackMode
            )
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

@DarkLightModePreviews
@Composable
private fun SettingsScreenPreview() {
    Navigator(SettingsScreen())
}
