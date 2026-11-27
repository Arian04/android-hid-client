package me.arianb.usb_hid_client

import io.mockk.mockk
import io.mockk.verifySequence
import me.arianb.usb_hid_client.input_views.touch_input_handlers.MouseInputHandler
import me.arianb.usb_hid_client.report_senders.pointer_device_senders.MouseSender
import me.arianb.usb_hid_client.report_senders.pointer_device_senders.PointerDeviceSender.TouchpadButtonState
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class MouseInputHandlerTest {
    private val mouseSender: MouseSender = mockk(relaxUnitFun = true)
    private val handler = MouseInputHandler(mouseSender)

    // ---------- sendAbsoluteMouseMovement ----------

    @Test
    fun `initial touch sends zero delta and subsequent moves send relative deltas`() {
        handler.sendAbsoluteMouseMovement(100, 200)
        handler.sendAbsoluteMouseMovement(115, 190)
        handler.sendAbsoluteMouseMovement(105, 205)

        verifySequence {
            mouseSender.sendMouseReport(0, 0, NO_BUTTONS)
            mouseSender.sendMouseReport(15, -10, NO_BUTTONS)
            mouseSender.sendMouseReport(-10, 15, NO_BUTTONS)
        }
    }

    @ParameterizedTest
    @MethodSource("largeMovementCases")
    fun `large movements preserve direction and clamp to HID byte range`(case: MovementCase) {
        val (startX, startY, endX, endY, expectedDeltaX, expectedDeltaY) = case

        handler.sendAbsoluteMouseMovement(startX, startY)
        handler.sendAbsoluteMouseMovement(endX, endY)

        verifySequence {
            mouseSender.sendMouseReport(0, 0, NO_BUTTONS)
            mouseSender.sendMouseReport(expectedDeltaX, expectedDeltaY, NO_BUTTONS)
        }
    }

    // ---------- Button State & Relative Movement ----------

    @Test
    fun `pressing a button before first touch does not cause a jump on touch down`() {
        handler.sendButtonStateUpdate(LEFT_PRESSED)
        handler.sendAbsoluteMouseMovement(50, 60)
        handler.sendAbsoluteMouseMovement(55, 62)

        verifySequence {
            mouseSender.sendMouseReport(0, 0, LEFT_PRESSED)
            mouseSender.sendMouseReport(0, 0, LEFT_PRESSED)
            mouseSender.sendMouseReport(5, 2, LEFT_PRESSED)
        }
    }

    @Test
    fun `button state persists across moves and updates cleanly mid-drag`() {
        handler.sendAbsoluteMouseMovement(100, 100)
        handler.sendButtonStateUpdate(LEFT_PRESSED)
        handler.sendAbsoluteMouseMovement(110, 105)
        handler.sendButtonStateUpdate(NO_BUTTONS)
        handler.sendAbsoluteMouseMovement(108, 115)

        verifySequence {
            mouseSender.sendMouseReport(0, 0, NO_BUTTONS)
            mouseSender.sendMouseReport(0, 0, LEFT_PRESSED)
            mouseSender.sendMouseReport(10, 5, LEFT_PRESSED)
            mouseSender.sendMouseReport(0, 0, NO_BUTTONS)
            mouseSender.sendMouseReport(-2, 10, NO_BUTTONS)
        }
    }

    @Test
    fun `relative movement sends exact delta and retains current button state when omitted`() {
        handler.sendButtonStateUpdate(LEFT_PRESSED)
        handler.sendRelativeMouseMovement(12, -8, touchpadButtonState = null)

        verifySequence {
            mouseSender.sendMouseReport(0, 0, LEFT_PRESSED)
            mouseSender.sendMouseReport(12, -8, LEFT_PRESSED)
        }
    }

    companion object {
        private val NO_BUTTONS = TouchpadButtonState(isLeftButtonPressed = false, isRightButtonPressed = false)
        private val LEFT_PRESSED = TouchpadButtonState(isLeftButtonPressed = true, isRightButtonPressed = false)

        data class MovementCase(
            val startX: Short,
            val startY: Short,
            val endX: Short,
            val endY: Short,
            val expectedDeltaX: Byte,
            val expectedDeltaY: Byte,
        )

        // ---------- Argument Generators ----------

        @JvmStatic
        fun largeMovementCases(): Iterable<MovementCase> = listOf(
            // Exact HID descriptor boundaries (-127..127)
            MovementCase(startX = 0, startY = 0, endX = 127, endY = 0, expectedDeltaX = 127, expectedDeltaY = 0),
            MovementCase(startX = 127, startY = 0, endX = 0, endY = 0, expectedDeltaX = -127, expectedDeltaY = 0),
            // Positive overflow (> 127)
            MovementCase(startX = 0, startY = 0, endX = 130, endY = 0, expectedDeltaX = 127, expectedDeltaY = 0),
            MovementCase(
                startX = 100,
                startY = 200,
                endX = 600,
                endY = 700,
                expectedDeltaX = 127,
                expectedDeltaY = 127
            ),
            // Negative overflow (< -127, matching HID descriptor logical min 0x81 = -127)
            MovementCase(startX = 128, startY = 0, endX = 0, endY = 0, expectedDeltaX = -127, expectedDeltaY = 0),
            MovementCase(
                startX = 600,
                startY = 700,
                endX = 100,
                endY = 200,
                expectedDeltaX = -127,
                expectedDeltaY = -127
            ),
        )
    }
}
