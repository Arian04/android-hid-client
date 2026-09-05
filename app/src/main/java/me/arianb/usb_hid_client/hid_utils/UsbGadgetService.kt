package me.arianb.usb_hid_client.hid_utils

import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Parcelable
import android.os.Process
import android.os.RemoteException
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ipc.RootService
import kotlinx.parcelize.Parcelize
import me.arianb.usb_hid_client.BuildConfig
import me.arianb.usb_hid_client.getParcelableCompat
import me.arianb.usb_hid_client.settings.GadgetUserPreferences
import me.arianb.usb_hid_client.troubleshooting.Level
import me.arianb.usb_hid_client.troubleshooting.LogBuffer
import me.arianb.usb_hid_client.troubleshooting.ProductionTree
import timber.log.Timber
import java.io.IOException
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createSymbolicLinkPointingTo
import kotlin.io.path.deleteIfExists
import kotlin.io.path.deleteRecursively
import kotlin.io.path.div
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.isSymbolicLink
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.io.path.writer

class UsbGadgetService : RootService() {
    init {
        // This is called in a different process, so we gotta replant Timber stuff
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            Shell.enableVerboseLogging = true
        }

        Timber.plant(ProductionTree(Level.VERBOSE))
    }

    private val mMessenger: Messenger by lazy {
        Messenger(
            Handler(Looper.getMainLooper(), MessageHandler())
        )
    }

    private class MessageHandler : Handler.Callback {
        override fun handleMessage(msg: Message): Boolean {
            Timber.i("Message (what = ${msg.what}) received in service, running with UID = ${Process.myUid()}")

            try {
                when (msg.what) {
                    MSG_CREATE, MSG_DELETE -> handleGadgetManagerMessage(msg)
                    MSG_GET_LOGS -> sendLogs(msg.replyTo)
                    else -> {
                        Timber.wtf("Unhandled message: $msg")
                        return false
                    }
                }
            } catch (e: Exception) {
                // Catching unhandled exceptions because the logs were complaining about unclosed file descriptors
                // when I didn't
                Timber.w("Oh no, an unhandled exception occurred in UsbGadgetService")
                Timber.w(e)
            }

            return true
        }

        private fun handleGadgetManagerMessage(msg: Message): Boolean {
            val gadgetUserPreferences: GadgetUserPreferences? = run {
                val bundle = msg.data.apply {
                    classLoader = GadgetUserPreferences::class.java.classLoader
                }
                bundle.getParcelableCompat(GADGET_PREF_BUNDLE_KEY)
            }
            if (gadgetUserPreferences == null) {
                Timber.e("Failed to unmarshal GadgetUserPreferences")
                return false
            }
            Timber.v("GadgetUserPreferences = $gadgetUserPreferences")

            val usbGadgetManager = UsbGadgetManager(gadgetUserPreferences)
            when (msg.what) {
                MSG_CREATE -> usbGadgetManager.createCharacterDevices()
                MSG_DELETE -> usbGadgetManager.deleteCharacterDevices()
                else -> {
                    Timber.w("Unhandled message: $msg")
                    return false
                }
            }

            return true
        }

        private fun sendLogs(messenger: Messenger?) {
            if (messenger == null) {
                Timber.w("Attempted to communicate with service using unbound connection")
                return
            }
            val logArray = LogBuffer.getAndClearLogList()
            Timber.d("RootService sending logs array: ${logArray.contentToString()}")
            val msg = Message.obtain(null, MSG_GET_LOGS).apply {
                data.putParcelableArray(null, logArray)
            }

            try {
                messenger.send(msg)
            } catch (e: RemoteException) {
                Timber.e(e)
            }
        }
    }

    override fun onBind(intent: Intent): IBinder {
        Timber.v("UsbGadgetService onBind() called")
        return mMessenger.binder
    }

    override fun onUnbind(intent: Intent): Boolean {
        Timber.v("UsbGadgetService onUnbind() called")

        // TODO: maybe block if service is still doing some work? Not yet sure if that's a good idea though.

        return super.onUnbind(intent)
    }

    companion object {
        const val GADGET_PREF_BUNDLE_KEY = "data"

        const val MSG_CREATE = 0
        const val MSG_DELETE = 1
        const val MSG_GET_LOGS = 2
    }
}

