package me.arianb.usb_hid_client

import android.view.KeyEvent
import io.mockk.every
import io.mockk.mockk
import me.arianb.usb_hid_client.hid_utils.KeyCodeTranslation
import me.arianb.usb_hid_client.hid_utils.LEFT_SHIFT_SCAN_CODE
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * Unit tests for [KeyCodeTranslation].
 *
 * TODO: Add tests for `KeyCodeTranslation.Scripting.keyCharToScanCodes()`. That object carries
 *       mutable modifier state across calls (`keyCharToScanCodes_modifier` /
 *       `keyCharToScanCodes_isModifierUsed`), and since `Scripting` is a singleton `object`, that
 *       state persists across test methods too. Needs either careful sequencing within a single
 *       test or a test-only reset hook added to production code (touch base before adding one).
 */
class KeyCodeTranslationTest {

    // ---------- keyCharToScanCodes(Char) ----------

    @ParameterizedTest
    @MethodSource("charToScanCodes")
    fun `keyCharToScanCodes resolves a standard key code`(pair: Pair<Char, Byte>) {
        val keyChar = pair.first
        val expectedScanCode = pair.second

        val result = KeyCodeTranslation.keyCharToScanCodes(keyChar)!!

        assertEquals(expectedScanCode, result.second)
    }

    @ParameterizedTest
    @MethodSource("lowerCaseLetters")
    fun `uppercase letter maps to lowercase scan code with left-shift modifier`(lowercaseKeyChar: Char) {
        val uppercaseScanCode = KeyCodeTranslation.keyCharToScanCodes(lowercaseKeyChar.uppercaseChar())!!
        val lowercaseScanCode = KeyCodeTranslation.keyCharToScanCodes(lowercaseKeyChar)!!

        // lowercase modifier scan code should be zero (no modifier)
        assertEquals(0x0.toByte(), lowercaseScanCode.first)

        // the part of the returned scan code that represents just the actual char, not the modifier
        val lowercaseNonModifierScanCode = lowercaseScanCode.second

        // the scan code for the uppercase letter should be left-shift + the scan code for the lowercase letter
        assertEquals(uppercaseScanCode, Pair(LEFT_SHIFT_SCAN_CODE, lowercaseNonModifierScanCode))
    }

    @Test
    fun `unsupported character returns null`() {
        val result = KeyCodeTranslation.keyCharToScanCodes('§')

        assertNull(result)
    }

    // ---------- keyCodeToScanCode(Int) ----------

    @ParameterizedTest
    @MethodSource("keyCodesToScanCodes")
    fun `keyCodeToScanCode resolves a standard key code`(pair: Pair<Int, Byte>) {
        val keyCode = pair.first
        val expectedScanCode = pair.second

        val result = KeyCodeTranslation.keyCodeToScanCode(keyCode)

        assertEquals(expectedScanCode, result)
    }

    @Test
    fun `keyCodeToScanCode returns null for an unmapped key code`() {
        // KEYCODE_UNKNOWN should never have been added to the map
        val result = KeyCodeTranslation.keyCodeToScanCode(KeyEvent.KEYCODE_UNKNOWN)

        assertNull(result)
    }

    // ---------- isMediaKey(Int) ----------

    @Test
    fun `isMediaKey returns true for volume and playback keys`() {
        assertEquals(true, KeyCodeTranslation.isMediaKey(KeyEvent.KEYCODE_VOLUME_UP))
        assertEquals(true, KeyCodeTranslation.isMediaKey(KeyEvent.KEYCODE_VOLUME_DOWN))
        assertEquals(true, KeyCodeTranslation.isMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT))
        assertEquals(true, KeyCodeTranslation.isMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS))
        assertEquals(true, KeyCodeTranslation.isMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
    }

    @Test
    fun `isMediaKey returns false for a standard key`() {
        assertEquals(false, KeyCodeTranslation.isMediaKey(KeyEvent.KEYCODE_A))
    }

    // ---------- getModifiersScanCode(KeyEvent) ----------

    @Test
    fun `getModifiersScanCode returns zero when no modifiers are active`() {
        val event = mockk<KeyEvent>()
        every { event.modifiers } returns 0

        val result = KeyCodeTranslation.getModifiersScanCode(event)

        assertEquals(0x0.toByte(), result)
    }

    companion object {
        // ---------- Argument Generators ----------
        @JvmStatic
        fun lowerCaseLetters(): Iterable<Char> {
            return 'a'..'z'
        }

        @JvmStatic
        fun charToScanCodes(): Iterable<Pair<Char, Byte>> {
            return listOf(
                Pair('a', 0x04.toByte()),
            )
        }

        @JvmStatic
        fun keyCodesToScanCodes(): Iterable<Pair<Int, Byte>> {
            return listOf(
                Pair(KeyEvent.KEYCODE_A, 0x04.toByte()),
            )
        }
    }
}