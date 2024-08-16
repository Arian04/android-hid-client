package me.arianb.usb_hid_client.hid_utils

import android.view.KeyEvent
import android.view.KeyEvent.*
import timber.log.Timber
import kotlin.experimental.or

const val LEFT_SHIFT_SCAN_CODE: Byte = 0x02

object KeyCodeTranslation {
    private val keyEventKeys: MutableMap<Int, String> = mutableMapOf()
    private val hidKeyCodes: MutableMap<String, Byte> = mutableMapOf()

    private val keyEventModifierKeys: MutableMap<Int, String> = mutableMapOf()
    private val hidModifierCodes: MutableMap<String, Byte> = mutableMapOf()

    private val shiftChars: MutableMap<String, String> = mutableMapOf()

    // Converts key to two scan codes
    // First element in pair is the scan code for the modifier
    // Second element in pair is the scan code for the key
    fun keyCharToScanCodes(key: Char): Pair<Byte, Byte>? {
        //Log.d(TAG, "converting following key into scan code: " + key);

        val keyString = key.toString()

        // If key is shift + another key, add left-shift scan code
        val modifier: Byte
        val keyScanCode: Byte?
        if (shiftChars.containsKey(key.toString())) {
            modifier = LEFT_SHIFT_SCAN_CODE
            keyScanCode = hidKeyCodes[shiftChars[keyString]]
        } else if (Character.isUpperCase(key)) {
            modifier = LEFT_SHIFT_SCAN_CODE
            keyScanCode = hidKeyCodes[keyString.lowercase()]
        } else {
            modifier = 0x0
            keyScanCode = hidKeyCodes[keyString]
        }

        if (keyScanCode == null) {
            Timber.e("key: '$keyString' could not be converted to an HID code (it wasn't found in the map)")
            return null
        }

        return Pair(modifier, keyScanCode)
    }

    fun keyCodeToScanCode(keyCode: Int): Byte? =
        hidKeyCodes[keyEventKeys[keyCode]]

    fun getModifiersScanCode(event: KeyEvent): Byte {
        var modifier: Byte = 0x0
        if (event.isShiftPressed) {
            modifier = modifier or modifierKeyCodeToScanCode(META_SHIFT_LEFT_ON)
        }
        if (event.isCtrlPressed) {
            modifier = modifier or modifierKeyCodeToScanCode(META_CTRL_LEFT_ON)
        }
        if (event.isAltPressed) {
            modifier = modifier or modifierKeyCodeToScanCode(META_ALT_LEFT_ON)
        }
        if (event.isMetaPressed) {
            modifier = modifier or modifierKeyCodeToScanCode(META_META_LEFT_ON)
        }

        return modifier
    }

    private fun modifierKeyCodeToScanCode(modifierKeyCode: Int): Byte {
        return hidModifierCodes[keyEventModifierKeys[modifierKeyCode]] ?: 0x0
    }

