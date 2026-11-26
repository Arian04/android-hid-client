package me.arianb.usb_hid_client.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import me.arianb.usb_hid_client.R
import me.arianb.usb_hid_client.settings.GadgetConfigSettings.FullyDisableGadgetDuringConfiguration
import me.arianb.usb_hid_client.settings.GadgetConfigSettings.KeyboardCharacterDevicePath
import me.arianb.usb_hid_client.settings.GadgetConfigSettings.TouchpadCharacterDevicePath
import me.arianb.usb_hid_client.settings.GadgetConfigSettings.UsbGadgetPath
import me.arianb.usb_hid_client.ui.theme.PaddingNormal
import me.arianb.usb_hid_client.ui.utils.BasicPage
import me.arianb.usb_hid_client.ui.utils.DarkLightModePreviews
import me.arianb.usb_hid_client.ui.utils.SimpleNavTopBar

class GadgetConfigScreen : Screen {
    @Composable
    override fun Content() {
        GadgetConfigPage()
    }
}

@Composable
fun GadgetConfigPage() {
    BasicPage(
        topBar = { GadgetConfigTopBar() },
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(PaddingNormal, Alignment.Top),
        scrollable = true
    ) {
        PreferenceCategory(
            title = stringResource(R.string.device_specific_quirks_header),
            showDivider = false
        ) {
            FullyDisableGadgetDuringConfiguration()
            UsbGadgetPath()
            KeyboardCharacterDevicePath()
            TouchpadCharacterDevicePath()
        }
    }
}

@Composable
private fun GadgetConfigTopBar() {
    SimpleNavTopBar(
        title = stringResource(R.string.gadget_config_title)
    )
}

private object GadgetConfigSettings {
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

    @Composable
    fun FullyDisableGadgetDuringConfiguration() {
        SwitchPreference(
            title = stringResource(R.string.disable_gadget_functions_during_config),
            summary = stringResource(R.string.disable_gadget_functions_during_config_summary),
            preference = AppPreference.DisableGadgetFunctionsDuringConfiguration
        )
    }
}

@DarkLightModePreviews
@Composable
private fun GadgetConfigScreenPreview() {
    Navigator(GadgetConfigScreen())
}
