package me.arianb.usb_hid_client.unit_ui

import android.view.ContextThemeWrapper
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.arianb.usb_hid_client.R
import me.arianb.usb_hid_client.hid_utils.KeyboardDevicePath
import me.arianb.usb_hid_client.input_views.DirectInputKeyboardView
import me.arianb.usb_hid_client.report_senders.CharDeviceTransport
import me.arianb.usb_hid_client.report_senders.KeySender
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.time.Duration.Companion.seconds

class DirectInputKeyboardViewTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun sendKeyEvent_beforeInputConnectionCreated_returnsFalse() {
        val handled = withViewOnMainThread {
            sendKeyEvent(KEY_A_DOWN)
        }

        assertFalse(handled)
    }

    @Test
    fun onCreateInputConnection_beforeSetKeySender_doesNotCrash() {
        val handled = withViewOnMainThread {
            onCreateInputConnection(EditorInfo())
            sendKeyEvent(KEY_A_DOWN)
        }

        assertFalse(handled)
    }

    @Test
    fun sendKeyEvent_routesToLatestKeySenderAfterUpdate() = runBlocking {
        val firstSender = KeySender(CharDeviceTransport(KeyboardDevicePath(tempFolder.newFile("hidg0").absolutePath)))
        val secondSender = KeySender(CharDeviceTransport(KeyboardDevicePath(tempFolder.newFile("hidg1").absolutePath)))
        val receivingSender = CompletableDeferred<KeySender>()

        val senderJobs = listOf(firstSender, secondSender).map { sender ->
            launch {
                sender.start(
                    onSuccess = { receivingSender.complete(sender) },
                    onError = { receivingSender.completeExceptionally(IllegalStateException("Unexpected report error: $it")) },
                )
            }
        }

        try {
            val handled = withViewOnMainThread {
                setKeySender(firstSender)
                onCreateInputConnection(EditorInfo())
                setKeySender(secondSender)
                sendKeyEvent(KEY_A_DOWN)
            }

            assertTrue(handled)
            assertSame(secondSender, withTimeout(TEST_TIMEOUT) { receivingSender.await() })
        } finally {
            senderJobs.forEach(Job::cancel)
        }
    }

    companion object {
        private val TEST_TIMEOUT = 5.seconds
        private val KEY_A_DOWN = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A)

        private fun <T> withViewOnMainThread(block: DirectInputKeyboardView.() -> T): T {
            var result: Result<T>? = null
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val themedContext = ContextThemeWrapper(
                    ApplicationProvider.getApplicationContext(),
                    R.style.Theme_USBHIDClient,
                )
                result = runCatching { DirectInputKeyboardView(themedContext).block() }
            }
            return result!!.getOrThrow()
        }
    }
}
