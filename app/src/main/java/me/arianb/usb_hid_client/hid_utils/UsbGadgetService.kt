package me.arianb.usb_hid_client.hid_utils

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.os.RemoteException
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ipc.RootService
import me.arianb.usb_hid_client.BuildConfig
import me.arianb.usb_hid_client.hid_utils.UsbGadgetManager.createCharacterDevices
import me.arianb.usb_hid_client.hid_utils.UsbGadgetManager.deleteCharacterDevices
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
import kotlin.io.path.listDirectoryEntries
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
//        Timber.plant(ProductionTree())
    }

    private val mMessenger: Messenger by lazy {
        Messenger(
            Handler(Looper.getMainLooper(), MessageHandler())
        )
    }

    internal class MessageHandler : Handler.Callback {
        override fun handleMessage(msg: Message): Boolean {
            Timber.d("Message received in service, running with UID = ${Process.myUid()}")
            try {
                when (msg.what) {
                    MSG_CREATE -> createCharacterDevices()
                    MSG_DELETE -> deleteCharacterDevices()
                    else -> {
                        Timber.w("Unhandled message: $msg")
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
    }

    override fun onBind(intent: Intent): IBinder {
        Timber.d("UsbGadgetService onBind() called")
        return mMessenger.binder
    }

    override fun onUnbind(intent: Intent): Boolean {
        Timber.d("UsbGadgetService onUnbind() called")
        return super.onUnbind(intent)
    }

    companion object {
        const val MSG_CREATE = 0
        const val MSG_DELETE = 1
    }
}

class UsbGadgetServiceConnection : ServiceConnection {
    private var mService: Messenger? = null

    val isBound: Boolean
        get() = mService != null

    override fun onServiceConnected(className: ComponentName, service: IBinder) {
        mService = Messenger(service)
    }

    override fun onServiceDisconnected(className: ComponentName) {
        // This is called when the connection with the service has been
        // unexpectedly disconnected; that is, its process crashed.
        mService = null
    }

    private fun send(messageType: Int) {
        if (!isBound) {
            Timber.w("Attempted to communicate with service using unbound connection")
            return
        }

        val msg = Message.obtain(null, messageType)
        try {
            mService!!.send(msg)
        } catch (e: RemoteException) {
            Timber.e(e)
        }
    }

    fun createGadget() {
        send(UsbGadgetService.MSG_CREATE)
    }

    fun deleteGadget() {
        send(UsbGadgetService.MSG_DELETE)
    }
}

@OptIn(ExperimentalUnsignedTypes::class)
internal object UsbGadgetManager {
    private val CONFIG_FS_PATH: Path = Path("/config/usb_gadget")
    private val USB_GADGET_PATH: Path = determineGadgetPath()

    private val CONFIGS_PATH: Path = USB_GADGET_PATH / "configs/b.1/"
    private val FUNCTIONS_PATH: Path = USB_GADGET_PATH / "functions/"

    private data class HidFunction(
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
            reportDescriptor = ubyteArrayOf(0x05u,0x0Du,0x09u,0x05u,0xA1u,0x01u,0x85u,0x04u,0x09u,0x22u,0xA1u,0x02u,0x15u,0x00u,0x25u,0x01u,0x09u,0x47u,0x09u,0x42u,0x95u,0x02u,0x75u,0x01u,0x81u,0x02u,0x75u,0x01u,0x95u,0x02u,0x81u,0x03u,0x95u,0x01u,0x75u,0x04u,0x25u,0x0Fu,0x09u,0x51u,0x81u,0x02u,0x05u,0x01u,0x15u,0x00u,0x26u,0xC4u,0x09u,0x75u,0x10u,0x55u,0x0Du,0x65u,0x11u,0x09u,0x30u,0x35u,0x00u,0x46u,0x88u,0x13u,0x95u,0x01u,0x81u,0x02u,0x46u,0x10u,0x27u,0x26u,0x88u,0x13u,0x26u,0x88u,0x13u,0x09u,0x31u,0x81u,0x02u,0x05u,0x0Du,0x15u,0x00u,0x25u,0x64u,0x95u,0x03u,0xC0u,0x55u,0x0Cu,0x66u,0x01u,0x10u,0x47u,0xFFu,0xFFu,0x00u,0x00u,0x27u,0xFFu,0xFFu,0x00u,0x00u,0x75u,0x10u,0x95u,0x01u,0x09u,0x56u,0x81u,0x02u,0x09u,0x54u,0x25u,0x7Fu,0x95u,0x01u,0x75u,0x08u,0x81u,0x02u,0x05u,0x09u,0x09u,0x01u,0x25u,0x01u,0x75u,0x01u,0x95u,0x01u,0x81u,0x02u,0x95u,0x07u,0x81u,0x03u,0x09u,0xC5u,0x75u,0x08u,0x95u,0x02u,0x81u,0x03u,0x05u,0x0Du,0x85u,0x02u,0x09u,0x55u,0x09u,0x59u,0x75u,0x04u,0x95u,0x02u,0x25u,0x0Fu,0xB1u,0x02u,0x85u,0x07u,0x09u,0x60u,0x75u,0x01u,0x95u,0x01u,0x15u,0x00u,0x25u,0x01u,0xB1u,0x02u,0x95u,0x0Fu,0xB1u,0x03u,0x06u,0x00u,0xFFu,0x06u,0x00u,0xFFu,0x85u,0x06u,0x09u,0xC5u,0x15u,0x00u,0x26u,0xFFu,0x00u,0x75u,0x08u,0x96u,0x00u,0x01u,0xB1u,0x02u,0xC0u)
            // @formatter:on
        )
    )

    private fun determineGadgetPath(): Path {
        var currentGadgetPath: Path = CONFIG_FS_PATH / "g1"
        if (currentGadgetPath.isDirectory()) {
            return currentGadgetPath
        }

        currentGadgetPath = CONFIG_FS_PATH / "g2"
        if (currentGadgetPath.isDirectory()) {
            return currentGadgetPath
        }

        val gadgetPaths = runCatching { CONFIG_FS_PATH.listDirectoryEntries() }.getOrNull() ?: run {
            Timber.e("Failed to list directory entries at path: $CONFIG_FS_PATH")
            emptyList()
        }

        if (gadgetPaths.isEmpty()) {
            // FIXME: replace this
            throw RuntimeException("no udc :(")
        } else {
            for (path in gadgetPaths) {
                if ((path / "UDC").isRegularFile()) {
                    return path
                }
            }

            return gadgetPaths.first()
        }
    }

    fun createCharacterDevices() {
        // TODO:
        //  check if symlinks already exist in configs dir bc if they do, writes will fail due to "device or resource busy",
        //  which is reasonable, since the function would be active.

        for (hidFunction in allHidFunctions) {
            try {
                addHidFunction(hidFunction)
            } catch (e: IOException) {
                Timber.e("Failed to add '${hidFunction.name}' function to usb gadget")
                Timber.e(e)
            }
        }

        linkFunctionsToConfig(allHidFunctions)

        try {
            resetGadget()
        } catch (e: IOException) {
            Timber.e("Failed to reset usb gadget")
            Timber.e(e)
        }
    }

    private fun Path.writeAsString(uByte: UByte) = writeAsString(uByte.toUInt())

    private fun Path.writeAsString(uInt: UInt) = writeText(uInt.toString())

    @Throws(IOException::class)
    private fun addHidFunction(function: HidFunction) {
        function.functionPath.let {
            // Ensure this directory (and all its parents) exist
            it.createDirectories()

            (it / "protocol").writeAsString(function.protocol)
            (it / "subclass").writeAsString(function.subclass)
            try {
                // Not critical, so don't let this one fail the whole operation
                (it / "no_out_endpoint").writeAsString(1u)
            } catch (e: IOException) {
                Timber.w(e)
            }
            (it / "report_length").writeAsString(function.reportLength)
            (it / "report_desc").writeBytes(function.reportDescriptor.asByteArray())
        }
    }

    private fun linkFunctionsToConfig(functions: Array<HidFunction>) {
        if (functions.isEmpty()) {
            // FIXME: handle this?
            Timber.w("bruh it's empty")
            return
        }

        // Ensure this directory (and all its parents) exist
        try {
            CONFIGS_PATH.createDirectories()
        } catch (e: IOException) {
            Timber.e("IOException occurred while trying to create all directories in path: $CONFIGS_PATH")
            Timber.e(e)
        }

        functions.forEach {
            try {
                it.configPath.createSymbolicLinkPointingTo(it.functionPath)
            } catch (e: java.nio.file.FileAlreadyExistsException) {
                // NOTE: it's extremely important to make sure you catch Java's FileAlreadyExistsException, not Kotlin's
                Timber.w("Attempted to create a symlink in a location that already had a file")
                Timber.d(e)
            }
        }
    }

    @Throws(IOException::class)
    private fun resetGadget() {
        val udcPath: Path = USB_GADGET_PATH / "UDC"

        val udc: String? = System.getProperty("sys.usb.controller")

        udcPath.writer(options = arrayOf(StandardOpenOption.SYNC)).use {
            // For some reason, it was refusing to clear without writing a newline, other whitespace didn't seem to work.
            it.write("\n")

            // This part seems to happen implicitly
            if (udc != null) {
                it.write(udc)
            }
        }
    }

    @OptIn(ExperimentalPathApi::class)
    fun deleteCharacterDevices() {
        for (hidFunction in allHidFunctions) {
            try {
                // Clear out function configuration directory (should just point to function path)
                hidFunction.configPath.deleteRecursively()

                // Delete function directories
                hidFunction.functionPath.deleteIfExists()
            } catch (e: IOException) {
                Timber.e("Failed to remove '${hidFunction.name}' function from usb gadget")
                Timber.e(e)
            }

            // Apply changes
            try {
                resetGadget()
            } catch (e: IOException) {
                Timber.e("Failed to reset usb gadget")
                Timber.e(e)
            }

            // Delete character devices
            CharacterDeviceManager.ALL_CHARACTER_DEVICE_PATHS.map { Path(it) }.forEach {
                it.deleteIfExists()
            }
        }
    }
}

@JvmInline
value class UsbGadgetPath(val path: String) {}