    // Fill maps
    init {
        // translate standard keys
        putKey(KEYCODE_A, "a", 0x04)
        putKey(KEYCODE_B, "b", 0x05)
        putKey(KEYCODE_C, "c", 0x06)
        putKey(KEYCODE_D, "d", 0x07)
        putKey(KEYCODE_E, "e", 0x08)
        putKey(KEYCODE_F, "f", 0x09)
        putKey(KEYCODE_G, "g", 0x0a)
        putKey(KEYCODE_H, "h", 0x0b)
        putKey(KEYCODE_I, "i", 0x0c)
        putKey(KEYCODE_J, "j", 0x0d)
        putKey(KEYCODE_K, "k", 0x0e)
        putKey(KEYCODE_L, "l", 0x0f)
        putKey(KEYCODE_M, "m", 0x10)
        putKey(KEYCODE_N, "n", 0x11)
        putKey(KEYCODE_O, "o", 0x12)
        putKey(KEYCODE_P, "p", 0x13)
        putKey(KEYCODE_Q, "q", 0x14)
        putKey(KEYCODE_R, "r", 0x15)
        putKey(KEYCODE_S, "s", 0x16)
        putKey(KEYCODE_T, "t", 0x17)
        putKey(KEYCODE_U, "u", 0x18)
        putKey(KEYCODE_V, "v", 0x19)
        putKey(KEYCODE_W, "w", 0x1a)
        putKey(KEYCODE_X, "x", 0x1b)
        putKey(KEYCODE_Y, "y", 0x1c)
        putKey(KEYCODE_Z, "z", 0x1d)

        putKey(KEYCODE_1, "1", 0x1e)
        putKey(KEYCODE_2, "2", 0x1f)
        putKey(KEYCODE_3, "3", 0x20)
        putKey(KEYCODE_4, "4", 0x21)
        putKey(KEYCODE_5, "5", 0x22)
        putKey(KEYCODE_6, "6", 0x23)
        putKey(KEYCODE_7, "7", 0x24)
        putKey(KEYCODE_8, "8", 0x25)
        putKey(KEYCODE_9, "9", 0x26)
        putKey(KEYCODE_0, "0", 0x27)

        // I believe, on the HID side, these are only usually for special keyboard layouts
        // But I'm receiving some of these on the Android side, so I'm handling them
        putKey(KEYCODE_STAR, "*", 0x55) // Keypad *
//        putKey(KEYCODE_POUND, "#", 0xcc.toByte()) // Keypad #
//        putKey(KEYCODE_AT, "@", 0xce.toByte()) // Keypad @
        putKey(KEYCODE_PLUS, "+", 0x57) // Keypad +

        putKey(KEYCODE_MINUS, "-", 0x2d)
        putKey(KEYCODE_EQUALS, "=", 0x2e)
        putKey(KEYCODE_LEFT_BRACKET, "[", 0x2f)
        putKey(KEYCODE_RIGHT_BRACKET, "]", 0x30)
        putKey(KEYCODE_BACKSLASH, "\\", 0x31)
        putKey(KEYCODE_SEMICOLON, ";", 0x33)
        putKey(KEYCODE_APOSTROPHE, "'", 0x34)
        putKey(KEYCODE_GRAVE, "`", 0x35)
        putKey(KEYCODE_COMMA, ",", 0x36)
        putKey(KEYCODE_PERIOD, ".", 0x37)
        putKey(KEYCODE_SLASH, "/", 0x38)

        putKey(KEYCODE_F1, "f1", 0x3a)
        putKey(KEYCODE_F2, "f2", 0x3b)
        putKey(KEYCODE_F3, "f3", 0x3c)
        putKey(KEYCODE_F4, "f4", 0x3d)
        putKey(KEYCODE_F5, "f5", 0x3e)
        putKey(KEYCODE_F6, "f6", 0x3f)
        putKey(KEYCODE_F7, "f7", 0x40)
        putKey(KEYCODE_F8, "f8", 0x41)
        putKey(KEYCODE_F9, "f9", 0x42)
        putKey(KEYCODE_F10, "f10", 0x43)
        putKey(KEYCODE_F11, "f11", 0x44)
        putKey(KEYCODE_F12, "f12", 0x45)

        putKey(KEYCODE_SPACE, " ", 0x2c)
        putKey(KEYCODE_ENTER, "\n", 0x28)

        putKey(KEYCODE_ESCAPE, "escape", 0x29)
        putKey(KEYCODE_DEL, "backspace", 0x2a)
        putKey(KEYCODE_TAB, "tab", 0x2b)
        putKey(KEYCODE_FORWARD_DEL, "delete", 0x4c)
        putKey(KEYCODE_SYSRQ, "print", 0x46)
        putKey(KEYCODE_SCROLL_LOCK, "scroll-lock", 0x47)
        putKey(KEYCODE_NUM_LOCK, "num-lock", 0x53)
        putKey(KEYCODE_BREAK, "pause", 0x48)
        putKey(KEYCODE_INSERT, "insert", 0x49)
        putKey(KEYCODE_MOVE_HOME, "home", 0x4a)
        putKey(KEYCODE_MOVE_END, "end", 0x4d)
        putKey(KEYCODE_PAGE_UP, "page-up", 0x4b)
        putKey(KEYCODE_PAGE_DOWN, "page-down", 0x4e)

        putKey(KEYCODE_DPAD_RIGHT, "right", 0x4f)
        putKey(KEYCODE_DPAD_LEFT, "left", 0x50)
        putKey(KEYCODE_DPAD_DOWN, "down", 0x51)
        putKey(KEYCODE_DPAD_UP, "up", 0x52)

        // translate media keys
        putKey(KEYCODE_MEDIA_NEXT, "next", 0xb5.toByte())
        putKey(KEYCODE_MEDIA_PREVIOUS, "previous", 0xb6.toByte())
        putKey(KEYCODE_MEDIA_PLAY_PAUSE, "play-pause", 0xcd.toByte())
        putKey(KEYCODE_VOLUME_UP, "volume-up", 0xe9.toByte())
        putKey(KEYCODE_VOLUME_DOWN, "volume-down", 0xea.toByte())

        // translate modifier keys
        putModifierKey(0, "no-modifier", 0x0)
        putModifierKey(META_CTRL_LEFT_ON, "left-ctrl", 0x01)
        putModifierKey(META_SHIFT_LEFT_ON, "left-shift", LEFT_SHIFT_SCAN_CODE)
        putModifierKey(META_ALT_LEFT_ON, "left-alt", 0x04)
        putModifierKey(META_META_LEFT_ON, "left-meta", 0x08)
        putModifierKey(META_CTRL_RIGHT_ON, "right-ctrl", 0x10)
        putModifierKey(META_SHIFT_RIGHT_ON, "right-shift", 0x20)
        putModifierKey(META_ALT_RIGHT_ON, "right-alt", 0x40)
        putModifierKey(META_META_RIGHT_ON, "right-meta", 0x80.toByte())

        // Keys that are represented by another key + shift
        shiftChars["<"] = ","
        shiftChars[">"] = "."
        shiftChars["?"] = "/"
        shiftChars[":"] = ";"
        shiftChars["\""] = "'"
        shiftChars["{"] = "["
        shiftChars["}"] = "]"
        shiftChars["|"] = "\\"
        shiftChars["~"] = "`"
        shiftChars["!"] = "1"
        shiftChars["@"] = "2"
        shiftChars["#"] = "3"
        shiftChars["$"] = "4"
        shiftChars["%"] = "5"
        shiftChars["^"] = "6"
        shiftChars["&"] = "7"
        shiftChars["*"] = "8"
        shiftChars["("] = "9"
        shiftChars[")"] = "0"
        shiftChars["_"] = "-"
        shiftChars["+"] = "="
    }

    private fun putKey(keyCode: Int, key: String, scanCode: Byte) {
        keyEventKeys[keyCode] = key
        hidKeyCodes[key] = scanCode
    }

    private fun putModifierKey(keyCode: Int, key: String, scanCode: Byte) {
        keyEventModifierKeys[keyCode] = key
        hidModifierCodes[key] = scanCode
    }

    fun isMediaKey(keyCode: Int): Boolean {
        return keyCode == KEYCODE_VOLUME_UP ||
                keyCode == KEYCODE_VOLUME_DOWN ||
                keyCode == KEYCODE_MEDIA_NEXT ||
                keyCode == KEYCODE_MEDIA_PREVIOUS ||
                keyCode == KEYCODE_MEDIA_PLAY_PAUSE
    }
}
