package me.arianb.usb_hid_client

import me.arianb.usb_hid_client.hid_utils.KeyboardDevicePath
import me.arianb.usb_hid_client.report_senders.CharDeviceTransport
import me.arianb.usb_hid_client.report_senders.ReportWriteError
import me.arianb.usb_hid_client.report_senders.UhidTransport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Path

class ReportTransportTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `CharDeviceTransport classifies missing FileNotFoundException as MissingCharDevice`() {
        val missingPath = KeyboardDevicePath(tempDir.resolve("hidg0").toString())
        val transport = CharDeviceTransport(missingPath)

        val error = transport.classifyError(FileNotFoundException("open failed: ENOENT (No such file or directory)"))

        assertEquals(ReportWriteError.MissingCharDevice(missingPath), error)
    }

    @Test
    fun `CharDeviceTransport classifies permission-denied FileNotFoundException on existing file as BrokenPermissions`() {
        val existingFile = tempDir.resolve("hidg0").toFile().apply { createNewFile() }
        val existingPath = KeyboardDevicePath(existingFile.absolutePath)
        val transport = CharDeviceTransport(existingPath)

        val error = transport.classifyError(FileNotFoundException("open failed: EACCES (Permission denied)"))

        assertEquals(ReportWriteError.BrokenPermissions(existingPath), error)
    }

    @Test
    fun `CharDeviceTransport classifies errno 108 as DeviceUnplugged`() {
        val transport = CharDeviceTransport(KeyboardDevicePath(tempDir.resolve("hidg0").toString()))

        val error = transport.classifyError(IOException("write failed: ESHUTDOWN (errno 108)"))

        assertEquals(ReportWriteError.DeviceUnplugged, error)
    }

    @Test
    fun `CharDeviceTransport classifies ENXIO as GadgetDisabled`() {
        val transport = CharDeviceTransport(KeyboardDevicePath(tempDir.resolve("hidg0").toString()))

        val error = transport.classifyError(IOException("open failed: ENXIO (No such device or address)"))

        assertEquals(ReportWriteError.GadgetDisabled, error)
    }

    @Test
    fun `CharDeviceTransport classifies unrecognized IOException as Unknown`() {
        val transport = CharDeviceTransport(KeyboardDevicePath(tempDir.resolve("hidg0").toString()))
        val cause = IOException("some unexpected failure")

        val error = transport.classifyError(cause)

        assertEquals(ReportWriteError.Unknown(cause), error)
    }

    @Test
    fun `UhidTransport classifies IOException as Unknown`() {
        val cause = IOException("uhid write failed")

        val error = UhidTransport.classifyError(cause)

        assertEquals(ReportWriteError.Unknown(cause), error)
    }
}
