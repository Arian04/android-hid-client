package me.arianb.usb_hid_client.input_views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.lifecycle.viewmodel.compose.viewModel
import me.arianb.usb_hid_client.MainViewModel
import me.arianb.usb_hid_client.R
import me.arianb.usb_hid_client.hid_utils.KeyCodeTranslation
import me.arianb.usb_hid_client.settings.PreferenceKey
import me.arianb.usb_hid_client.settings.SettingsViewModel
import me.arianb.usb_hid_client.ui.theme.PaddingLarge
import timber.log.Timber

@Composable
fun ManualInput(
    mainViewModel: MainViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel()
) {
    var manualInputString by remember { mutableStateOf("") }
    var showInputString by remember { mutableStateOf(value = true) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PaddingLarge),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f), // For some reason, this makes the button not be squished at the end of the Row
            value = manualInputString,
            label = { Text(stringResource(R.string.manual_input)) },
            onValueChange = { manualInputString = it },
            visualTransformation = if (showInputString) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            keyboardOptions = if (showInputString) {
                KeyboardOptions(keyboardType = KeyboardType.Text)
            } else {
                KeyboardOptions(keyboardType = KeyboardType.Password)
            },
            trailingIcon = {
                if (showInputString) {
                    IconButton(onClick = { showInputString = false }) {
                        Icon(
                            imageVector = Icons.Filled.Visibility,
                            contentDescription = "Show Input"
                        )
                    }
                } else {
                    IconButton(
                        onClick = { showInputString = true }) {
                        Icon(
                            imageVector = Icons.Filled.VisibilityOff,
                            contentDescription = "Hide Input"
                        )
                    }
                }
            }
        )
        Button(
            modifier = Modifier.wrapContentSize(),
            onClick = onClick@{
                // If empty, don't do anything
                if (manualInputString.isEmpty()) {
                    return@onClick
                }

                val stringToSend = manualInputString
                Timber.d("manual input sending string: %s", stringToSend)

                // Clear EditText if the user's preference is to clear it
                val clearManualInput = settingsViewModel.getBoolean(PreferenceKey.ClearManualInputKey, false)
                if (clearManualInput) {
                    manualInputString = ""
                }

                // Sends all keys
                for (char in stringToSend) {
                    // Converts (String) key to (byte) key scan code and (byte) modifier scan code and add to queue
                    val tempScanCodes = KeyCodeTranslation.convertKeyToScanCodes(char)
                    if (tempScanCodes == null) {
                        val error = "key: '$char' is not supported."
                        Timber.e(error)
                        // FIXME: snackbar
//                        Snackbar.make(parentLayout, error, Snackbar.LENGTH_SHORT).show()
                        return@onClick
                    }
                    val modifierScanCode = tempScanCodes[0]
                    val keyScanCode = tempScanCodes[1]
                    mainViewModel.addStandardKey(modifierScanCode, keyScanCode)
                }
            }
        ) {
            Text(stringResource(R.string.send))
        }
    }
}
