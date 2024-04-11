package me.arianb.usb_hid_client.hid_utils

import android.content.Context
import android.content.res.Resources
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import me.arianb.usb_hid_client.R
import me.arianb.usb_hid_client.shell_utils.NoRootPermissionsException
import me.arianb.usb_hid_client.shell_utils.RootState
import timber.log.Timber
import java.io.File

class CharacterDevice(context: Context, private val ioDispatcher: CoroutineDispatcher) {
    private val appResources: Resources
    private val appUID: Int
    private val appDataDirPath: String

    init {
        appResources = context.resources
        appUID = context.applicationInfo.uid
        appDataDirPath = context.applicationInfo.dataDir
    }

    @Throws(NoRootPermissionsException::class)
    suspend fun createCharacterDevices(): Boolean {
        return withContext(ioDispatcher) {
            if (!Shell.getShell().isRoot) {
                throw NoRootPermissionsException()
            }

            val createCharDevicesResult = Shell.cmd(appResources.openRawResource(R.raw.create_char_devices)).exec()
            Timber.d("create device script: \nstdout=%s\nstderr=%s", createCharDevicesResult.out, createCharDevicesResult.err)

            fixSelinuxPermissions()

            try {
                withTimeout(3000) {
                    for (devicePath in ALL_CHARACTER_DEVICE_PATHS) {
                        launch {
                            // wait until the device file exists before trying to fix its permissions
                            while (!File(devicePath).exists()) {
                                Timber.d("$devicePath doesn't exist yet, sleeping for 50ms...")
                                delay(100)
                            }
                            Timber.d("$devicePath exists now!!!")
                            fixCharacterDevicePermissions(devicePath)
                        }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Timber.e("Shell script ran, but we timed out while waiting for character devices to be created.")
                return@withContext false
            }
            return@withContext true
        }
    }

    private fun fixSelinuxPermissions() {
        val selinuxDomain = "appdomain"
        val selinuxPolicy = "allow $selinuxDomain device chr_file { getattr open write }"
        val selinuxPolicyCommand = "${RootState.getSepolicyCommand()} '$selinuxPolicy'"
        Shell.cmd(selinuxPolicyCommand).exec()
    }

    @Throws(NoRootPermissionsException::class)
    fun fixCharacterDevicePermissions(device: String) {
        if (!Shell.getShell().isRoot) {
            throw NoRootPermissionsException()
        }

        // Set Linux permissions -> only my app user can r/w to the char device
        val chownCommand = "chown ${appUID}:${appUID} $device"
        val chmodCommand = "chmod 600 $device"
        Shell.cmd(chownCommand).exec()
        Shell.cmd(chmodCommand).exec()

        // Set SELinux permissions -> only my app's selinux context can r/w to the char device
        val chconCommand = "chcon u:object_r:device:s0:${getSelinuxCategories()} $device"
        Shell.cmd(chconCommand).exec()
    }

    private fun getSelinuxCategories(): String {
        // Get selinux context for app
        val commandResult = Shell.cmd("stat -c %C $appDataDirPath").exec()

        // Get the part of the context that I need (categories) by grabbing everything after the last ':'
        val contextFromCommand = java.lang.String.join("\n", commandResult.out)
        var categories = contextFromCommand

        // TODO: handle the case that stdout doesn't include any ':' (idk why that would even happen tho)
        categories = categories.substring(categories.lastIndexOf(':') + 1)
        categories = categories.trim { it <= ' ' } // trim whitespace
        Timber.d("context (before,after): (%s,%s)", contextFromCommand, categories)

        // If it hasn't changed, then the previous piece of code failed to get the substring
        if (categories == contextFromCommand) {
            Timber.e("Failed to get app's selinux context")
        }
        return categories
    }

    companion object {
        // character device paths
        const val KEYBOARD_DEVICE_PATH = "/dev/hidg0"
        const val MOUSE_DEVICE_PATH = "/dev/hidg1"
        private val ALL_CHARACTER_DEVICE_PATHS = listOf(KEYBOARD_DEVICE_PATH, MOUSE_DEVICE_PATH)

        fun characterDeviceMissing(charDevicePath: String): Boolean {
            return if (!ALL_CHARACTER_DEVICE_PATHS.contains(charDevicePath)) {
                true
            } else !File(charDevicePath).exists()
        }

        fun anyCharacterDeviceMissing(): Boolean {
            for (charDevicePath in ALL_CHARACTER_DEVICE_PATHS) {
                if (!File(charDevicePath).exists()) {
                    return true
                }
            }
            return false
        }
    }
}