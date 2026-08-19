package me.arianb.usb_hid_client.input_views.scripting

import android.content.ContentResolver
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import me.arianb.usb_hid_client.MainViewModel
import me.arianb.usb_hid_client.R
import me.arianb.usb_hid_client.hid_utils.KeyCodeTranslation
import me.arianb.usb_hid_client.ui.theme.PaddingExtraSmall
import me.arianb.usb_hid_client.ui.theme.PaddingSmall
import timber.log.Timber
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.Locale.getDefault

//---------- Contribution by saaiqSAS ----------
@Composable
fun ScriptsDisplayView(
    mainViewModel: MainViewModel = viewModel(),
    scriptingViewModel: ScriptingViewModel = viewModel()
) {
    var scriptPathString by remember { mutableStateOf("") }
    var scriptFileUri by remember { mutableStateOf<Uri?>(null) }

    val context = LocalContext.current
    val contentResolver = context.contentResolver
    val scriptFileExtensions = listOf(".ahc", ".duck", ".txt")

    val filePickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                val mimeType = context.contentResolver.getType(uri)
                if ((mimeType == "text/plain" || mimeType == "application/octet-stream") && scriptFileExtensions.any {
                        uri.path.toString().endsWith(it)
                    }) {
                    scriptPathString = uri.path.toString()
                    scriptFileUri = uri
                } else {
                    Toast.makeText(
                        context,
                        "Only (${scriptFileExtensions.joinToString(", ")}) files are accepted",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

        }

    Column(
        modifier = Modifier
            .padding(0.dp, 0.dp, 0.dp, 0.dp),
        verticalArrangement = Arrangement.spacedBy(PaddingSmall),
        horizontalAlignment = Alignment.CenterHorizontally,

        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(0.dp, PaddingExtraSmall, 0.dp, 0.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                modifier = Modifier
                    .wrapContentHeight()
                    .padding(0.dp, 0.dp, 0.dp, 0.dp),
                text = scriptingViewModel.scriptLog,
                textAlign = TextAlign.Left,
                style = TextStyle(
                    fontSize = 14.sp,
                )
            )
        }

        TextField(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            value = scriptPathString,
            label = { Text("Script File Path") },
            onValueChange = { scriptPathString = it },
            readOnly = true
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
                    .padding(0.dp, 0.dp, PaddingExtraSmall, 0.dp),
                shape = RoundedCornerShape(
                    topStart = 20.dp,
                    topEnd = 0.dp,
                    bottomStart = 20.dp,
                    bottomEnd = 0.dp
                ),
                onClick = onClick@{
                    filePickerLauncher.launch("application/plain")
                }
            ) {
                Text(stringResource(R.string.select))
            }

            Button(
                modifier = Modifier
                    .wrapContentSize()
                    .padding(0.dp, 0.dp, PaddingExtraSmall, 0.dp),
                shape = RoundedCornerShape(
                    topStart = 0.dp,
                    topEnd = 0.dp,
                    bottomStart = 0.dp,
                    bottomEnd = 0.dp
                ),
                onClick = onClick@{
                    if (scriptFileUri == null) {
                        Toast.makeText(context, "Select script file first", Toast.LENGTH_SHORT).show()
                        return@onClick
                    }
                    val script = readFileFromUri(contentResolver, scriptFileUri!!)
                    scriptingViewModel.manualInputText = script
                }
            ) {
                Text(stringResource(R.string.edit))
            }

            Button(
                modifier = Modifier.wrapContentSize(),
                shape = RoundedCornerShape(
                    topStart = 0.dp,
                    topEnd = 20.dp,
                    bottomStart = 0.dp,
                    bottomEnd = 20.dp
                ),
                onClick = onClick@{
                    if (scriptFileUri == null) {
                        Toast.makeText(context, "Select script file first", Toast.LENGTH_SHORT).show()
                        return@onClick
                    }
                    executeScriptFile(contentResolver, scriptFileUri, mainViewModel, scriptingViewModel)
                }
            ) {
                Text(stringResource(R.string.execute_script))
            }
        }

    }

}

fun executeScriptFile(
    contentResolver: ContentResolver,
    scriptFileUri: Uri?,
    mainViewModel: MainViewModel,
    scriptingViewModel: ScriptingViewModel
) {
    if (scriptFileUri != null) {
        val content = readFileFromUri(contentResolver, scriptFileUri)
        scriptExecutor(content, mainViewModel, scriptingViewModel)
    }
}

