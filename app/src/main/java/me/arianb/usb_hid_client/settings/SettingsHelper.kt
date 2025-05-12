package me.arianb.usb_hid_client.settings

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
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import me.arianb.usb_hid_client.ui.utils.LabeledCategory
import kotlin.reflect.KProperty1

// This is basically just an alias now
@Composable
fun PreferenceCategory(
    title: String,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true,
    preferences: @Composable () -> Unit
) = LabeledCategory(title, modifier, showDivider) {
    preferences()
}

@Composable
fun SwitchPreference(
    title: String,
    summary: String? = null,
    preference: BooleanPreferenceKey,
    viewModel: SettingsViewModel = viewModel()
) {
    var isChecked by remember { mutableStateOf(viewModel.getPreference(preference)) }

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
                    viewModel.setPreference(preference, isChecked)
                },
            )
        }
    )
}

@Composable
fun <T : SealedString> BasicListPreference(
    title: String,
    options: List<T>,
    selected: T,
    enabled: Boolean = true,
    onPreferenceClicked: ((thisSealedString: T) -> Unit),
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
fun <T> TextDialogPreference(
    title: String,
    summary: String? = null, // If null (default), display the current preference value
    enabled: Boolean = true,
    preference: ObjectPreferenceKey<T>,
    property: KProperty1<UserPreferences, T>,
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val currentUserPreferences by settingsViewModel.userPreferencesFlow.collectAsState()

    val initialValue = property.get(currentUserPreferences)
    val initialValueString = if (initialValue == preference.defaultValue) {
        // If preference is default, string is empty
        ""
    } else {
        preference.toStringPreference(initialValue)
    }

    // UI stuff
    var isShowingDialog by remember { mutableStateOf(false) }
    var editableValue by remember { mutableStateOf(initialValueString) }
    OnClickPreference(
        title = title,
        summary = summary ?: editableValue.ifBlank {
            "Default"
        },
        enabled = enabled,
        onClick = { isShowingDialog = true },
        trailingContent = {
            if (isShowingDialog) {
                AlertDialog(
                    title = { Text(title) },
                    onDismissRequest = { isShowingDialog = false },
                    confirmButton = {
                        TextButton(onClick = { isShowingDialog = false }) {
                            Text("OK")
                        }
                    },
                    text = {
                        TextField(
                            value = editableValue,
                            placeholder = {
                                Text(text = preference.toStringPreference(preference.defaultValue))
                            },
                            onValueChange = {
                                // Update UI
                                editableValue = it

                                // Update preference
                                if (it.isBlank()) {
                                    settingsViewModel.resetPreferenceToDefault(preference)
                                } else {
                                    settingsViewModel.setPreference(preference, preference.fromStringPreference(it))
                                }
                            },
                        )
                    }
                )
            }
        }
    )
}
