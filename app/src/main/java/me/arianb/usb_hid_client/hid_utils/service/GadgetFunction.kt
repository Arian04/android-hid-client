package me.arianb.usb_hid_client.hid_utils.service

import timber.log.Timber
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.div
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

internal sealed interface GadgetFunction {
    val name: String

    fun getFunctionPath(functionsDir: Path): Path =
        functionsDir / name

    fun getConfigPath(configsDir: Path): Path =
        configsDir / name

    @Throws(IOException::class)
    fun addFunction(functionsDir: Path)

    @Throws(IOException::class)
    fun deleteFunctionDirectory(functionsDir: Path) {
        val path = getFunctionPath(functionsDir)
        Timber.i("deleteFunctionDirectory(): deleting function directory at $path")
        path.deleteIfExists()
    }
}

@OptIn(ExperimentalUnsignedTypes::class)
internal class HidFunction(
    override val name: String,
    val protocol: UByte,
    val subclass: UByte,
    val reportLength: UInt,
    val reportDescriptor: UByteArray
) : GadgetFunction {

    @Throws(IOException::class)
    override fun addFunction(functionsDir: Path) {
        Timber.i("addFunction() called on: function = $this")

        getFunctionPath(functionsDir).let {
            // Ensure this directory (and all its parents) exist
            Timber.v("Creating directory at path: $it")
            it.createDirectories()

            Timber.v("About to begin writing properties of the HID function to the respective files in $it")

            Timber.v("Writing protocol")
            (it / "protocol").writeAsString(protocol)

            Timber.v("Writing subclass")
            (it / "subclass").writeAsString(subclass)

            try {
                // Not critical, so don't let this one fail the whole operation
                Timber.v("Writing no_out_endpoint")
                (it / "no_out_endpoint").writeAsString(1u)
            } catch (e: IOException) {
                Timber.w(e, "Failed to write no_out_endpoint to ${it / "no_out_endpoint"} (non-critical)")
            }

            Timber.v("Writing report_length")
            (it / "report_length").writeAsString(reportLength)

            Timber.v("Writing report_desc")
            (it / "report_desc").writeBytes(reportDescriptor.asByteArray())
        }

        Timber.i("returning from addFunction() successfully for: $name")
    }

    override fun toString(): String {
        return "HidFunction(name='$name', protocol=$protocol, subclass=$subclass, reportLength=$reportLength, reportDescriptor=${reportDescriptor.toHexString()})"
    }

    companion object {
        internal val keyboardHidFunction = HidFunction(
            "hid.keyboard",
            protocol = 1u,
            subclass = 1u,
            reportLength = 5u,
            reportDescriptor = KEYBOARD_REPORT_DESCRIPTOR
        )

        internal val touchpadHidFunction = HidFunction(
            "hid.touchpad",
            protocol = 2u,
            subclass = 0u,
            reportLength = 12u,
            reportDescriptor = TOUCHPAD_REPORT_DESCRIPTOR
        )

        private fun Path.writeAsString(uByte: UByte) = writeAsString(uByte.toUInt())

        private fun Path.writeAsString(uInt: UInt) = writeText(uInt.toString())
    }
}