// FIXME: implement CreateNewGadgetForFunctions preference
@OptIn(ExperimentalUnsignedTypes::class)
internal class UsbGadgetManager(val gadgetUserPreferences: GadgetUserPreferences) {
    private val UDC_ROOT_PATH: Path = Path("/sys/class/udc")
    private val CONFIG_FS_PATH: Path = Path("/config/usb_gadget")
    private val USB_GADGET_PATH: Path = determineGadgetPath()
    private val UDC_PATH: Path = USB_GADGET_PATH / "UDC"

    private val CONFIGS_PATH: Path = USB_GADGET_PATH / "configs/b.1/"
    private val FUNCTIONS_PATH: Path = USB_GADGET_PATH / "functions/"

    private val allCharacterDevicePaths: List<DevicePath> = gadgetUserPreferences.allCharacterDevicePathsList

    private inner class HidFunction(
        val name: String,
        val protocol: UByte,
        val subclass: UByte,
        val reportLength: UInt,
        val reportDescriptor: UByteArray
    ) {
        val functionPath: Path
            get() = FUNCTIONS_PATH / name

        val configPath: Path
            get() = CONFIGS_PATH / name

        override fun toString(): String {
            return "HidFunction(name='$name', protocol=$protocol, subclass=$subclass, reportLength=$reportLength, reportDescriptor=${reportDescriptor.toHexString()}, functionPath=$functionPath, configPath=$configPath)"
        }
    }

