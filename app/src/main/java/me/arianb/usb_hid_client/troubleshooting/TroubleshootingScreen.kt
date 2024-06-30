package me.arianb.usb_hid_client.troubleshooting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import me.arianb.usb_hid_client.MainViewModel
import me.arianb.usb_hid_client.R
import me.arianb.usb_hid_client.shell_utils.RootMethod
import me.arianb.usb_hid_client.ui.theme.codeLineHeightScaleFactor
import me.arianb.usb_hid_client.ui.theme.codeStyle
import me.arianb.usb_hid_client.ui.utils.BasicPage
import me.arianb.usb_hid_client.ui.utils.DarkLightModePreviews
import me.arianb.usb_hid_client.ui.utils.LabeledCategory
import me.arianb.usb_hid_client.ui.utils.SimpleNavTopBar
import timber.log.Timber

class TroubleshootingScreen : Screen {
    @Composable
    override fun Content() {
        TroubleshootingPage()
    }
}

@Composable
fun TroubleshootingPage() {
    BasicPage(
        topBar = { TroubleshootingTopBar() },
        scrollable = true
    ) {

        // TODO: add buttons for gadget "actions" like:
        //  - [x] create gadget
        //  - [x] remove gadget
        //  - [ ] re-create gadget
//        LabeledCategory("Gadget Actions") {
//            GadgetActionButtons()
//        }

        LabeledCategory("Debugging Information") {
            DebuggingInfoList()
        }

        ExportLogsPreferenceButton()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TroubleshootingTopBar() {
    SimpleNavTopBar(
        title = stringResource(R.string.troubleshooting_title)
    )
}

@Composable
private fun GadgetActionButtons(mainViewModel: MainViewModel = viewModel()) {
    var isShowingConfirmationAlert by remember { mutableStateOf(false) }

    val state by mainViewModel.uiState.collectAsState()

    val runOnClick: () -> Unit
    val actionLabel: String
    if (state.missingCharacterDevice) {
        runOnClick = { mainViewModel.createCharacterDevices() }
        actionLabel = "Create Character Devices"
    } else {
        runOnClick = { mainViewModel.deleteCharacterDevices() }
        actionLabel = "Delete Character Devices"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        Button(onClick = { isShowingConfirmationAlert = true }) {
            Text(actionLabel)
        }
    }

    if (isShowingConfirmationAlert) {
        AlertDialog(
            title = { Text("Are you sure you want to do this?") },
            text = { Text(text = actionLabel) },
            onDismissRequest = { isShowingConfirmationAlert = false },
            confirmButton = {
                TextButton(onClick = {
                    runOnClick()
                    isShowingConfirmationAlert = false
                }) {
                    Text("Yes")
                }
            },
            dismissButton = {
                TextButton(onClick = { isShowingConfirmationAlert = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun DebuggingInfoList() {
    val troubleshootingInfo = detectIssues()
    Timber.d("debug info: %s", troubleshootingInfo.toString())

    with(troubleshootingInfo.rootPermissionInfo) {
        GadgetStatusItem(
            title = "Root Permission Info",
            summary = "Root Method: ${rootMethod.name}",
            isGood = hasRootPermissions && rootMethod != RootMethod.UNKNOWN
        )
    }

    troubleshootingInfo.characterDevicesInfoList?.let {
        for (characterDevice in it) {
            GadgetStatusItem(
                title = "Character Device Info",
                summary = "path: ${characterDevice.path}",
                extraInfo = AnnotatedString(characterDevice.permissions ?: "Failed to read permissions"),
                isGood = characterDevice.isPresent && characterDevice.isVisibleWithoutRoot && characterDevice.permissions != null
            )
        }
    }

    troubleshootingInfo.kernelInfo?.let {
        GadgetStatusItem(
            title = "Kernel Support Info",
            summary = "ConfigFS support: ${it.hasConfigFsSupport ?: "Unknown"}" + "\n" +
                    "ConfigFS HID support: ${it.hasConfigFsHidFunctionSupport ?: "Unknown"}",
            extraInfo = it.kernelConfigAnnotated,
            isGood = if (it.hasConfigFsSupport == null || it.hasConfigFsHidFunctionSupport == null) {
                null
            } else {
                it.hasConfigFsSupport && it.hasConfigFsHidFunctionSupport
            }
        )
    }
}

@Composable
private fun GadgetStatusItem(
    title: String,
    summary: String? = null,
    isGood: Boolean?,
    extraInfo: AnnotatedString,
) {
    var isShowingInfoAlert by remember { mutableStateOf(false) }

    GadgetStatusItem(
        title = title,
        summary = summary,
        isGood = isGood,
        additionalTrailingContent = { color ->
            IconButton(onClick = { isShowingInfoAlert = !isShowingInfoAlert }) {
                Icon(imageVector = Icons.Outlined.Info, tint = color, contentDescription = "Info")
            }
        }
    )

    if (isShowingInfoAlert) {
        val clipboardManager = LocalClipboardManager.current
        AlertDialog(
            title = { Text(title) },
            text = { CodeText(extraInfo) },
            onDismissRequest = { isShowingInfoAlert = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        clipboardManager.setText(extraInfo)
                        //isShowingInfoAlert = false // TODO: should I close the dialog when text is copied or not?
                    }
                ) {
                    // TODO: should i add a "copy" icon here?
                    Text("Copy")
                }
            }
        )
    }
}

@Composable
private fun GadgetStatusItem(
    title: String,
    summary: String?,
    isGood: Boolean?,
    additionalTrailingContent: @Composable ((color: Color) -> Unit)? = null
) {
    val color = if (isGood == true) {
        LocalContentColor.current
    } else {
        MaterialTheme.colorScheme.error
    }

    val baseTrailingContent = @Composable {
        // This is here so that these icons are aligned properly with the IconButtons, since those have larger
        // minimum padding due to accessibility guidelines for touch targets. These aren't interactive components.
        val sizeModifier = Modifier.minimumInteractiveComponentSize()
        when (isGood) {
            true -> {
                Icon(
                    modifier = sizeModifier,
                    imageVector = Icons.Default.Check,
                    tint = color,
                    contentDescription = "Good"
                )
            }

            false -> {
                Icon(
                    modifier = sizeModifier,
                    painter = painterResource(R.drawable.priority_high),
                    tint = color,
                    contentDescription = "Error"
                )
            }

            null -> {
                Icon(
                    modifier = sizeModifier,
                    painter = painterResource(R.drawable.question_mark_outline),
                    tint = color,
                    contentDescription = "Unknown"
                )
            }
        }
    }

    val trailingContent: @Composable (() -> Unit) = if (additionalTrailingContent == null) {
        baseTrailingContent
    } else {
        {
            Row {
                additionalTrailingContent(color)
                baseTrailingContent()
            }
        }
    }

    ListItem(
        colors = ListItemDefaults.colors(headlineColor = color),
        headlineContent = { Text(title) },
        supportingContent = {
            if (summary != null) {
                Text(summary)
            }
        },
        trailingContent = trailingContent
    )
}

@Composable
private fun CodeText(text: AnnotatedString) {
    AutoResizeText(text, style = codeStyle)
}

/**
 * Text composable that automatically shrinks its text as to not overflow and wrap.
 *
 * @param minFontSize Minimum font size to shrink text to. Prevents long lines from becoming incredibly small.
 */
@Composable
fun AutoResizeText(text: AnnotatedString, style: TextStyle, minFontSize: TextUnit = 8.sp) {
    var readyToDraw by remember { mutableStateOf(false) }
    var textStyle by remember { mutableStateOf(style) }
    Text(
        text,
        style = textStyle,
        softWrap = false,
        modifier = Modifier.drawWithContent {
            if (readyToDraw) {
                drawContent()
            }
        },
        onTextLayout = { textLayoutResult ->
            if (textLayoutResult.didOverflowWidth && textStyle.fontSize > minFontSize) {
                Timber.d("fixing font size, current value: %s", textStyle.fontSize)
                textStyle = textStyle.copy(
                    fontSize = textStyle.fontSize * 0.95,
                    lineHeight = textStyle.fontSize * codeLineHeightScaleFactor
                )
            } else {
                Timber.d("FONT SIZE THAT FITS: %s", textStyle.fontSize)
                readyToDraw = true
            }
        },
    )
}

@DarkLightModePreviews
@Composable
private fun GadgetConfigurationScreenPreview() {
    Navigator(TroubleshootingScreen())
}
