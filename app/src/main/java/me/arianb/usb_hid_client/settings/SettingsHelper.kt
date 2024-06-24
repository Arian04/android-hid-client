package me.arianb.usb_hid_client.settings

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.material3.ListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.topjohnwu.superuser.Shell
import me.arianb.usb_hid_client.BuildConfig
import me.arianb.usb_hid_client.ui.theme.LabeledCategory
import timber.log.Timber
import java.io.IOException


// This is basically just an alias now
@Composable
fun PreferenceCategory(
    title: String,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
    preferences: @Composable () -> Unit
) {
    LabeledCategory(title, modifier, showDivider) {
        preferences()
    }
}

@Composable
fun SwitchPreference(
    title: String,
    summary: String? = null,
    key: String,
    defaultValue: Boolean = false,
    viewModel: SettingsViewModel = viewModel()
) {
    var isChecked by remember { mutableStateOf(viewModel.getBoolean(key, defaultValue)) }

    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            if (summary != null) {
                Text(summary)
            }
        },
        trailingContent = {
            Switch(
                checked = isChecked,
                onCheckedChange = {
                    isChecked = it
                    viewModel.putBoolean(key, isChecked)
                },
            )
        }
    )
}

@Composable
fun OnClickPreference(
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        modifier = Modifier.clickable { onClick() }
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