fun readFileFromUri(contentResolver: ContentResolver, fileUri: Uri): String {
    var fileContent = ""
    val inputStream = contentResolver.openInputStream(fileUri)

    inputStream?.let {
        try {
            val reader = BufferedReader(InputStreamReader(it))
            val stringBuilder = StringBuilder()

            reader.forEachLine { line ->
                stringBuilder.append(line).append("\n")
            }

            fileContent = stringBuilder.toString()
            reader.close()
        } catch (e: IOException) {
            Timber.e("Error reading file: ${e.message}")
        } finally {
            it.close()
        }
    }
    return fileContent.removeSuffix("\n")
}

fun scriptExecutor(script: String, mainViewModel: MainViewModel, scriptingViewModel: ScriptingViewModel) {
    //---------- Contribution by saaiqSAS ----------
    // INTERPRETER FOR SCRIPTING FORMAT
    // This method allows the use of scripting. The entire script should be passed to this method and it will be executed line by line

    val lines = (script).split("\n")
    var lineNum = 0

    scriptingViewModel.updateScriptLog("") // reset

    for (line in lines) {
        val command: String
        var key = ""
        var para = ""
        val firstSpace = line.indexOf(" ")
        lineNum++

        if (firstSpace == -1) {
            command = line
        } else {
            command = line.take(firstSpace)
            para = line.substring(firstSpace + 1)
        }

        when (command.uppercase(getDefault())) {
            //COMMANDS - only commands take a parameter
            "//", "REM", "NAME", "DESC", "AUTHOR" -> {} //do nothing
            "SEND", "STRING" -> sendInputScriptString(para, mainViewModel)
            "SENDLN", "STRINGLN" -> sendInputScriptString(para + "\n", mainViewModel)
            "SLEEP", "DELAY" -> Thread.sleep(para.toLong())

            //MODIFIER KEYS
            "L_CTRL", "L_CONTROL", "CTRL" -> key = "left-ctrl"
            "L_ALT", "ALT" -> key = "left-alt"
            "L_SHIFT", "SHIFT" -> key = "left-shift"
            "L_META", "L_WIN", "META", "WIN" -> key = "left-meta"
            "R_CTRL", "R_CONTROL" -> key = "right-ctrl"
            "R_ALT" -> key = "right-alt"
            "R_SHIFT" -> key = "right-shift"
            "R_META", "R_WIN" -> key = "right-meta"

            //SPECIAL KEYS
            "UP" -> key = "up"
            "DOWN" -> key = "down"
            "LEFT" -> key = "left"
            "RIGHT" -> key = "right"
            "ESCAPE", "ESC" -> key = "escape"
            "TAB" -> key = "tab"
            "BACKSPACE", "BACK" -> key = "backspace"
            "DELETE", "DEL" -> key = "delete"
            "PRINT" -> key = "print"
            "SPACE" -> key = " "
            "ENTER" -> key = "\n"
            "SCROLL_LOCK" -> key = "scroll-lock"
            "NUM_LOCK" -> key = "num-lock"
            "PAUSE" -> key = "pause"
            "INSERT" -> key = "insert"
            "HOME" -> key = "home"
            "END" -> key = "end"
            "PAGE_UP", "PG_UP" -> key = "page-up"
            "PAGE_DOWN", "PG_DOWN" -> key = "page-down"
            "NEXT" -> key = "next"
            "PREVIOUS", "PREV" -> key = "previous"
            "PLAY_PAUSE", "PLAY", "PP" -> key = "play-pause"
            "VOLUME_UP", "VOL_UP" -> key = "volume-up"
            "VOLUME_DOWN", "VOL_DOWN" -> key = "volume-down"
            "F1" -> key = "f1"
            "F2" -> key = "f2"
            "F3" -> key = "f3"
            "F4" -> key = "f4"
            "F5" -> key = "f5"
            "F6" -> key = "f6"
            "F7" -> key = "f7"
            "F8" -> key = "f8"
            "F9" -> key = "f9"
            "F10" -> key = "f10"
            "F11" -> key = "f11"
            "F12" -> key = "f12"

            else -> {
                scriptingViewModel.updateScriptLog("Error at line $lineNum")
                return
            }

        }

        if (key.isNotEmpty()) {
            val scanCodes = KeyCodeTranslation.Scripting.keyCharToScanCodes(key)

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
    scriptingViewModel.updateScriptLog("Script Executed")
}