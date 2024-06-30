package me.arianb.usb_hid_client.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonColors
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import me.arianb.usb_hid_client.BuildConfig
import me.arianb.usb_hid_client.LogBuffer
import me.arianb.usb_hid_client.R
import me.arianb.usb_hid_client.TroubleshootingInfo
import me.arianb.usb_hid_client.detectIssues
import me.arianb.usb_hid_client.ui.utils.LabeledCategory
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
    key: PreferenceKey,
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
fun ListPreference(
    title: String,
    options: List<AppTheme>,
    enabled: Boolean = true,
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val preferencesState by settingsViewModel.userPreferencesFlow.collectAsState()

    val selectedTheme = preferencesState.appTheme

    BasicListPreference(
        title = title,
        options = options,
        enabled = enabled,
        selected = selectedTheme,
        onPreferenceClicked = { thisSealedString ->
            // Not sure what situation would cause this cast to fail, but it's better to be safe
            val thisTheme = thisSealedString as? AppTheme
            if (thisTheme != null) {
                settingsViewModel.putAppTheme(thisTheme)
            } else {
                Timber.wtf("cast from SealedString to AppTheme failed for: %s", thisSealedString.key)
            }
        }
    )
}

@Composable
fun BasicListPreference(
    title: String,
    options: List<SealedString>,
    selected: SealedString,
    enabled: Boolean = true,
    onPreferenceClicked: ((thisSealedString: SealedString) -> Unit),
) {
    var isShowingAlert by remember { mutableStateOf(false) }

    val selectedThemeLabel = stringResource(selected.id)

    OnClickPreference(
        title = title,
        summary = selectedThemeLabel,
        enabled = enabled,
        onClick = { isShowingAlert = true },
        trailingContent = {
            if (isShowingAlert) {
                AlertDialog(
                    title = { Text(title) },
                    onDismissRequest = { isShowingAlert = false },
                    confirmButton = {
                        TextButton(onClick = { isShowingAlert = false }) {
                            Text("Done")
                        }
                    },
                    text = {
                        Column {
                            for (thisSealedString in options) {
                                val isSelected = selected == thisSealedString
                                val label = stringResource(thisSealedString.id)
                                RadioButtonHelper(
                                    label = label,
                                    selected = isSelected,
                                    onClick = { onPreferenceClicked(thisSealedString) }
                                )
                            }
                        }
                    }
                )
            }
        }
    )
}

@Composable
private fun RadioButtonHelper(
    label: String,
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: RadioButtonColors = RadioButtonDefaults.colors(),
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = colors,
        )
        Text(label)
    }
}

@Composable
fun OnClickPreference(
    title: String,
    summary: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    trailingContent: @Composable (() -> Unit)? = null
) {
    val defaults = ListItemDefaults.colors()
    val listItemColors: ListItemColors = if (enabled) {
        defaults
    } else {
        ListItemDefaults.colors(
            headlineColor = defaults.disabledHeadlineColor,
            leadingIconColor = defaults.disabledLeadingIconColor,
            trailingIconColor = defaults.disabledTrailingIconColor,
            supportingColor = defaults.disabledHeadlineColor
        )
    }
    val modifier = if (enabled) {
        Modifier.clickable { onClick() }
    } else {
        Modifier
    }

    ListItem(
        modifier = modifier,
        colors = listItemColors,
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = trailingContent
    )
}

@Composable
fun ExportLogsPreferenceButton() {
    val troubleshootingInfo = detectIssues()

    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        // If the user doesn't choose a location to save the file, don't continue
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult

        Timber.d("selected file URI: %s", uri)
        saveLogFile(context, uri, troubleshootingInfo)
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

private inline fun StringBuilder.appendDivider(): StringBuilder =
    appendLine().appendLine("------------------------------")

// FIXME: finish rewriting this
private fun saveLogFile(context: Context, uri: Uri, troubleshootingInfo: TroubleshootingInfo) {
    try {
        val stringBuilder = buildString {
            val rootPermissionInfo = troubleshootingInfo.rootPermissionInfo
            val characterDevicesInfoList = troubleshootingInfo.characterDevicesInfoList
            val kernelInfo = troubleshootingInfo.kernelInfo

            appendLine("Do we have root permissions?: ${rootPermissionInfo.hasRootPermissions}")
            appendLine("Root method: ${rootPermissionInfo.rootMethod.name}")

            appendDivider()

            if (characterDevicesInfoList != null) {
                for (characterDevice in characterDevicesInfoList) {
                    appendLine("character device info for: ${characterDevice.path}")
                    appendLine("does it exist?: ${characterDevice.isPresent}")
                    appendLine("is it visible without root?: ${characterDevice.isVisibleWithoutRoot}")
                    appendLine("permissions: ")
                    appendLine(characterDevice.permissions)

                    appendLine()
                }
            } else {
                appendLine("character device info list is null, that's bad.")
            }

            appendDivider()

            if (kernelInfo != null) {
                appendLine("relevant snippet of kernel config: ")
                appendLine(kernelInfo.kernelConfigAnnotated.text)
                appendLine("-")
                appendLine("has ConfigFS support?: ${kernelInfo.hasConfigFsSupport}")
                appendLine("has ConfigFS HID function support?: ${kernelInfo.hasConfigFsHidFunctionSupport}")

            } else {
                appendLine("kernel info is null, that's bad.")
            }

            appendDivider()

            appendLine("Logs: ")

            // Append all logs
            for (entry in LogBuffer.getLogList()) {
                appendLine(entry.toString())
            }
        }

        Timber.d(stringBuilder)

        // Write out file
        context.contentResolver.openOutputStream(uri).use { outputStream ->
            if (outputStream == null) {
                Timber.e("Failed to open output stream for writing log file.")
                return
            }
            outputStream.write(stringBuilder.toByteArray())
        }

        Timber.d("Successfully exported logs")
    } catch (e: IOException) {
        Timber.e(e)
        Timber.e("IOException occurred while exporting logs")
    }
}