    private val allHidFunctions = arrayOf(
        HidFunction(
            "hid.keyboard",
            protocol = 1u,
            subclass = 1u,
            reportLength = 4u,
            // @formatter:off
            reportDescriptor = ubyteArrayOf(0x05u,0x01u,0x09u,0x06u,0xA1u,0x01u,0x85u,0x01u,0x75u,0x01u,0x95u,0x08u,0x05u,0x07u,0x19u,0xE0u,0x29u,0xE7u,0x15u,0x00u,0x25u,0x01u,0x81u,0x02u,0x75u,0x01u,0x95u,0x08u,0x81u,0x03u,0x95u,0x02u,0x75u,0x08u,0x15u,0x00u,0x25u,0xFFu,0x05u,0x07u,0x19u,0x00u,0x29u,0xFFu,0x81u,0x00u,0xC0u,0x05u,0x0Cu,0x09u,0x01u,0xA1u,0x01u,0x85u,0x02u,0x75u,0x10u,0x95u,0x01u,0x26u,0xFFu,0x07u,0x19u,0x00u,0x2Au,0xFFu,0x07u,0x81u,0x00u,0xC0u)
            // @formatter:on
        ), HidFunction(
            "hid.touchpad",
            protocol = 2u,
            subclass = 0u,
            reportLength = 12u,
            // @formatter:off
            reportDescriptor = ubyteArrayOf(0x05u, 0x01u, 0x09u, 0x02u, 0xa1u, 0x01u, 0x85u, 0x01u, 0x09u, 0x01u, 0xa1u, 0x00u, 0x05u, 0x09u, 0x19u, 0x01u, 0x29u, 0x02u, 0x15u, 0x00u, 0x25u, 0x01u, 0x75u, 0x01u, 0x95u, 0x02u, 0x81u, 0x02u, 0x95u, 0x06u, 0x81u, 0x03u, 0x05u, 0x01u, 0x09u, 0x30u, 0x09u, 0x31u, 0x15u, 0x81u, 0x25u, 0x7fu, 0x75u, 0x08u, 0x95u, 0x02u, 0x81u, 0x06u, 0x75u, 0x08u, 0x95u, 0x05u, 0x81u, 0x03u, 0xc0u, 0x06u, 0x00u, 0xffu, 0x09u, 0x01u, 0x85u, 0x0eu, 0x09u, 0xc5u, 0x15u, 0x00u, 0x26u, 0xffu, 0x00u, 0x75u, 0x08u, 0x95u, 0x04u, 0xb1u, 0x02u, 0xc0u, 0x05u, 0x0du, 0x09u, 0x05u, 0xa1u, 0x01u, 0x85u, 0x04u, 0x09u, 0x22u, 0xa1u, 0x02u, 0x15u, 0x00u, 0x25u, 0x01u, 0x09u, 0x47u, 0x09u, 0x42u, 0x95u, 0x02u, 0x75u, 0x01u, 0x81u, 0x02u, 0x75u, 0x01u, 0x95u, 0x02u, 0x81u, 0x03u, 0x95u, 0x01u, 0x75u, 0x04u, 0x25u, 0x0fu, 0x09u, 0x51u, 0x81u, 0x02u, 0x05u, 0x01u, 0x15u, 0x00u, 0x26u, 0xc8u, 0x0du, 0x75u, 0x10u, 0x55u, 0x0du, 0x65u, 0x11u, 0x09u, 0x30u, 0x35u, 0x00u, 0x46u, 0x88u, 0x13u, 0x95u, 0x01u, 0x81u, 0x02u, 0x46u, 0x10u, 0x27u, 0x26u, 0x88u, 0x13u, 0x26u, 0x88u, 0x13u, 0x09u, 0x31u, 0x81u, 0x02u, 0x05u, 0x0du, 0x15u, 0x00u, 0x25u, 0x64u, 0x95u, 0x03u, 0xc0u, 0x55u, 0x0cu, 0x66u, 0x01u, 0x10u, 0x47u, 0xffu, 0xffu, 0x00u, 0x00u, 0x27u, 0xffu, 0xffu, 0x00u, 0x00u, 0x75u, 0x10u, 0x95u, 0x01u, 0x09u, 0x56u, 0x81u, 0x02u, 0x09u, 0x54u, 0x25u, 0x7fu, 0x95u, 0x01u, 0x75u, 0x08u, 0x81u, 0x02u, 0x05u, 0x09u, 0x09u, 0x01u, 0x25u, 0x01u, 0x75u, 0x01u, 0x95u, 0x01u, 0x81u, 0x02u, 0x95u, 0x07u, 0x81u, 0x03u, 0x09u, 0xc5u, 0x75u, 0x08u, 0x95u, 0x02u, 0x81u, 0x03u, 0x05u, 0x0du, 0x85u, 0x02u, 0x09u, 0x55u, 0x09u, 0x59u, 0x75u, 0x04u, 0x95u, 0x02u, 0x25u, 0x0fu, 0xb1u, 0x02u, 0x85u, 0x07u, 0x09u, 0x60u, 0x75u, 0x01u, 0x95u, 0x01u, 0x15u, 0x00u, 0x25u, 0x01u, 0xb1u, 0x02u, 0x95u, 0x0fu, 0xb1u, 0x03u, 0x06u, 0x00u, 0xffu, 0x06u, 0x00u, 0xffu, 0x85u, 0x06u, 0x09u, 0xc5u, 0x15u, 0x00u, 0x26u, 0xffu, 0x00u, 0x75u, 0x08u, 0x96u, 0x00u, 0x01u, 0xb1u, 0x02u, 0xc0u, 0x05u, 0x0du, 0x09u, 0x0eu, 0xa1u, 0x01u, 0x85u, 0x03u, 0x09u, 0x22u, 0xa1u, 0x00u, 0x09u, 0x52u, 0x15u, 0x00u, 0x25u, 0x0au, 0x75u, 0x08u, 0x95u, 0x01u, 0xb1u, 0x02u, 0xc0u, 0x09u, 0x22u, 0xa1u, 0x00u, 0x85u, 0x05u, 0x09u, 0x57u, 0x09u, 0x58u, 0x75u, 0x01u, 0x95u, 0x02u, 0x25u, 0x01u, 0xb1u, 0x02u, 0x95u, 0x0eu, 0xb1u, 0x03u, 0xc0u, 0xc0u, 0x06u, 0x00u, 0xffu, 0x09u, 0x01u, 0xa1u, 0x01u, 0x85u, 0x5cu, 0x09u, 0x01u, 0x95u, 0x0bu, 0x75u, 0x08u, 0x81u, 0x06u, 0x85u, 0x0du, 0x09u, 0xc5u, 0x15u, 0x00u, 0x26u, 0xffu, 0x00u, 0x75u, 0x08u, 0x95u, 0x04u, 0xb1u, 0x02u, 0x85u, 0x0cu, 0x09u, 0xc6u, 0x96u, 0xf0u, 0x03u, 0x75u, 0x08u, 0xb1u, 0x02u, 0x85u, 0x0bu, 0x09u, 0xc7u, 0x96u, 0x82u, 0x00u, 0x75u, 0x08u, 0xb1u, 0x02u, 0xc0u)
            // @formatter:on
        )
    )

