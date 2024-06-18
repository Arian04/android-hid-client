package me.arianb.usb_hid_client.input_views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import me.arianb.usb_hid_client.MainViewModel
import me.arianb.usb_hid_client.R
import me.arianb.usb_hid_client.hid_utils.KeyCodeTranslation
import me.arianb.usb_hid_client.settings.CLEAR_MANUAL_INPUT_KEY
import me.arianb.usb_hid_client.settings.SettingsViewModel
import timber.log.Timber

@Composable
fun ManualInput(
    mainViewModel: MainViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel()
) {
    var manualInputString by remember { mutableStateOf("") }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        TextField(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f), // For some reason, this makes the button not be squished at the end of the Row
            value = manualInputString,
            label = { Text(stringResource(R.string.manual_input)) },
            onValueChange = { manualInputString = it }
        )
        Button(
            modifier = Modifier.wrapContentSize(),
            onClick = {
                // If empty, don't do anything
                if (manualInputString.isEmpty()) {
                    return@Button
                }

                val stringToSend = manualInputString
                Timber.d("manual input sending string: %s", stringToSend)

                // Clear EditText if the user's preference is to clear it
                val clearManualInput = settingsViewModel.getBoolean(CLEAR_MANUAL_INPUT_KEY, false)
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
                        return@Button
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
