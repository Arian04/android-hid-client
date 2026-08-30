package me.arianb.usb_hid_client.troubleshooting

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.collection.mutableIntSetOf
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.lifecycle.viewmodel.compose.viewModel
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ShellUtils
import me.arianb.usb_hid_client.BuildConfig
import me.arianb.usb_hid_client.MainViewModel
import me.arianb.usb_hid_client.R
import me.arianb.usb_hid_client.hid_utils.CharacterDeviceManager
import me.arianb.usb_hid_client.hid_utils.DevicePath
import me.arianb.usb_hid_client.settings.OnClickPreference
import me.arianb.usb_hid_client.shell_utils.RootMethod
import me.arianb.usb_hid_client.shell_utils.RootStateHolder
import timber.log.Timber
import java.io.IOException

data class TroubleshootingInfo(
    val deviceInfo: DeviceInfo,
    val rootPermissionInfo: RootPermissionInfo,
    val characterDevicesInfoList: List<CharacterDeviceInfo>? = null,
    val kernelInfo: KernelInfo? = null,
    val usbGadgetSystemInfo: UsbGadgetSystemInfo? = null,
)

// Not sure if the BUILD.* strings are nullable or not, so marking them as nullable just to be safe.
data class DeviceInfo(
    val manufacturer: String? = Build.MANUFACTURER,
    val model: String? = Build.MODEL,
    val brand: String? = Build.BRAND,
    val device: String? = Build.DEVICE,
    val product: String? = Build.PRODUCT,
    val board: String? = Build.BOARD,
    val hardware: String? = Build.HARDWARE,
    val sdkInt: Int = Build.VERSION.SDK_INT,
    val buildId: String? = Build.DISPLAY,
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
    val version: String,
    val kernelConfigAnnotated: AnnotatedString,
    val hasConfigFsSupport: Boolean?,
    val hasConfigFsHidFunctionSupport: Boolean?,
)

data class UsbGadgetSystemInfo(
    val selinuxMode: String?,
    val sysUsbController: String?,
    val sysUsbState: String?,
    val sysUsbConfig: String?,
    val udcListing: List<String>,
    val usbGadgetListing: List<String>,
    val characterDevicesListing: List<String>,
)

// TODO: make this run in a coroutine in case something takes a while or hangs?
fun detectIssues(): TroubleshootingInfo {
    Timber.i("detectIssues() called")

    val deviceInfo = DeviceInfo()
    Timber.v("detectIssues(): deviceInfo = $deviceInfo")

    val rootStateHolder = RootStateHolder.getInstance()

    // Root permission stuff
    val hasRootPermissions = rootStateHolder.hasRootPermissions()
    val rootMethod = rootStateHolder.detectRootMethod()
    val rootPermissionInfo = RootPermissionInfo(
        hasRootPermissions,
        rootMethod,
    )
    Timber.v("detectIssues(): rootPermissionInfo = $rootPermissionInfo")

    // Character device stuff
    val characterDevicesInfoList: List<CharacterDeviceInfo>?

    // Kernel stuff
    val kernelInfo: KernelInfo?

    // USB Gadget system info
    val usbGadgetSystemInfo: UsbGadgetSystemInfo?

    if (hasRootPermissions) {
        Timber.d("detectIssues(): gathering debugging info that requires root permissions")
        // Check character device stuff
        characterDevicesInfoList = buildList {
            for (path in CharacterDeviceManager.Companion.DevicePaths.all) {
                add(getCharacterDeviceInfo(path))
            }
        }

        // Check kernel support
        kernelInfo = getKernelInfo()

        // Check USB gadget system info
        usbGadgetSystemInfo = getUsbGadgetSystemInfo()
    } else {
        Timber.w("detectIssues(): we don't have root permissions, skipping root-level checks")
        characterDevicesInfoList = null
        kernelInfo = null
        usbGadgetSystemInfo = null
    }

    return TroubleshootingInfo(
        deviceInfo = deviceInfo,
        rootPermissionInfo = rootPermissionInfo,
        characterDevicesInfoList = characterDevicesInfoList,
        kernelInfo = kernelInfo,
        usbGadgetSystemInfo = usbGadgetSystemInfo,
    )
}