    private fun determineGadgetPath(): Path {
        val pathsToTry: List<Path> = buildList {
            val prefPath = gadgetUserPreferences.usbGadgetPath
            if (prefPath.path.isNotBlank()) {
                this.add(Path(prefPath.path))
            }

            this.add(CONFIG_FS_PATH / "g1")
            this.add(CONFIG_FS_PATH / "g2")
        }

        Timber.v("determineGadgetPath(): trying preferred paths in order: $pathsToTry")

        for (path in pathsToTry) {
            Timber.v("determineGadgetPath(): checking if path isDirectory(): $path")

            if (path.isDirectory()) {
                Timber.i("determineGadgetPath(): found existing directory at preferred path, using it: $path")
                return path
            }
        }

        Timber.w("determineGadgetPath(): none of the preferred paths exist. Falling back to listing entries at: $CONFIG_FS_PATH")

        val gadgetPaths = (runCatching { CONFIG_FS_PATH.listDirectoryEntries() }.getOrNull() ?: run {
            Timber.e("determineGadgetPath(): Failed to list entries at path: $CONFIG_FS_PATH")
            emptyList()
        }).filter { it.isDirectory() }

        Timber.i("determineGadgetPath(): directories found under $CONFIG_FS_PATH: $gadgetPaths")

        if (gadgetPaths.isEmpty()) {
            // TODO: This is WRONG, but it's better than a RuntimeException and I don't have better handling yet
            val fallback = pathsToTry.first()
            Timber.wtf(
                "determineGadgetPath(): no gadget directories found anywhere. Returning an unverified fallback path that " +
                        "almost certainly doesn't exist: $fallback. If things are broken, this is probably the cause."
            )
            return fallback
        } else {
            // Look for gadget with UDC, if we find one, use it
            for (path in gadgetPaths) {
                val udcPathUnderGadget = path / "UDC"
                if (udcPathUnderGadget.isRegularFile()) {
                    Timber.i("determineGadgetPath(): found a gadget directory with a UDC file, using it: $path")

                    return path
                } else {
                    Timber.d("determineGadgetPath(): gadget directory has no UDC file at $udcPathUnderGadget, skipping: $path")
                }
            }

            val fallback = gadgetPaths.first()

            Timber.e("determineGadgetPath(): none of the gadget dirs had a UDC file. Falling back to the first one found: $fallback")

            return fallback
        }
    }

