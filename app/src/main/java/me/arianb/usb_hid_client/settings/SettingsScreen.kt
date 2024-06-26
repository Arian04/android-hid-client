package me.arianb.usb_hid_client.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import me.arianb.usb_hid_client.BuildConfig
import me.arianb.usb_hid_client.R
import me.arianb.usb_hid_client.ui.theme.PaddingNormal
import me.arianb.usb_hid_client.ui.theme.isDynamicColorAvailable
import me.arianb.usb_hid_client.ui.utils.BasicPage
import me.arianb.usb_hid_client.ui.utils.DarkLightModePreviews
import me.arianb.usb_hid_client.ui.utils.SimpleNavTopBar
import timber.log.Timber

class SettingsScreen : Screen {
    @Composable
    override fun Content() {
        SettingsPage()
    }
}

@Composable
private fun SettingsPage() {
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
            ListPreference(
                title = stringResource(R.string.app_theme_title),
                options = AppTheme.values
            )
            if (isDynamicColorAvailable()) {
                SwitchPreference(
                    title = stringResource(R.string.dynamic_colors_title),
                    key = PreferenceKey.DynamicColorKey
                )
            }
        }
        PreferenceCategory(
            title = stringResource(R.string.misc_header),
            modifier = paddingModifier
        ) {
            SwitchPreference(
                title = stringResource(R.string.clear_manual_input_title),
                key = PreferenceKey.ClearManualInputKey
            )
            SwitchPreference(
                title = stringResource(R.string.volume_button_passthrough_title),
                summary = stringResource(R.string.volume_button_passthrough_summary),
                key = PreferenceKey.VolumeButtonPassthroughKey
            )
        }
        PreferenceCategory(
            title = stringResource(R.string.debug_header),
            modifier = paddingModifier,
            showDivider = false
        ) {
            SwitchPreference(
                title = stringResource(R.string.debug_mode_title),
                summary = stringResource(R.string.debug_mode_summary),
                key = PreferenceKey.DebugModeKey
            )
            ExportLogsPreferenceButton()
        }
    }
}

@Composable
fun ExportLogsPreferenceButton() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        // If the user doesn't choose a location to save the file, don't continue
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult

        Timber.d("selected file URI: %s", uri)
        saveLogFile(context, uri)
    }

    OnClickPreference(
        title = stringResource(R.string.export_debug_logs_btn_title),
        summary = stringResource(R.string.export_debug_logs_btn_summary),
        onClick = {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/plain"

                val unixTime = System.currentTimeMillis() / 1000
                val filename = "debug_log_${BuildConfig.APPLICATION_ID}_${unixTime}.txt"
                putExtra(Intent.EXTRA_TITLE, filename)
            }

            launcher.launch(intent)
        }
    )
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