@RequiresRoot
private fun getCharacterDeviceInfo(gadgetPath: DevicePath): CharacterDeviceInfo {
    Timber.d("getCharacterDeviceInfo() called for: ${gadgetPath.path}")
    val safeGadgetPathString = ShellUtils.escapedString(gadgetPath.path)

    // Check if it exists
    val shellResult = Shell.cmd("test -e $safeGadgetPathString").exec()
    val isPresent = shellResult.code == 0
    Timber.v("getCharacterDeviceInfo(): path=${gadgetPath.path}, isPresent=$isPresent (code=${shellResult.code})")

    val isVisibleWithoutRoot: Boolean
    val permissionsString: String?

    if (isPresent) {
        // Check if it's still visible if we check without root permissions
        // this verifies that selinux policy was added correctly
        isVisibleWithoutRoot = gadgetPath.exists()
        Timber.v("getCharacterDeviceInfo(): path=${gadgetPath.path}, isVisibleWithoutRoot=$isVisibleWithoutRoot")
        if (!isVisibleWithoutRoot) {
            Timber.w("getCharacterDeviceInfo(): path=${gadgetPath.path} exists with root, but is NOT visible without root! SELinux policy may be missing or failing.")
        }

        // read permissions
        val result = Shell.cmd("ls -lZ -- $safeGadgetPathString").exec()
        Timber.v("getCharacterDeviceInfo(): ls -lZ exit code=${result.code}, stdout=${result.out}, stderr=${result.err}")

        permissionsString = buildString {
            // Check if command ran successfully
            //if (!result.isSuccess) {
            // oh no, it wasn't successful :(
            //}

            // Check if output seems alright
            val outputLinesList = result.out
            if (outputLinesList.isNotEmpty()) {
                append("stdout: ")
                for (line in outputLinesList) {
                    appendLine(line)
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
        path = gadgetPath.path,
        isPresent = isPresent,
        isVisibleWithoutRoot = isVisibleWithoutRoot,
        permissions = permissionsString,
    )
}

@RequiresRoot
private fun getKernelInfo(): KernelInfo {
    Timber.d("getKernelInfo() called")

    // constants
    val configFsKernelOption = "CONFIG_USB_CONFIGFS"
    val configFsHidKernelOption = "${configFsKernelOption}_F_HID"

    val kernelVersion = System.getProperty("os.version")
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

    Timber.i("getKernelInfo(): version=$kernelVersion, hasConfigFsSupport=$hasConfigFsSupport, hasConfigFsHidFunctionSupport=$hasConfigFsHidFunctionSupport")

    return KernelInfo(
        kernelVersion ?: "unknown",
        kernelConfigAnnotatedString,
        hasConfigFsSupport,
        hasConfigFsHidFunctionSupport,
    )
}

@RequiresRoot
private fun getKernelConfig(): List<String> {
    Timber.d("getKernelConfig() called")
    val commandResult = Shell.cmd("gunzip -c /proc/config.gz | grep -i configfs").exec()
    Timber.v("getKernelConfig(): exit code=${commandResult.code}, out lines count=${commandResult.out.size}")

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
        Timber.e("failed to read kernel config or no configfs options found in /proc/config.gz (exit code=${commandResult.code}, err=${commandResult.err})")
    }

    return kernelConfigLinesList
}

@RequiresRoot
private fun getUsbGadgetSystemInfo(): UsbGadgetSystemInfo {
    Timber.d("getUsbGadgetSystemInfo() called")
    val selinuxResult = Shell.cmd("getenforce").exec()
    val selinuxMode = selinuxResult.out.firstOrNull()?.trim()
    Timber.v("getUsbGadgetSystemInfo(): SELinux mode = $selinuxMode")

    val sysUsbController = Shell.cmd("getprop sys.usb.controller").exec().out.firstOrNull()?.trim()
    val sysUsbState = Shell.cmd("getprop sys.usb.state").exec().out.firstOrNull()?.trim()
    val sysUsbConfig = Shell.cmd("getprop sys.usb.config").exec().out.firstOrNull()?.trim()
    Timber.v("getUsbGadgetSystemInfo(): sys.usb.controller=$sysUsbController, state=$sysUsbState, config=$sysUsbConfig")

    fun commandResultToStringList(result: Shell.Result): List<String> = buildList {
        if (result.out.isEmpty()) {
            add("stdout: (empty)")
        } else {
            add("stdout:")
            addAll(result.out)
        }

        if (result.err.isEmpty()) {
            add("stderr: (empty)")
        } else {
            add("stderr:")
            addAll(result.err)
        }
    }

    val udcListingLines = Shell.cmd("ls -la /sys/class/udc").exec().let {
        val lines = commandResultToStringList(it)
        Timber.v("getUsbGadgetSystemInfo(): /sys/class/udc = $lines")
        lines
    }

    val usbGadgetListingLines = Shell.cmd("ls -la /config/usb_gadget").exec().let {
        val lines = commandResultToStringList(it)
        Timber.v("getUsbGadgetSystemInfo(): /config/usb_gadget = $lines")
        lines
    }

    val characterDevicesListingLines = Shell.cmd("ls -laZ /dev/hid*").exec().let {
        val lines = commandResultToStringList(it)
        Timber.v("getUsbGadgetSystemInfo(): /dev/hid* = $lines")
        lines
    }

    return UsbGadgetSystemInfo(
        selinuxMode = selinuxMode,
        sysUsbController = sysUsbController,
        sysUsbState = sysUsbState,
        sysUsbConfig = sysUsbConfig,
        udcListing = udcListingLines,
        usbGadgetListing = usbGadgetListingLines,
        characterDevicesListing = characterDevicesListingLines,
    )
}

/**
 * Should only be called if you know you have root permissions
 */
annotation class RequiresRoot

@Composable
fun ExportLogsButton() {
    val mainViewModel: MainViewModel = viewModel()
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        // If the user doesn't choose a location to save the file, don't continue
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult

        Timber.v("selected file URI: %s", uri)

        val troubleshootingInfo = detectIssues()
        mainViewModel.syncLogsToMainProcess()
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

private fun StringBuilder.appendDivider(): StringBuilder =
    appendLine().appendLine("------------------------------")

private fun saveLogFile(context: Context, uri: Uri, troubleshootingInfo: TroubleshootingInfo) {
    try {
        val logString = buildString {
            val deviceInfo = troubleshootingInfo.deviceInfo
            deviceInfo.let {
                appendLine("Device Information:")
                appendLine("Manufacturer: ${it.manufacturer}")
                appendLine("Model: ${it.model}")
                appendLine("Brand: ${it.brand}")
                appendLine("Device: ${it.device}")
                appendLine("Product: ${it.product}")
                appendLine("Board: ${it.board}")
                appendLine("Hardware: ${it.hardware}")
                appendLine("SDK Int: ${it.sdkInt}")
                appendLine("Build ID: ${it.buildId}")
                appendDivider()
            }

            val rootPermissionInfo = troubleshootingInfo.rootPermissionInfo
            rootPermissionInfo.let {
                appendLine("Do we have root permissions?: ${it.hasRootPermissions}")
                appendLine("Root method: ${it.rootMethod.name}")
                appendDivider()
            }

            val usbGadgetSystemInfo = troubleshootingInfo.usbGadgetSystemInfo
            usbGadgetSystemInfo?.let {
                appendLine("SELinux Mode: ${it.selinuxMode}")
                appendLine("sys.usb.controller: ${it.sysUsbController}")
                appendLine("sys.usb.state: ${it.sysUsbState}")
                appendLine("sys.usb.config: ${it.sysUsbConfig}")

                appendLsOutputLines("/sys/class/udc", it.udcListing)
                appendLsOutputLines("/config/usb_gadget", it.usbGadgetListing)
                appendLsOutputLines("/dev/hid*", it.characterDevicesListing)
            } ?: run {
                appendLine("usbGadgetSystemInfo is null")
            }
            appendDivider()

            val characterDevicesInfoList = troubleshootingInfo.characterDevicesInfoList
            characterDevicesInfoList?.let {
                appendLine("Character Devices Info:")
                it.forEach { characterDevice ->
                    appendLine("character device info for: ${characterDevice.path}")
                    appendLine("does it exist?: ${characterDevice.isPresent}")
                    appendLine("is it visible without root?: ${characterDevice.isVisibleWithoutRoot}")
                    appendLine("permissions: ")
                    appendLine(characterDevice.permissions)

                    appendLine()
                }
            } ?: run {
                appendLine("character device info list is null")
            }
            appendDivider()

            val kernelInfo = troubleshootingInfo.kernelInfo
            kernelInfo?.let {
                appendLine("Kernel Info:")
                appendLine("version: ${it.version}")
                appendLine("has ConfigFS support?: ${it.hasConfigFsSupport}")
                appendLine("has ConfigFS HID function support?: ${it.hasConfigFsHidFunctionSupport}")
                appendLine("-")
                appendLine("relevant snippet of kernel config: ")
                appendLine(it.kernelConfigAnnotated.text)
            } ?: run {
                appendLine("kernel info is null, that's bad.")
            }
            appendDivider()

            // Append all logs
            appendLine("Logs: ")
            for (entry in LogBuffer.getLogList()) {
                appendLine(entry.toString())
            }
        }

        Timber.v(logString)

        // Write out file
        context.contentResolver.openOutputStream(uri).use { outputStream ->
            if (outputStream == null) {
                Timber.e("Failed to open output stream for writing log file.")
                return
            }
            outputStream.write(logString.toByteArray())
        }

        Timber.i("Successfully exported logs")
    } catch (e: IOException) {
        Timber.e(e)
        Timber.e("IOException occurred while exporting logs")
    }
}

private fun StringBuilder.appendLsOutputLines(label: String, lines: List<String>) {
    appendLine("listing for: '$label'")

    if (lines.isEmpty()) {
        appendLine("  (empty or unreadable)")
    } else {
        for (line in lines) {
            appendLine("  $line")
        }
    }
}
