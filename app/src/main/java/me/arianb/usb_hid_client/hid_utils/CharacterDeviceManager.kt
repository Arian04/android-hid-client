package me.arianb.usb_hid_client.hid_utils

import android.app.Application
import android.content.res.Resources
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import me.arianb.usb_hid_client.R
import me.arianb.usb_hid_client.shell_utils.RootStateHolder
import timber.log.Timber
import java.io.File

class CharacterDeviceManager private constructor(private val application: Application) {
    private val dispatcher = Dispatchers.IO
    private val rootStateHolder = RootStateHolder.getInstance()

    suspend fun createCharacterDevices() {
        val appResources: Resources = application.resources

        withContext(dispatcher) {
            val commandResult = Shell.cmd(appResources.openRawResource(R.raw.create_char_devices)).exec()
            logShellCommandResult("create devices script", commandResult)

            fixSelinuxPermissions()

            launch {
                for (devicePath in ALL_CHARACTER_DEVICE_PATHS) {
                    try {
                        withTimeout(3000) {
                            // wait until the device file exists before trying to fix its permissions
                            while (!File(devicePath).exists()) {
                                Timber.d("$devicePath doesn't exist yet, sleeping for a bit before trying again...")
                                delay(200)
                            }
                            Timber.d("$devicePath exists now!!!")
                        }
                        fixCharacterDevicePermissions(devicePath)
                    } catch (e: TimeoutCancellationException) {
                        Timber.e("Shell script ran, but we timed out while waiting for character devices to be created.")
                        // FIXME: show this error to the user
                    }
                }
            }
        }
    }

    private fun fixSelinuxPermissions() {
        val selinuxPolicyCommand = "${rootStateHolder.sepolicyCommand} '$SELINUX_POLICY'"
        Shell.cmd(selinuxPolicyCommand).exec()
    }

    fun fixCharacterDevicePermissions(device: String) {
        val appUID: Int = application.applicationInfo.uid

        // Set Linux permissions -> only my app user can r/w to the char device
        val chownCommand = "chown '${appUID}:${appUID}' $device"
        val chmodCommand = "chmod 600 $device"
        Shell.cmd(chownCommand).exec()
        Shell.cmd(chmodCommand).exec()

        // Set SELinux permissions -> only my app's selinux context can r/w to the char device
        val chconCommand = "chcon 'u:object_r:device:s0:${getSelinuxCategories()}' $device"
        Shell.cmd(chconCommand).exec()

        return
    }

    private fun getSelinuxCategories(): String {
        val appDataDirPath: String = application.applicationInfo.dataDir

        // Get selinux context for app
        val commandResult = Shell.cmd("stat -c %C $appDataDirPath").exec()

        val selinuxContextString = commandResult.out.joinToString(separator = "\n").trim()

        // Get the part of the context that I need (categories) by grabbing everything after the last ':'
        val categories = selinuxContextString.substringAfterLast(':')

        // If it hasn't changed, then the previous piece of code failed to get the substring
        if (categories == selinuxContextString) {
            Timber.wtf("Failed to get app's selinux context")
        }

        Timber.d("context (before,after): (%s,%s)", selinuxContextString, categories)

        return categories
    }

    fun deleteCharacterDevices() {
        val appResources: Resources = application.resources

        val commandResult = Shell.cmd(appResources.openRawResource(R.raw.delete_char_devices)).exec()
        logShellCommandResult("delete gadget script", commandResult)

        return
    }

    @ModifiesStateDirectly
    fun characterDeviceMissing(charDevicePath: String): Boolean {
        val isCharDevMissing = if (!ALL_CHARACTER_DEVICE_PATHS.contains(charDevicePath)) {
            true
        } else !File(charDevicePath).exists()

        return isCharDevMissing
    }

    @ModifiesStateDirectly
    fun anyCharacterDeviceMissing(): Boolean {
        for (charDevicePath in ALL_CHARACTER_DEVICE_PATHS) {
            if (!File(charDevicePath).exists()) {
                return true
            }
        }

        return false
    }

    companion object {
        // character device paths
        const val KEYBOARD_DEVICE_PATH = "/dev/hidg0"
        const val TOUCHPAD_DEVICE_PATH = "/dev/hidg1"
        val ALL_CHARACTER_DEVICE_PATHS = listOf(KEYBOARD_DEVICE_PATH, TOUCHPAD_DEVICE_PATH)

        // SeLinux stuff
        private const val SELINUX_DOMAIN = "appdomain"
        private const val SELINUX_POLICY = "allow $SELINUX_DOMAIN device chr_file { getattr open read write }"

        @Volatile
        private var INSTANCE: CharacterDeviceManager? = null
        fun getInstance(application: Application): CharacterDeviceManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE?.let {
                    return it
                }

                val instance = CharacterDeviceManager(application)
                INSTANCE = instance
                instance
            }
        }
    }
}

private fun logShellCommandResult(label: String, commandResult: Shell.Result) {
    with(commandResult) {
        Timber.i("${label}: \nexit code=%d\nstdout=%s\nstderr=%s", code, out, err)
    }
}

// This annotation is to help me ensure that I don't accidentally bypass my ViewModel wrappers when calling certain
// methods from inside ViewModel. For example, by calling CharacterDeviceManager.anyCharacterDeviceMissing directly without
// updating the state at the call site.
@Retention(value = AnnotationRetention.BINARY)
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "Ensure to update state when calling this method. You will usually want to be calling the ViewModel wrappers instead"
)
annotation class ModifiesStateDirectly
