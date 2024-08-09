package me.arianb.usb_hid_client.hid_utils

import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os.write
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder

private external fun createDevice(path: String): Int
//private external fun destroyDevice(fd: Int)

//private external fun getCreateEvent(): ByteArray
private external fun getDestroyEvent(): ByteArray

const val NATIVE_PROJECT_LIB_NAME = "usb_hid_client"

object UHID {
    const val PATH: String = "/dev/uhid"
    private val fd: Int

    init {
        System.loadLibrary(NATIVE_PROJECT_LIB_NAME)

        fd = createDevice(PATH)
    }

    // This method serves two purposes over existing write() methods:
    //  1. Trying to write to /dev/uhid from the JVM throws EINVAL unless I dup the file descriptor, still don't know why.
    //  2. I can catch all thrown exceptions for the safety of the caller.
    private fun write(byteArray: ByteArray) {
        val pfd = ParcelFileDescriptor.fromFd(fd)
        pfd.use {
            try {
                write(it.fileDescriptor, byteArray, 0, byteArray.size)
            } catch (e: ErrnoException) {
                Timber.e(e)
            } catch (e: InterruptedException) {
                Timber.w(e)
            }
        }
    }

    fun sendHidEvent(hidEventBytes: ByteArray) {
        val uhidEventBytes = hidEventToUhidEvent(hidEventBytes)

        write(uhidEventBytes)
    }

    // TODO: figure out when I ever actually need to destroy this?
    fun destroy() {
        val destroyEvent = getDestroyEvent()

        write(destroyEvent)
        // fd is now closed, TODO: make code safer against continuing to use the closed fd
    }

    private fun hidEventToUhidEvent(hidBytes: ByteArray): ByteArray {
        val uhidEventType: Int = UHIDEventType.UHID_INPUT2.ordinal
        val reportSize: Short = hidBytes.size.toShort()

        val uhidByteBuffer = ByteBuffer.allocate(Int.SIZE_BYTES + Short.SIZE_BYTES + hidBytes.size)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(uhidEventType)
            .putShort(reportSize)
            .put(hidBytes)

        return uhidByteBuffer.array()
    }
}

// From linux/uhid.h
//
// Suppressing "unused" because these are enums for a kernel API, so I can't just remove unused ones unless I wanna
// hardcode the values, which is annoying, error-prone, and makes it so I need to go check the headers again if I end
// up needing to add one of these back.
//
// Suppressing "EnumEntryName" because I like the "__Foo" legacy naming to ensure I don't accidentally use them
@Suppress("unused", "EnumEntryName")
internal enum class UHIDEventType {
    __UHID_LEGACY_CREATE,
    UHID_DESTROY,
    UHID_START,
    UHID_STOP,
    UHID_OPEN,
    UHID_CLOSE,
    UHID_OUTPUT,
    __UHID_LEGACY_OUTPUT_EV,
    __UHID_LEGACY_INPUT,
    UHID_GET_REPORT,
    UHID_GET_REPORT_REPLY,
    UHID_CREATE2,
    UHID_INPUT2,
    UHID_SET_REPORT,
    UHID_SET_REPORT_REPLY,
}
