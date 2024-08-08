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
import timber.log.Timber


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
fun AppThemePreference(
    title: String,
    enabled: Boolean = true,
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val preferencesState by settingsViewModel.userPreferencesFlow.collectAsState()

    val selectedTheme = preferencesState.appTheme

    val options = AppTheme.values

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