    fun createCharacterDevices() {
        Timber.i("createCharacterDevices() called with preferences: $gadgetUserPreferences")

        // TODO:
        //  check if symlinks already exist in configs dir bc if they do, writes will fail due to "device or resource busy",
        //  which is reasonable, since the function would be active.

        val gadgetFunctionLinksToRestore: List<Pair<Path, Path>> =
            if (gadgetUserPreferences.disableGadgetFunctionsDuringConfiguration) {
                Timber.i("disableGadgetFunctionsDuringConfiguration is true, collecting function links to restore")
                getGadgetFunctionLinksToRestore().apply {
                    // Delete links
                    forEach { (linkPath, _) ->
                        Timber.i("About to attempt to delete link at path: $linkPath")
                        runCatching {
                            linkPath.deleteIfExists()
                        }.onSuccess { deleted ->
                            Timber.v("Deletion of link at '$linkPath': deleted=$deleted")
                        }.onFailure {
                            Timber.e("Failed to delete link at path: $linkPath")
                            Timber.e(it)
                        }
                    }
                }
            } else {
                Timber.i("disableGadgetFunctionsDuringConfiguration is false, skipping existing links deletion")
                emptyList()
            }

        // Disable gadget before configuring it to work around possible device-specific issue
        //
        // Credit to @szescxz on GitHub for finding this workaround
        //   - https://github.com/Arian04/android-hid-client/issues/50#issuecomment-2915345677
        // Credit to @alryaz on GitHub for submitting a PR that I unfortunately couldn't accept, since it was based
        // on outdated code from the main branch
        //  - https://github.com/Arian04/android-hid-client/pull/64
        Timber.i("Attempting to disable USB gadget before configuring HID functions")
        try {
            disableGadget()
            Timber.i("Successfully disabled USB gadget")
        } catch (e: IOException) {
            Timber.e("Failed to disable usb gadget")
            Timber.e(e)
        }

        for (hidFunction in allHidFunctions) {
            Timber.i("Attempting to add HID function: ${hidFunction.name}")
            try {
                addHidFunction(hidFunction)
                Timber.i("Successfully added HID function: ${hidFunction.name}")
            } catch (e: IOException) {
                Timber.e("Failed to add '${hidFunction.name}' function to usb gadget")
                Timber.e(e)
            }
        }

        Timber.i("Linking HID functions to config at $CONFIGS_PATH")
        linkFunctionsToConfig(allHidFunctions)

        Timber.i("about to restore the following symlinks: $gadgetFunctionLinksToRestore")
        gadgetFunctionLinksToRestore.forEach { (linkPath, targetPath) ->
            runCatching {
                linkPath.createSymbolicLinkPointingTo(targetPath)
            }.onSuccess {
                Timber.i("Successfully restored symlink: link=$linkPath -> target=$targetPath")
            }.onFailure {
                Timber.e("ugh it didn't work, here's some info: link=$linkPath target=$targetPath")
                Timber.e(it)
            }
        }

        Timber.i("Attempting to enable/reset USB gadget after configuration")
        try {
            enableGadget()
            Timber.i("Successfully enabled USB gadget")
        } catch (e: IOException) {
            Timber.e("Failed to reset usb gadget")
            Timber.e(e)
        }

        Timber.i("createCharacterDevices() finished")
    }

    private fun getGadgetFunctionLinksToRestore(): List<Pair<Path, Path>> {
        Timber.i("in getGadgetFunctionLinksToRestore()")

        val entries: List<Path> = runCatching { CONFIGS_PATH.listDirectoryEntries() }.getOrNull() ?: run {
            Timber.e("Failed to list directory entries at path: $CONFIGS_PATH")
            emptyList()
        }

        Timber.i("in getGadgetFunctionLinksToRestore(), CONFIGS_PATH.listDirectoryEntries() returned: $entries")

        val links = entries.filter { it.isSymbolicLink() }
        Timber.i("out of those entries, the following satisfy isSymbolicLink(): $links")

        val linkPairs: List<Pair<Path, Path>> = links.mapNotNull {
            runCatching {
                // It is extremely important to get the real path, and not just use the link's target, because using
                // and restoring that relative path target will fail later. I think it's due to some file system
                // weirdness in the way ConfigFS handles symlinks (play around with `ln -s` and you'll see what I mean)
                Pair(it, it.toRealPath())
            }.getOrNull()
        }

        Timber.i("returning linkPairs: $linkPairs")

        return linkPairs
    }

