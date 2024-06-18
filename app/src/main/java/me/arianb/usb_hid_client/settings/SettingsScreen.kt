package me.arianb.usb_hid_client.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import com.topjohnwu.superuser.Shell
import me.arianb.usb_hid_client.BuildConfig
import me.arianb.usb_hid_client.R
import me.arianb.usb_hid_client.ui.theme.BasicPage
import me.arianb.usb_hid_client.ui.theme.DarkLightModePreviews
import me.arianb.usb_hid_client.ui.theme.SimpleNavTopBar
import timber.log.Timber
import java.io.IOException

// TODO: make this scrollable

class SettingsScreen : Screen {
    @Composable
    override fun Content() {
        SettingsPage()
    }
}

@Composable
fun SettingsPage() {
    val padding = 16.dp
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        // If the user doesn't choose a location to save the file, don't continue
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult

        Timber.d("selected file URI: %s", uri)
        saveLogFile(context, uri)
    }

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

    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTopBar() {
    SimpleNavTopBar(
        title = stringResource(R.string.settings)
    )
}

fun saveLogFile(context: Context, uri: Uri) {
    try {
        val stringBuilder = StringBuilder()
        var command: String
        if (Shell.getShell().isRoot) {
            command = String.format(
                "logcat -e '%s' -t 1000",
                BuildConfig.APPLICATION_ID
            )
            stringBuilder.append(
                getCommandInLogFormatString(
                    command,
                    "Logcat"
                )
            )
            command = "ls -lAhZ /config/usb_gadget"
            stringBuilder.append(
                getCommandInLogFormatString(
                    command,
                    "Gadgets Directory"
                )
            )
            command =
                "echo KERNEL_VERSION=$(uname -r |cut -d '-' -f1 ) && (gunzip -c /proc/config.gz | grep -i configfs | sed 's/# //; s/ is not set/=NOT_SET/')"
            stringBuilder.append(
                getCommandInLogFormatString(
                    command,
                    "Kernel Config"
                )
            )
        } else {
            stringBuilder.append("Could not create root shell. Was the app given root permissions?")
                .append("\n")
        }
        Timber.d(stringBuilder.toString())

        // Write out file
        context.contentResolver.openOutputStream(uri).use { outputStream ->
            if (outputStream == null) {
                Timber.e("Failed to open output stream for writing log file.")
                return
            }
            outputStream.write(stringBuilder.toString().toByteArray())
        }
        Timber.d("Successfully exported logs")
    } catch (e: IOException) {
        Timber.e(e)
        Timber.e("Error occurred while exporting logs")
    }
}

fun getCommandInLogFormatString(command: String, title: String): String {
    val logStringBuilder = StringBuilder()
    val halfDivider = "------------------------------"
    val divider = halfDivider + halfDivider
    val commandResult = Shell.cmd(command).exec()
    val commandResultMultiline = java.lang.String.join("\n", commandResult.out)

    logStringBuilder.append(String.format("%s %s %s", halfDivider, title, halfDivider)).append("\n")
    logStringBuilder.append(commandResultMultiline).append("\n")
    logStringBuilder.append(divider + "\n")
    return logStringBuilder.toString()
}

@DarkLightModePreviews
@Composable
fun SettingsScreenPreview() {
    Navigator(SettingsScreen())
}
