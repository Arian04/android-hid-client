package me.arianb.usb_hid_client.input_views;

import static me.arianb.usb_hid_client.hid_utils.KeyCodeTranslation.convertKeyToScanCodes;
import static me.arianb.usb_hid_client.hid_utils.KeyCodeTranslation.hidMediaKeyCodes;
import static me.arianb.usb_hid_client.hid_utils.KeyCodeTranslation.hidModifierCodes;
import static me.arianb.usb_hid_client.hid_utils.KeyCodeTranslation.keyEventKeys;
import static me.arianb.usb_hid_client.hid_utils.KeyCodeTranslation.keyEventModifierKeys;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.Editable;
import android.text.method.KeyListener;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.google.android.material.snackbar.Snackbar;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import me.arianb.usb_hid_client.report_senders.KeySender;
import timber.log.Timber;

public class DirectInputKeyboardView extends androidx.appcompat.widget.AppCompatEditText {
    private final View parentLayout = findViewById(android.R.id.content);
    private SharedPreferences preferences;

    // Contains modifiers of the current key as its being processed
    private final Set<Byte> modifiers = new HashSet<>();

    private KeySender keySender;

    public DirectInputKeyboardView(@NonNull Context context) {
        super(context);
    }

    public DirectInputKeyboardView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public DirectInputKeyboardView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setKeyListeners(@NonNull KeySender keySender) {
        this.keySender = keySender;
        preferences = PreferenceManager.getDefaultSharedPreferences(getContext());

        // Listens for keys pressed while the "Direct Input" EditText is focused and adds them to
        // the queue of keys
        this.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) {
                return false;
            }

            // keyCode 0 corresponds to KEYCODE_UNKNOWN which I obviously can't do anything with
            // since I can't send an unknown key, so just ignore it
            if (keyCode == 0) {
                return false;
            }

            Timber.d("onKey: %d", keyCode);

            if (KeyEvent.isModifierKey(keyCode)) { // Handle modifier keys
                Byte modifier = hidModifierCodes.get(keyEventModifierKeys.get(keyCode));
                if (modifier != null) {
                    toggleModifier(modifier);
                    Timber.d("modifier: %s", modifier);
                } else {
                    Timber.e("either keyEventModifierKeys map does not contain keyCode (%d) or hidModifierCodes doesn't contain the result", keyCode);
                    return false;
                }
            } else { // Handle non-modifier keys
                if (keyEventKeys.containsKey(keyCode)) {
                    // If key is volume key and user doesn't want us to pass it through, then just
                    // ignore it and let the system handle it normally
                    if ((keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
                            && !preferences.getBoolean("volume_button_passthrough", false)) {
                        return false;
                    }

                    convertKeyAndSendKey(keyCode);
                    Timber.d("key: %s", keyEventKeys.get(keyCode));
                } else {
                    Snackbar.make(parentLayout, "That key (keycode " + keyCode + ") is not supported yet, file a bug report", Snackbar.LENGTH_SHORT).show();
                    return false;
                }
            }
            return true;
        });

        // The onKeyListener doesn't work properly at all unless this is also set.
        // And if I try to use this instead of the onKeyListener, tab and enter don't work.
        this.setKeyListener(new KeyListener() {
            public int getInputType() {
                // Forces it to not consume the keys
                // Side effect: I have to manually manage showing the soft keyboard on interactions
                return 0;
            }

            @Override
            public boolean onKeyDown(View view, Editable editable, int i, KeyEvent keyEvent) {
                return false;
            }

            @Override
            public boolean onKeyUp(View view, Editable editable, int i, KeyEvent keyEvent) {
                return false;
            }

            @Override
            public boolean onKeyOther(View view, Editable editable, KeyEvent keyEvent) {
                return false;
            }

            @Override
            public void clearMetaKeyState(View view, Editable editable, int i) {
            }
        });
    }

    // Converts (int) KeyEvent code to (byte) key scan code and (byte) modifier scan code and add to queue
    private void convertKeyAndSendKey(int keyCode) {
        // If key is volume (up or down) key
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            Timber.d("volume key: %s", keyCode);

            Byte volumeScanCode = hidMediaKeyCodes.get(keyEventKeys.get(keyCode));
            if (volumeScanCode != null) {
                keySender.addMediaKey(volumeScanCode);
                return;
            } else {
                Timber.e("keycode (%s) not found in hidMediaKeyCodes map", keyCode);
            }
        }

        byte[] tempScanCodeBytes = convertKeyToScanCodes(keyEventKeys.get(keyCode));

        // If convertKeyToScanCodes returns null then an error has occurred in translation
        if (tempScanCodeBytes == null) {
            String error = "key: '" + keyEventKeys.get(keyCode) + "' is not supported.";
            Timber.e(error);
            Snackbar.make(parentLayout, error, Snackbar.LENGTH_SHORT).show();
            return;
        }
        byte keyScanCode = tempScanCodeBytes[1];

        // Add modifier to set (bypass toggle method because I don't want this to toggle it
        modifiers.add(tempScanCodeBytes[0]);

        // Sum all modifiers in modifiers Set
        Iterator<Byte> modifiersIterator = modifiers.iterator();
        byte modifiersSum = 0;
        while (modifiersIterator.hasNext()) {
            modifiersSum += modifiersIterator.next();
        }

        byte modifierScanCode = modifiersSum;
        keySender.addStandardKey(modifierScanCode, keyScanCode);
        modifiers.clear();
    }

    // Toggles the presence of a modifier in the Set of modifiers
    // In other words, if the modifier is in the set, remove it, if the modifier isn't, add it.
    // The purpose of this is so that when modifiers are pressed a second time, they get unselected
    // ex: User is using Hacker's keyboard and clicks ctrl key, then clicks it again to toggle it off
    private void toggleModifier(byte modifier) {
        if (modifiers.contains(modifier)) {
            modifiers.remove(modifier);
        } else {
            modifiers.add(modifier);
        }
    }
}
