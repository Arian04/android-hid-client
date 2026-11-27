package me.arianb.usb_hid_client.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import me.arianb.usb_hid_client.hid_utils.GadgetViewModel
import me.arianb.usb_hid_client.settings.GadgetConfigSettings.FullyDisableGadgetDuringConfiguration
import me.arianb.usb_hid_client.settings.GadgetConfigSettings.KeyboardCharacterDevicePath
import me.arianb.usb_hid_client.settings.GadgetConfigSettings.TouchpadCharacterDevicePath
import me.arianb.usb_hid_client.settings.GadgetConfigSettings.UsbGadgetPath
import me.arianb.usb_hid_client.ui.theme.PaddingNormal
import me.arianb.usb_hid_client.ui.utils.BasicPage
import me.arianb.usb_hid_client.ui.utils.DarkLightModePreviews
import me.arianb.usb_hid_client.ui.utils.SimpleNavTopBar
import me.arianb.usb_hid_client.ui.utils.activityViewModel
import me.arianb.usb_hid_client.ui.utils.rememberSnackbarHostState

class GadgetConfigScreen : Screen {
    @Composable
    override fun Content() {
        GadgetConfigPage()
    }
}

@Composable
fun GadgetConfigPage(
    gadgetViewModel: GadgetViewModel = activityViewModel(),
) {
    BasicPage(
        topBar = { GadgetConfigTopBar() },
        snackbarHostState = rememberSnackbarHostState(gadgetViewModel.snackbarFlow),
        scrollable = true
    ) {
        RecreateGadgetBanner()

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

@Composable
private fun ColumnScope.RecreateGadgetBanner(
    gadgetViewModel: GadgetViewModel = activityViewModel(),
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val userPreferences by settingsViewModel.userPreferencesFlow.collectAsState()
    val gadgetState by gadgetViewModel.gadgetState.collectAsState()

    val currentGadgetPrefs = GadgetUserPreferences.fromUserPreferences(userPreferences)
    val needsRecreate = gadgetState.needsRecreate(currentGadgetPrefs)

    AnimatedVisibility(visible = needsRecreate) {
        RecreateGadgetCard(
            enabled = !gadgetState.isOperationInProgress,
            onRecreateClick = { gadgetViewModel.recreateCharacterDevices() }
        )
    }
}

@Composable
private fun RecreateGadgetCard(
    enabled: Boolean,
    onRecreateClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PaddingNormal),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(PaddingNormal),
            verticalArrangement = Arrangement.spacedBy(PaddingNormal)
        ) {
            Text(
                text = stringResource(R.string.gadget_config_recreate_notice),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Button(
                onClick = onRecreateClick,
                enabled = enabled,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(stringResource(R.string.gadget_config_recreate_button))
            }
        }
    }
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
