package me.arianb.usb_hid_client

import androidx.collection.mutableIntSetOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ShellUtils
import me.arianb.usb_hid_client.hid_utils.CharacterDeviceManager
import me.arianb.usb_hid_client.shell_utils.RootMethod
import me.arianb.usb_hid_client.shell_utils.RootStateHolder
import timber.log.Timber
import java.io.File

data class TroubleshootingInfo(
    val rootPermissionInfo: RootPermissionInfo,
    val characterDevicesInfoList: List<CharacterDeviceInfo>? = null,
    val kernelInfo: KernelInfo? = null
)

data class RootPermissionInfo(
    val hasRootPermissions: Boolean,
    val rootMethod: RootMethod,
)

data class CharacterDeviceInfo(
    val path: String,
    val isPresent: Boolean,
    val isVisibleWithoutRoot: Boolean,
    val permissions: String?,
)

data class KernelInfo(
    val kernelConfigAnnotated: AnnotatedString,
    val hasConfigFsSupport: Boolean?,
    val hasConfigFsHidFunctionSupport: Boolean?,
)

// TODO: make this run in a coroutine in case something takes a while or hangs?
fun detectIssues(): TroubleshootingInfo {
    val rootStateHolder = RootStateHolder.getInstance()

    // Root permission stuff
    val hasRootPermissions = rootStateHolder.hasRootPermissions()
    val rootMethod = rootStateHolder.detectRootMethod()
    val rootPermissionInfo = RootPermissionInfo(
        hasRootPermissions,
        rootMethod,
    )

    // Character device stuff
    val characterDevicesInfoList: List<CharacterDeviceInfo>?

    // Kernel stuff
    val kernelInfo: KernelInfo?

    if (hasRootPermissions) {
        // Check character device stuff
        characterDevicesInfoList = buildList {
            for (path in CharacterDeviceManager.ALL_CHARACTER_DEVICE_PATHS) {
                add(getCharacterDeviceInfo(path))
            }
        }

        // Check kernel support
        kernelInfo = getKernelInfo()
    } else {
        characterDevicesInfoList = null
        kernelInfo = null
    }

    return TroubleshootingInfo(
        rootPermissionInfo = rootPermissionInfo,
        characterDevicesInfoList = characterDevicesInfoList,
        kernelInfo = kernelInfo
    )
}

@RequiresRoot
private fun getCharacterDeviceInfo(gadgetPath: String): CharacterDeviceInfo {
    val safeGadgetPathString = ShellUtils.escapedString(gadgetPath)

    // Check if it exists
    val shellResult = Shell.cmd("test -e $safeGadgetPathString").exec()
    val isPresent = shellResult.code == 0

    val isVisibleWithoutRoot: Boolean
    val permissionsString: String?

    if (isPresent) {
        // Check if it's still visible if we check without root permissions
        // this verifies that selinux policy was added correctly
        isVisibleWithoutRoot = File(gadgetPath).exists()
        if (!isVisibleWithoutRoot) {
            // selinux policy is probably not right
        }

        // read permissions
        val result = Shell.cmd("ls -lZ -- $safeGadgetPathString").exec()

        permissionsString = buildString {
            // Check if command ran successfully
            //if (!result.isSuccess) {
            // oh no, it wasn't successful :(
            //}

            // Check if output seems alright
            val outputLinesList = result.out
            if (outputLinesList.isNotEmpty()) {
                append("stdout (with extra newlines): ")
                appendLine()
                for (line in outputLinesList) {
                    val adjustedLine = line.replace(' ', '\n')
                    append(adjustedLine)
                    appendLine()
                }
            }

            val errorLinesList = result.err
            if (errorLinesList.isNotEmpty()) {
                append("stderr: ")
                for (line in errorLinesList) {
                    append(line)
                    appendLine()
                }
            }
        }

        // TODO: check if permissions seem correct
//        Process.myUid()
        val arePermissionsGood = false
        if (arePermissionsGood) {
            // try to use the char device (write something safe like all zeroes)
            val didWriteFail = false
            if (didWriteFail) {
                // TODO: more debugging necessary, grab the exception
            } else {
                // doesn't seem like there are any problems
            }
        }
    } else {
        isVisibleWithoutRoot = false
        permissionsString = null
    }

    return CharacterDeviceInfo(
        path = gadgetPath,
        isPresent = isPresent,
        isVisibleWithoutRoot = isVisibleWithoutRoot,
        permissions = permissionsString,
    )
}

@RequiresRoot
private fun getKernelInfo(): KernelInfo {
    // constants
    val configFsKernelOption = "CONFIG_USB_CONFIGFS"
    val configFsHidKernelOption = "${configFsKernelOption}_F_HID"

    val kernelConfig = getKernelConfig()
    var hasConfigFsSupport: Boolean? = null
    var hasConfigFsHidFunctionSupport: Boolean? = null
    val highlightConfigLines = mutableIntSetOf()

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

    return KernelInfo(
        kernelConfigAnnotatedString,
        hasConfigFsSupport,
        hasConfigFsHidFunctionSupport,
    )
}

@RequiresRoot
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

/**
 * Should only be called if you know you have root permissions
 */
annotation class RequiresRoot