    private fun Path.writeAsString(uByte: UByte) = writeAsString(uByte.toUInt())

    private fun Path.writeAsString(uInt: UInt) = writeText(uInt.toString())

    @Throws(IOException::class)
    private fun addHidFunction(function: HidFunction) {
        Timber.i("addHidFunction() called with: function = $function")

        function.functionPath.let {
            // Ensure this directory (and all its parents) exist
            Timber.v("Creating directory at path: $it")
            it.createDirectories()

            Timber.v("About to begin writing properties of the HID function to the respective files in $it")

            Timber.v("Writing protocol")
            (it / "protocol").writeAsString(function.protocol)

            Timber.v("Writing subclass")
            (it / "subclass").writeAsString(function.subclass)

            try {
                // Not critical, so don't let this one fail the whole operation
                Timber.v("Writing no_out_endpoint")
                (it / "no_out_endpoint").writeAsString(1u)
            } catch (e: IOException) {
                Timber.w(e, "Failed to write no_out_endpoint to ${it / "no_out_endpoint"} (non-critical)")
            }

            Timber.v("Writing report_length")
            (it / "report_length").writeAsString(function.reportLength)

            Timber.v("Writing report_desc")
            (it / "report_desc").writeBytes(function.reportDescriptor.asByteArray())
        }

        Timber.i("returning from addHidFunction() successfully for: ${function.name}")
    }

    private fun linkFunctionsToConfig(functions: Array<HidFunction>) {
        Timber.i("linkFunctionsToConfig() called with: functions = ${functions.contentToString()}")

        if (functions.isEmpty()) {
            // TODO: should I handle this in some way?
            Timber.wtf("LOGIC BUG: linkFunctionsToConfig() was called with an empty array of functions!!!")
            return
        }

        // Ensure this directory (and all its parents) exist
        try {
            Timber.v("Creating all directories within (and up until) path: $CONFIGS_PATH")
            CONFIGS_PATH.createDirectories()
        } catch (e: IOException) {
            Timber.e("IOException occurred while trying to create all directories in path: $CONFIGS_PATH")
            Timber.e(e)
        }

        functions.forEach {
            Timber.v("Creating symlink from path '${it.configPath}' to target path '${it.functionPath}'")
            try {
                it.configPath.createSymbolicLinkPointingTo(it.functionPath)
                Timber.v("Successfully created symlink")
            } catch (e: java.nio.file.FileAlreadyExistsException) {
                // NOTE: it's extremely important to make sure you catch Java's FileAlreadyExistsException, not Kotlin's
                Timber.w(e, "Attempted to create a symlink in a location that already had a file")
            } catch (e: IOException) {
                Timber.e(e, "Failed to create symlink from '${it.configPath}' to '${it.functionPath}'")
            }
        }

        Timber.i("returning from linkFunctionsToConfig()")
    }

    private fun resetGadget() {
        Timber.i("resetGadget() called")

        try {
            Timber.i("disabling USB gadget")
            disableGadget()
        } catch (e: IOException) {
            Timber.w(e, "Failed to disable USB gadget during reset procedure")
        }

        try {
            Timber.i("enabling USB gadget")
            enableGadget()
        } catch (e: IOException) {
            Timber.w(e, "Failed to enable USB gadget during reset procedure")
        }

        Timber.i("returning from resetGadget()")
    }

    @Throws(IOException::class)
    private fun disableGadget() {
        Timber.i("disableGadget(): clearing UDC at path: $UDC_PATH")
        UDC_PATH.writer(options = arrayOf(StandardOpenOption.SYNC)).use {
            // For some reason, it was refusing to clear without writing a newline, other whitespace didn't seem to work.
            it.write("\n")
        }
        Timber.i("disableGadget(): successfully wrote to $UDC_PATH")
    }

