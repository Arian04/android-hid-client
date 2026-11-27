package me.arianb.usb_hid_client.unit_ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import me.arianb.usb_hid_client.input_views.TouchPadButton
import me.arianb.usb_hid_client.settings.AppTheme
import me.arianb.usb_hid_client.ui.theme.USBHIDClientTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TouchPadButtonTest {
    private enum class ButtonEvent { PRESSED, RELEASED }

    @get:Rule
    val composeTestRule = createComposeRule()

    private val events = mutableListOf<ButtonEvent>()

    @Test
    fun initialComposition_doesNotTriggerCallbacks() {
        setContent()
        composeTestRule.waitForIdle()

        assertEquals(emptyList<ButtonEvent>(), events)
    }

    @Test
    fun pressAndRelease_triggersPressedThenReleased() {
        setContent()
        val button = composeTestRule.onNodeWithText(BUTTON_TEXT)

        button.performTouchInput { down(center) }
        composeTestRule.waitForIdle()
        assertEquals(listOf(ButtonEvent.PRESSED), events)

        button.performTouchInput { up() }
        composeTestRule.waitForIdle()
        assertEquals(listOf(ButtonEvent.PRESSED, ButtonEvent.RELEASED), events)
    }

    @Test
    fun pressAndCancel_triggersPressedThenReleased() {
        setContent()
        val button = composeTestRule.onNodeWithText(BUTTON_TEXT)

        button.performTouchInput { down(center) }
        composeTestRule.waitForIdle()
        assertEquals(listOf(ButtonEvent.PRESSED), events)

        button.performTouchInput { cancel() }
        composeTestRule.waitForIdle()
        assertEquals(listOf(ButtonEvent.PRESSED, ButtonEvent.RELEASED), events)
    }

    private fun setContent() {
        composeTestRule.setContent {
            USBHIDClientTheme(
                appTheme = AppTheme.System,
                isDynamicColorEnabled = true,
            ) {
                TouchPadButton(
                    text = BUTTON_TEXT,
                    onPressed = { events.add(ButtonEvent.PRESSED) },
                    onReleased = { events.add(ButtonEvent.RELEASED) },
                )
            }
        }
    }

    companion object {
        private const val BUTTON_TEXT = "Left Button"
    }
}
