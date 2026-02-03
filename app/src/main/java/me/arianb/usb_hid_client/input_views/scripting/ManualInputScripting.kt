package me.arianb.usb_hid_client.input_views.scripting

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import me.arianb.usb_hid_client.MainViewModel
import me.arianb.usb_hid_client.R
import me.arianb.usb_hid_client.hid_utils.KeyCodeTranslation
import me.arianb.usb_hid_client.settings.SettingsViewModel
import me.arianb.usb_hid_client.ui.theme.PaddingExtraSmall
import me.arianb.usb_hid_client.ui.theme.PaddingSmall
import timber.log.Timber

@Composable
fun ManualInputForScripting(
    mainViewModel: MainViewModel = viewModel(),
    scriptingViewModel: ScriptingViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel()
) {
    var manualInputString by scriptingViewModel::manualInputText

    val preferencesState by settingsViewModel.userPreferencesFlow.collectAsState()
    val shouldClearManualInputOnSend = preferencesState.clearManualInput
    val context = LocalContext.current

    val createFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            // Save file through ViewModel
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(manualInputString.toByteArray())
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(PaddingSmall),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TextField(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            value = manualInputString,
            label = { Text(stringResource(R.string.manual_input)) },
            onValueChange = { manualInputString = it },
            maxLines = 3,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                modifier = Modifier
                    .wrapContentSize()
                    .padding(end = PaddingExtraSmall),
                shape = RoundedCornerShape(
                    topStart = 20.dp,
                    topEnd = 0.dp,
                    bottomStart = 20.dp,
                    bottomEnd = 0.dp
                ),
                onClick = onClick@{
                    if (manualInputString.isEmpty()) {
                        Toast.makeText(context, "Nothing to save", Toast.LENGTH_SHORT).show()
                        return@onClick
                    }

                    createFileLauncher.launch("script.ahc") // .ahc (android hid client)

                }
            ) {
                Text(stringResource(R.string.save))
            }

            Button( // pass to sendString()
                modifier = Modifier
                    .wrapContentSize()
                    .padding(end = PaddingExtraSmall),
                shape = CutCornerShape(size = 0.dp),
                onClick = onClick@{
                    if (manualInputString.isEmpty()) {
                        Toast.makeText(context, "Nothing to send", Toast.LENGTH_SHORT).show()
                        return@onClick
                    }
                    val stringToSend = manualInputString
                    Timber.d("manual input sending string: %s", stringToSend)

                    // Clear ManualInput if the user's preference is to clear it
                    if (shouldClearManualInputOnSend) {
                        manualInputString = ""
                    }

                    sendInputScriptString(stringToSend, mainViewModel)
                }
            ) {
                Text(stringResource(R.string.send))
            }

            Button( // pass to scriptExecutor()
                modifier = Modifier.wrapContentSize(),
                shape = RoundedCornerShape(
                    topStart = 0.dp,
                    topEnd = 20.dp,
                    bottomStart = 0.dp,
                    bottomEnd = 20.dp
                ),
                onClick = onClick@{
                    if (manualInputString.isEmpty()) {
                        Toast.makeText(context, "Nothing to execute", Toast.LENGTH_SHORT).show()
                        return@onClick
                    }

                    // Save string
                    val stringToSend = manualInputString
                    Timber.d("manual input sending string: %s", stringToSend)

                    // Clear EditText if the user's preference is to clear it
                    if (shouldClearManualInputOnSend) {
                        manualInputString = ""
                    }

                    scriptExecutor(stringToSend, mainViewModel, scriptingViewModel)
                }
            ) {
                Text(stringResource(R.string.execute_manual_input))
            }
        }
    }
}

fun sendInputScriptString(stringToSend: String, mainViewModel: MainViewModel) {
    //---------- Contributed by saaiqSAS ----------
    // Updated to allow the use of {[X]} tag format in the manual input field or in any String passed to this method
    // Hence setting a mockup foundation for scripting and automation

    // MODIFIER KEYS
    // {[C]} for Ctrl
    // {[A]} for Alt
    // {[S]} for Shift
    // {[M]} for Meta/Windows key

    //SPECIAL KEYS
    // {[E]} for Escape
    // {[T]} for Tab
    // {[B]} for Backspace
    // {[N]} for Enter
    // {[U]} for Up
    // {[D]} for Down
    // {[R]} for Right
    // {[L]} for Left
    // {[1]} for F1
    // {[2]} for F2
    // {[3]} for F3
    // {[4]} for F4
    // {[5]} for F5
    // {[6]} for F6
    // {[7]} for F7
    // {[8]} for F8
    // {[9]} for F9
    // {[!]} for F10
    // {[@]} for F11
    // {[#]} for F12

    //COMMANDS
    // {[ ]} to sleep for 500ms

    val arrayLength = stringToSend.length
    var i = 0
    while (i < arrayLength) {
        var key: String = stringToSend[i].toString()
        var scanCodes: Pair<Byte, Byte>?

        if (i + 4 < arrayLength) {
            if (stringToSend[i] == '{' && stringToSend[i + 1] == '[' && stringToSend[i + 3] == ']' && stringToSend[i + 4] == '}') {
                when (stringToSend[i + 2]) {
                    'C' -> key = "left-ctrl"
                    'A' -> key = "left-alt"
                    'S' -> key = "left-shift"
                    'M' -> key = "left-meta"
                    'E' -> key = "escape"
                    'T' -> key = "tab"
                    'B' -> key = "backspace"
                    'N' -> key = "\n"
                    'U' -> key = "up"
                    'D' -> key = "down"
                    'R' -> key = "right"
                    'L' -> key = "left"
                    '1' -> key = "f1"
                    '2' -> key = "f2"
                    '3' -> key = "f3"
                    '4' -> key = "f4"
                    '5' -> key = "f5"
                    '6' -> key = "f6"
                    '7' -> key = "f7"
                    '8' -> key = "f8"
                    '9' -> key = "f9"
                    '!' -> key = "f10"
                    '@' -> key = "f11"
                    '#' -> key = "f12"

                    ' ' -> { // sleep for 500ms
                        key = ""
                        Thread.sleep(500)
                    }

                }
                scanCodes = KeyCodeTranslation.Scripting.keyCharToScanCodes(key)
                i += 4
            } else {
                scanCodes = KeyCodeTranslation.Scripting.keyCharToScanCodes(stringToSend[i].toString())
            }
        } else {
            scanCodes = KeyCodeTranslation.Scripting.keyCharToScanCodes(stringToSend[i].toString())
        }
        i++


        if (scanCodes == null) {
            val error = "key: '$key' is not supported."
            Timber.e(error)
            return
        }

        if (scanCodes.second != 0x0.toByte()) {
            mainViewModel.addStandardKey(scanCodes.first, scanCodes.second)
        }

    }
}