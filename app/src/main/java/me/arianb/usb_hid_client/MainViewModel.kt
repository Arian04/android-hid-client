package me.arianb.usb_hid_client

import android.app.Application
import android.util.Log
import androidx.collection.mutableIntSetOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.arianb.usb_hid_client.hid_utils.CharacterDeviceManager
import me.arianb.usb_hid_client.hid_utils.ModifiesStateDirectly
import me.arianb.usb_hid_client.report_senders.KeySender
import me.arianb.usb_hid_client.report_senders.MouseSender
import me.arianb.usb_hid_client.report_senders.ReportSender
import me.arianb.usb_hid_client.shell_utils.RootMethod
import me.arianb.usb_hid_client.shell_utils.RootStateHolder
import timber.log.Timber
import java.io.FileNotFoundException
import java.io.IOException

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(MyUiState())
    val uiState: StateFlow<MyUiState> = _uiState

    private val characterDeviceManager = CharacterDeviceManager.getInstance(application)
    private val rootStateHolder = RootStateHolder.getInstance()
    val keySender = KeySender()
    val mouseSender = MouseSender()

    init {
        for (sender: ReportSender in listOf(keySender, mouseSender)) {
            viewModelScope.launch(ReportSender.dispatcher) {
                sender.start { e ->
                    val characterDevicePath = sender.characterDevicePath
                    if (e is FileNotFoundException && characterDeviceMissing(characterDevicePath)) {
                        Timber.wtf("Character device '$characterDevicePath' doesn't exist. Its existence is verified on app start, so the only reason this should happen is if it was removed *after* the app started.")
                    } else {
                        handleException(e, sender.characterDevicePath)
                    }
                }
            }
        }
    }

    private fun handleException(e: IOException, devicePath: String) {
        val lowercaseStacktrace = Log.getStackTraceString(e).lowercase()

        if (lowercaseStacktrace.contains("errno 108")) {
            Timber.d("device might be unplugged")
            _uiState.update { uiState.value.copy(isDeviceUnplugged = true) }
        } else if (lowercaseStacktrace.contains("permission denied")) {
            Timber.d("char dev perms are wrong")
            _uiState.update { uiState.value.copy(isCharacterDevicePermissionsBroken = devicePath) }
        } else if (lowercaseStacktrace.contains("enxio")) {
            Timber.d("somehow the HID gadget is disabled but the character devices are still present")
        } else {
            Timber.d("something else is wrong, idk")
//            showSnackbar("ERROR: Failed to send mouse report.", Snackbar.LENGTH_SHORT)
        }

        Timber.d("in MainViewModel, new state is: %s", uiState.value.toString())
    }

    // Character Device Manager
    fun createCharacterDevices() {
        if (!rootStateHolder.hasRootPermissions()) {
            Timber.e("Can't create character devices, missing root permissions")
            return
        }

        viewModelScope.launch {
            characterDeviceManager.createCharacterDevices()
        }
    }

    fun deleteCharacterDevices() {
        if (!rootStateHolder.hasRootPermissions()) {
            Timber.e("Can't delete character devices, missing root permissions")
            return
        }

        characterDeviceManager.deleteCharacterDevices()
    }

    fun fixCharacterDevicePermissions(device: String) {
        if (!rootStateHolder.hasRootPermissions()) {
            Timber.e("Can't fix character device permissions, missing root permissions")
            return
        }

        characterDeviceManager.fixCharacterDevicePermissions(device)
    }

    @OptIn(ModifiesStateDirectly::class)
    fun characterDeviceMissing(charDevicePath: String): Boolean {
        val result = characterDeviceManager.characterDeviceMissing(charDevicePath)

        _uiState.update { uiState.value.copy(missingCharacterDevice = result) }

        return result
    }

    @OptIn(ModifiesStateDirectly::class)
    fun anyCharacterDeviceMissing(): Boolean {
        val result = characterDeviceManager.anyCharacterDeviceMissing()

        _uiState.update { uiState.value.copy(missingCharacterDevice = result) }

        return result
    }

    data class DebugIssues(
        val hasRootPermissions: Boolean,
        val rootMethod: RootMethod,
        val isEveryCharDevPresent: Boolean?,

        // kernel stuff
        val kernelConfigAnnotated: AnnotatedString,
        val hasConfigFsSupport: Boolean?,
        val hasConfigFsHidFunctionSupport: Boolean?,
    )

    // FIXME:
    //  - finish implementation
    //  - make this run in a coroutine in case something hangs?
    fun detectIssues(): DebugIssues {
        val rootStateHolder = RootStateHolder.getInstance()

        // Root permission stuff
        val hasRootPermissions = rootStateHolder.hasRootPermissions()
        val rootMethod = rootStateHolder.detectRootMethod()

        // Character device stuff
        var isEveryCharDevPresent: Boolean? = null
        // TODO: check char dev permissions. Theres a few ways I can do that:
        //  - Check it theoretically (check for presence of proper selinux policy and unix permissions)
        //  - Check it realistically (write to the devices and see what happens)

        // Kernel stuff
        val kernelConfig = getKernelConfig()
        var hasConfigFsSupport: Boolean? = null
        var hasConfigFsHidFunctionSupport: Boolean? = null
        val configFsKernelOption = "CONFIG_USB_CONFIGFS"
        val configFsHidKernelOption = "${configFsKernelOption}_F_HID"
        val highlightConfigLines = mutableIntSetOf()

        if (hasRootPermissions) {
            // is each char device present?
            isEveryCharDevPresent = !anyCharacterDeviceMissing()
            if (isEveryCharDevPresent) {
                // check if permissions seem good
                val arePermissionsGood = false // TODO:
                if (arePermissionsGood) {
                    // try to use the char device (write something safe like all zeroes)
                    val didWriteFail = false
                    if (didWriteFail) {
                        // TODO: more debugging necessary, grab the exception
                    } else {
                        // doesn't seem like there are any problems
                    }
                }
            }

            // Check kernel support
            for ((index, line) in kernelConfig.withIndex()) {
                // Parse kernel config by line
                val configOption: String
                val enabled: Boolean
                if (line.first() == '#') {
                    enabled = false
                    configOption = line.split(" ")[1] // second word
                } else if (line[line.length - 2] == '=') { // if 2nd to last char is '='
                    enabled = line.last() == 'y'
                    configOption = line.substring(0, line.length - 2) // exclude last 2 chars
                } else {
                    Timber.wtf("error while parsing kernel config, this line was not formatted as expected: %s", line)
                    continue
                }

                // Evaluate the information that I parsed
                when (configOption) {
                    configFsKernelOption -> {
                        hasConfigFsSupport = enabled
                        highlightConfigLines.add(index)
                    }

                    configFsHidKernelOption -> {
                        hasConfigFsHidFunctionSupport = enabled
                        highlightConfigLines.add(index)
                    }

                    else -> {
                        // Found a config option I don't care about
                    }
                }
            }
        }

        // TODO: optimize this by building annotated string during the first loop through the config instead of looping
        //       a second time here.
        val kernelConfigAnnotatedString: AnnotatedString = if (kernelConfig.isEmpty()) {
            AnnotatedString("Failed to read kernel config")
        } else {
            buildAnnotatedString {
                for ((index, line) in kernelConfig.withIndex()) {
                    val annotatedLine = if (highlightConfigLines.contains(index)) {
                        AnnotatedString(line, spanStyle = SpanStyle(color = Color.Red))
                    } else {
                        line
                    }

                    append(annotatedLine)
                    appendLine()
                }
            }
        }

        return DebugIssues(
            hasRootPermissions = hasRootPermissions,
            rootMethod = rootMethod,

            isEveryCharDevPresent = isEveryCharDevPresent,

            kernelConfigAnnotated = kernelConfigAnnotatedString,
            hasConfigFsSupport = hasConfigFsSupport,
            hasConfigFsHidFunctionSupport = hasConfigFsHidFunctionSupport,
        )
    }

    private fun getKernelConfig(): List<String> {
        val commandResult = Shell.cmd("gunzip -c /proc/config.gz | grep -i configfs").exec()

        val kernelConfigLinesList = commandResult.out

        // DEBUG: this is code for testing behavior if kernel support wasn't present
//        val kernelConfigLinesList = mutableListOf<String>()
//        for (line in commandResult.out) {
//            when (line) {
//                "CONFIG_USB_CONFIGFS=y" -> {
//                    kernelConfigLinesList.add("CONFIG_USB_CONFIGFS=n")
//                }
//
//                "CONFIG_USB_CONFIGFS_F_HID=y" -> {
//                    kernelConfigLinesList.add("CONFIG_USB_CONFIGFS_F_HID=n")
//                }
//
//                else -> {
//                    kernelConfigLinesList.add(line)
//                }
//            }
//        }

        // TODO:
        //  - handle general errors
        //  - handle if /proc/config.gz doesn't exist
        //  - if empty, grab config without using grep to filter?

        if (kernelConfigLinesList.isEmpty()) {
            Timber.e("failed to read kernel config")
        }

        return kernelConfigLinesList
    }

    // Keyboard
    fun addStandardKey(modifier: Byte, key: Byte) =
        keySender.addStandardKey(modifier, key)

    fun addMediaKey(key: Byte) =
        keySender.addMediaKey(key)

    // Mouse
    fun click(button: Byte) =
        mouseSender.click(button)

    fun move(x: Byte, y: Byte) =
        mouseSender.move(x, y)
}
