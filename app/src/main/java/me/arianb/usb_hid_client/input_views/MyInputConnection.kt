package me.arianb.usb_hid_client.input_views

import android.os.Build
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.TextAttribute
import me.arianb.usb_hid_client.hid_utils.KeyCodeTranslation
import me.arianb.usb_hid_client.report_senders.KeySender
import timber.log.Timber

class MyInputConnection(
    private val keySender: KeySender,
    targetView: View,
    fullEditor: Boolean
) : BaseInputConnection(targetView, fullEditor) {
    override fun sendKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) {
            Timber.w("input connection received null KeyEvent")
            return false
        }
        Timber.v("input connection received KeyEvent: %s", event.toString())

        val keyCode = event.keyCode
        val action = event.action

        if (action != KeyEvent.ACTION_DOWN) {
            return false
        }
        if (keyCode == KeyEvent.KEYCODE_UNKNOWN) {
            return false
        }

        // If a lone modifier key was sent, consume it (return true), but don't do anything with it
        if (KeyEvent.isModifierKey(keyCode)) {
            return true
        }

        Timber.v("keyCode constant: %s", KeyEvent.keyCodeToString(keyCode))

        // If this is one of the problematic keys, handle it early in a special way
        val problematicKeyScanCodePair = KeyCodeTranslation.problematicKeyEventKeys[keyCode]
        if (problematicKeyScanCodePair != null) {
            keySender.addStandardKey(problematicKeyScanCodePair.first, problematicKeyScanCodePair.second)
            return true
        }

        val attemptRawKeyCodeTranslation = true

        // Handle the key itself
        val keyScanCode: Byte? = if (attemptRawKeyCodeTranslation) {
            val rawKeyCode = getRawKeyCode(event, keyCode)
            if (rawKeyCode != null) {
                KeyCodeTranslation.keyCodeToScanCode(rawKeyCode)
            } else {
                null
            }
        } else {
            null
        } ?: KeyCodeTranslation.keyCodeToScanCode(keyCode)

        if (keyScanCode == null) {
            Timber.w("Unsupported keycode '${KeyEvent.keyCodeToString(keyCode)}' ($keyCode). This is probably a bug.")
            return false
        }

        if (KeyCodeTranslation.isMediaKey(keyCode)) {
            keySender.addMediaKey(keyScanCode)
        } else {
            // Extract modifier from KeyEvent
            val modifiers = KeyCodeTranslation.getModifiersScanCode(event)

            keySender.addStandardKey(modifiers, keyScanCode)
        }

        return true
    }

    private fun getRawKeyCode(event: KeyEvent, keyCode: Int): Int? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val device = event.device
            if (device == null) {
                Timber.w("keyCode for location (Android Key Code): %s", "device is null")
            } else {
                val keyCodeForLocation = device.getKeyCodeForKeyLocation(keyCode)
                Timber.v("keyCode for location (Android Key Code): %s", keyCodeForLocation)
                return keyCodeForLocation
            }
        }

        return null
    }

    override fun commitText(text: CharSequence, newCursorPosition: Int, textAttribute: TextAttribute?): Boolean {
        Timber.w("input connection sending CharSequence: %s", text)
        return super.commitText(text, newCursorPosition, textAttribute)
    }
}
