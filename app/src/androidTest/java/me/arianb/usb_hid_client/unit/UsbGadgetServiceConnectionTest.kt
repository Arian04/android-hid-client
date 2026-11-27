package me.arianb.usb_hid_client.unit

import android.content.ComponentName
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Message
import android.os.Messenger
import android.os.Parcel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.arianb.usb_hid_client.hid_utils.CharacterDeviceManager
import me.arianb.usb_hid_client.hid_utils.service.UsbGadgetPath
import me.arianb.usb_hid_client.hid_utils.service.UsbGadgetService
import me.arianb.usb_hid_client.hid_utils.service.UsbGadgetServiceConnection
import me.arianb.usb_hid_client.settings.GadgetUserPreferences
import me.arianb.usb_hid_client.troubleshooting.LogBuffer
import me.arianb.usb_hid_client.troubleshooting.LogEntry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class UsbGadgetServiceConnectionTest {
    private val connection = UsbGadgetServiceConnection()
    private lateinit var serviceThread: HandlerThread

    @Before
    fun setUp() {
        LogBuffer.getAndClearLogList()
        serviceThread = HandlerThread("FakeUsbGadgetService").apply { start() }
    }

    @After
    fun tearDown() {
        serviceThread.quitSafely()
        LogBuffer.getAndClearLogList()
    }

    @Test
    fun getLogs_appendsParceledEntriesToLogBuffer() = runWithTimeout {
        val expectedLogs = arrayOf(
            LogEntry(priority = "INFO", tag = "UsbGadgetService", message = "first"),
            LogEntry(priority = "ERROR", tag = "UsbGadgetManager", message = "second", throwableString = "IOException"),
        )
        connectFakeService { msg ->
            val reply = Message.obtain(null, UsbGadgetService.MSG_GET_LOGS).apply {
                data = parcelRoundTrip(Bundle().apply { putParcelableArray(null, expectedLogs) })
            }
            msg.replyTo.send(reply)
        }

        assertTrue(connection.getLogs())
        // ProductionTree is active in the test app process, so UsbGadgetServiceConnection also logs
        // diagnostic entries before appending the synced batch.
        assertEquals(expectedLogs.toList(), LogBuffer.getLogList().takeLast(expectedLogs.size))
    }

    @Test
    fun createGadget_returnsServiceCompletionStatus() = runWithTimeout {
        connectFakeService { replyOperationComplete(it, succeeded = true) }
        assertTrue(connection.createGadget(DEFAULT_PREFS))

        connectFakeService { replyOperationComplete(it, succeeded = false) }
        assertFalse(connection.createGadget(DEFAULT_PREFS))
    }

    @Test
    fun cancellingInFlightRequest_doesNotBlockSubsequentRequests() = runWithTimeout {
        val firstRequestReceived = CompletableDeferred<Unit>()
        connectFakeService { firstRequestReceived.complete(Unit) }

        val firstJob = launch { connection.createGadget(DEFAULT_PREFS) }
        firstRequestReceived.await()
        firstJob.cancelAndJoin()

        connectFakeService { replyOperationComplete(it, succeeded = true) }
        assertTrue(connection.createGadget(DEFAULT_PREFS))
    }

    @Test
    fun onServiceDisconnected_failsInFlightRequestAndAllowsReconnect() = runWithTimeout {
        val requestReceived = CompletableDeferred<Unit>()
        connectFakeService { requestReceived.complete(Unit) }

        val inFlight = async { connection.createGadget(DEFAULT_PREFS) }
        requestReceived.await()
        connection.onServiceDisconnected(FAKE_COMPONENT)

        assertFalse(inFlight.await())
        assertFalse(connection.isBound)

        connectFakeService { replyOperationComplete(it, succeeded = true) }
        assertTrue(connection.createGadget(DEFAULT_PREFS))
    }

    private fun runWithTimeout(block: suspend CoroutineScope.() -> Unit) = runBlocking {
        withTimeout(TEST_TIMEOUT, block)
    }

    private fun connectFakeService(onMessage: (Message) -> Unit) {
        val fakeMessenger = Messenger(
            Handler(serviceThread.looper) { msg ->
                onMessage(msg)
                true
            }
        )
        connection.onServiceConnected(FAKE_COMPONENT, fakeMessenger.binder)
    }

    private fun replyOperationComplete(request: Message, succeeded: Boolean) {
        val reply = Message.obtain(null, UsbGadgetService.MSG_OPERATION_COMPLETE).apply {
            arg1 = request.what
            arg2 = if (succeeded) 1 else 0
        }
        request.replyTo.send(reply)
    }

    companion object {
        private val TEST_TIMEOUT = 5.seconds
        private val FAKE_COMPONENT = ComponentName("me.arianb.usb_hid_client", "FakeUsbGadgetService")
        private val DEFAULT_PREFS = GadgetUserPreferences(
            usbGadgetPath = UsbGadgetPath("/config/usb_gadget/g1"),
            keyboardCharacterDevicePath = CharacterDeviceManager.DEFAULT_KEYBOARD_DEVICE_PATH,
            touchpadCharacterDevicePath = CharacterDeviceManager.DEFAULT_TOUCHPAD_DEVICE_PATH,
            createNewGadgetForFunctions = false,
            disableGadgetFunctionsDuringConfiguration = false,
            enablePrecisionTouchpad = false,
        )

        /**
         * Round-trips a [Bundle] through a [Parcel] to replicate cross-process Binder marshaling
         * (in-process [Messenger] calls skip parceling).
         */
        private fun parcelRoundTrip(bundle: Bundle): Bundle {
            val parcel = Parcel.obtain()
            return try {
                parcel.writeBundle(bundle)
                parcel.setDataPosition(0)
                parcel.readBundle(null)!!
            } finally {
                parcel.recycle()
            }
        }
    }
}