    @Throws(IOException::class)
    private fun enableGadget() {
        val udc = getUDC()
        Timber.i("enableGadget(): enabling UDC '$udc' by writing to $UDC_PATH")
        UDC_PATH.writer(options = arrayOf(StandardOpenOption.SYNC)).use {
            // This part seems to happen implicitly
            it.write(udc)
        }
        Timber.i("enableGadget(): successfully wrote UDC")
    }

    @OptIn(ExperimentalPathApi::class)
    fun deleteCharacterDevices() {
        Timber.i("deleteCharacterDevices() called")

        for (hidFunction in allHidFunctions) {
            try {
                // Clear out function configuration directory (should just point to function path)
                Timber.i("deleteCharacterDevices(): deleting function config path recursively at ${hidFunction.configPath}")
                hidFunction.configPath.deleteRecursively()

                // Delete function directories
                Timber.i("deleteCharacterDevices(): deleting function directory at ${hidFunction.functionPath}")
                hidFunction.functionPath.deleteIfExists()
            } catch (e: IOException) {
                Timber.e("Failed to remove '${hidFunction.name}' function from usb gadget")
                Timber.e(e)
            }

            // Apply changes
            Timber.i("deleteCharacterDevices(): resetting gadget")
            resetGadget()

            // Delete character devices
            allCharacterDevicePaths.map { Path(it.path) }.forEach {
                Timber.i("deleteCharacterDevices(): deleting character device at $it")
                it.deleteIfExists()
            }
        }

        Timber.i("deleteCharacterDevices() finished")
    }

    @Throws(IOException::class)
    fun getUDC(): String {
        Timber.i("getUDC() called")

        val udcDirectoryPath = UDC_ROOT_PATH

        // NOTE:
        //  Reading the "sys.usb.controller" property will return null when (I think) the gadget is disabled.
        //  My guess is it returns the *active* UDC, so I can't read the UDC when it's inactive. So we're doing
        //  it this way instead.

        val unfilteredUdcList: List<Path> = run {
            udcDirectoryPath.listDirectoryEntries()
        }
        Timber.d("Unfiltered list of UDCs is: $unfilteredUdcList")

        val udcList = unfilteredUdcList.filter {
            // a "dummy" UDC can be present on some devices, that seems to just act as a loopback gadget that makes
            // the device act as if it is connected to itself. This is unintended, so we need to filter it out.
            !it.fileName.toString().contains("dummy")
        }

        val udcPath: Path = if (udcList.isEmpty()) {
            // TODO: What do we even do at this point
            Timber.wtf("getUDC(): $udcDirectoryPath has no entries at all. This is a known unhandled case (see TODO in source).")
            Path("")
        } else if (udcList.size == 1) {
            Timber.i("getUDC(): exactly one UDC entry found, using it: ${udcList.first()}")
            udcList.first()
        } else {
            Timber.i("getUDC(): more than one UDC entry found ($udcList), attempting to filter down to symlinks")

            // There's more than one, attempt to filter it down I guess
            val filteredList = udcList.filter { it.isSymbolicLink() }

            if (filteredList.isEmpty()) {
                // Just use the unfiltered list I guess
                val fallback = udcList.first()
                Timber.w("getUDC(): filtered list is empty. Falling back to first entry from unfiltered list: $fallback")
                fallback
            } else if (filteredList.size == 1) {
                Timber.i("getUDC(): filtering narrowed it down to exactly one entry, using it: ${filteredList.first()}")
                filteredList.first()
            } else {
                Timber.w("getUDC(): filtered list of UDCs still has more than one, using first one: $filteredList (unfiltered: $udcList)")

                filteredList.first()
            }
        }

        val udc = udcPath.name

        Timber.i("getUDC(): returning UDC: '$udc'")

        return udc
    }
}

@JvmInline
@Parcelize
value class UsbGadgetPath(val path: String) : Parcelable