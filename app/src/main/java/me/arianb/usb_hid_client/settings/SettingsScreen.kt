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
import me.arianb.usb_hid_client.ui.theme.BasicPage
import me.arianb.usb_hid_client.ui.theme.DarkLightModePreviews
import me.arianb.usb_hid_client.ui.theme.PaddingNormal
import me.arianb.usb_hid_client.ui.theme.SimpleNavTopBar
import timber.log.Timber

// TODO: make this scrollable

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
//        paddingAll = padding,
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(padding, Alignment.Top),
    ) {
        PreferenceCategory(
            title = stringResource(R.string.misc_header),
            modifier = Modifier.padding(horizontal = padding)
        ) {
            SwitchPreference(
                title = stringResource(R.string.clear_manual_input_title),
                key = CLEAR_MANUAL_INPUT_KEY
            )
            SwitchPreference(
                title = stringResource(R.string.volume_button_passthrough_title),
                summary = stringResource(R.string.volume_button_passthrough_summary),
                key = VOLUME_BUTTON_PASSTHROUGH_KEY
            )
        }
        PreferenceCategory(
            title = stringResource(R.string.debug_header),
            modifier = Modifier.padding(horizontal = padding),
            showDivider = false
        ) {
            SwitchPreference(
                title = stringResource(R.string.debug_mode_title),
                summary = stringResource(R.string.debug_mode_summary),
                key = DEBUG_MODE_KEY
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
fun SettingsTopBar() {
    SimpleNavTopBar(
        title = stringResource(R.string.settings)
    )
}

@DarkLightModePreviews
@Composable
fun SettingsScreenPreview() {
    Navigator(SettingsScreen())
}
