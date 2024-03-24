package me.arianb.usb_hid_client;

import static me.arianb.usb_hid_client.hid_utils.CharacterDevice.KEYBOARD_DEVICE_PATH;
import static me.arianb.usb_hid_client.hid_utils.KeyCodeTranslation.convertKeyToScanCodes;
import static me.arianb.usb_hid_client.hid_utils.KeyCodeTranslation.hidMediaKeyCodes;
import static me.arianb.usb_hid_client.hid_utils.KeyCodeTranslation.hidModifierCodes;
import static me.arianb.usb_hid_client.hid_utils.KeyCodeTranslation.keyEventKeys;
import static me.arianb.usb_hid_client.hid_utils.KeyCodeTranslation.keyEventModifierKeys;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.method.KeyListener;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GestureDetectorCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.snackbar.Snackbar;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import me.arianb.usb_hid_client.hid_utils.CharacterDevice;
import timber.log.Timber;

// Notes on terminology:
// 		A key that has been pressed in conjunction with the shift key (ex: @ = 2 + shift, $ = 4 + shift, } = ] + shift, etc.)
// 		will be referred to as a "shifted" key. In the previous example, 2, 4, and ] would
// 		be considered the "unshifted" keys.
public class MainActivity extends AppCompatActivity {
    private EditText etDirectInput;
    @SuppressWarnings("FieldCanBeLocal") // Leaving it as is for organization purposes
    private Button btnSubmit;
    private EditText etManualInput;
    private View parentLayout;

    private SharedPreferences preferences;

    // Contains modifiers of the current key as its being processed
    private Set<Byte> modifiers;

    private KeySender keySender;

    public static CharacterDevice characterDevice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        if (BuildConfig.DEBUG || preferences.getBoolean("debug_mode", false)) {
            Timber.plant(new Timber.DebugTree());
        }

        setContentView(R.layout.activity_main);

        // If this is the first time the app has been opened, then show OnboardingActivity
        boolean onboardingDone = preferences.getBoolean("onboarding_done", false);
        if (!onboardingDone) {
            // Start OnboardingActivity
            Intent intent = new Intent(this, OnboardingActivity.class);
            startActivity(intent);
        }

        modifiers = new HashSet<>();

        characterDevice = new CharacterDevice(getApplicationContext());

        // Initialize UI elements
        etDirectInput = findViewById(R.id.etDirectInput);
        btnSubmit = findViewById(R.id.btnKeyboard);
        etManualInput = findViewById(R.id.etManualInput);
        parentLayout = findViewById(android.R.id.content);

        // Start thread to send keys
        keySender = new KeySender(parentLayout);
        new Thread(keySender).start();

        // Button sends text in manualInput TextView
        btnSubmit.setOnClickListener(v -> {
            // Save text to send
            String sendStr = etManualInput.getText().toString();

            // If empty, don't do anything
            if (sendStr.isEmpty()) {
                return;
            }

            // Clear EditText if the user's preference is to clear it
            if (preferences.getBoolean("clear_manual_input", false)) {
                runOnUiThread(() -> etManualInput.setText(""));
            }

            // Sends all keys
            for (int i = 0; i < sendStr.length(); i++) {
                String key = sendStr.substring(i, i + 1);
                convertKeyAndSendKey(key);
            }
        });

        // Listens for keys pressed while the "Direct Input" EditText is focused and adds them to
        // the queue of keys
        etDirectInput.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) {
                return false;
            }

            // keyCode 0 corresponds to KEYCODE_UNKNOWN which I obviously can't do anything with, so
            // just ignore it
            if (keyCode == 0) {
                return false;
            }

            Timber.d("onKey: %d", keyCode);

            if (KeyEvent.isModifierKey(keyCode)) { // Handle modifier keys
                Byte temp = hidModifierCodes.get(keyEventModifierKeys.get(keyCode));
                if (temp != null) {
                    byte modifier = temp;
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

        // For some reason, the onKeyListener doesn't work properly at all unless this is also set
        // And if I try to use this instead of the onKeyListener, tab and enter don't work
        etDirectInput.setKeyListener(new KeyListener() {
            public int getInputType() {
                // Forces it to not consume the keys
                // Side effect: I have to manually manage showing the soft keyboard on interactions
                return 0;
            }

            @Override
            public boolean onKeyDown(View view, Editable editable, int i, KeyEvent keyEvent) { return false;}

            @Override
            public boolean onKeyUp(View view, Editable editable, int i, KeyEvent keyEvent) { return false;}

            @Override
            public boolean onKeyOther(View view, Editable editable, KeyEvent keyEvent) { return false;}

            @Override
            public void clearMetaKeyState(View view, Editable editable, int i) {}
        });

        // Detect when Direct Input gets focus, since for some reason, the keyboard doesn't open
        // when the EditText is focused after I made it use a KeyListener
        etDirectInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                etDirectInput.postDelayed(() -> {
                    etDirectInput.requestFocus();
                    imm.showSoftInput(etDirectInput, 0);
                }, 100);
            }
        });

        // Sometimes the keyboard closes while focus is maintained, in which case, the above code
        // won't work, so this works in that case
        etDirectInput.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            etDirectInput.postDelayed(() -> {
                etDirectInput.requestFocus();
                imm.showSoftInput(etDirectInput, 0);
            }, 100);
        });

        if (onboardingDone) {
            promptUserIfNonExistentCharacterDevice();
        }
    }

    // method to inflate the options menu when
    // the user opens the menu for the first time
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    // Run code on menu item selected
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();

        if (itemId == R.id.menuSettings) {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        } else if (itemId == R.id.menuHelp) {
            Intent intent = new Intent(this, HelpActivity.class);
            startActivity(intent);
        } else if (itemId == R.id.menuInfo) {
            Intent intent = new Intent(this, InfoActivity.class);
            startActivity(intent);
        }
        return super.onOptionsItemSelected(item);
    }

    private void promptUserIfNonExistentCharacterDevice() {
        String default_prompt_action_pref = preferences.getString("issue_prompt_action", "Ask Every Time");
        if (default_prompt_action_pref.equals("Ignore")) {
            return;
        }
        // Warns user if character device doesn't exist and shows a button to fix it
        if (CharacterDevice.characterDeviceMissing(KEYBOARD_DEVICE_PATH)) { // If it doesn't exist
            if (default_prompt_action_pref.equals("Fix")) {
                if (!characterDevice.createCharacterDevice()) {
                    Snackbar.make(parentLayout, "ERROR: Failed to create character device.", Snackbar.LENGTH_SHORT).show();
                }
            } else { // If pref isn't "ignore" or "fix" then it's "ask every time", so ask
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Error: Nonexistent character device");
                builder.setMessage(String.format("%s does not exist, would you like for it to be created for you?\n\n" +
                        "Don't decline unless you would rather create it yourself and know how to do that.", KEYBOARD_DEVICE_PATH));
                builder.setPositiveButton("YES", (dialog, which) -> {
                    if (!characterDevice.createCharacterDevice()) {
                        Snackbar.make(parentLayout, "ERROR: Failed to create character device.", Snackbar.LENGTH_SHORT).show();
                    }
                    dialog.dismiss();
                });
                builder.setNegativeButton("NO", null);
                AlertDialog alert = builder.create();
                alert.show();
            }
        }
    }

    // Converts (int) KeyEvent code to (byte) key scan code and (byte) modifier scan code and add to queue
    private void convertKeyAndSendKey(int keyCode) {
        // If key is volume (up or down) key
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)) {
            Timber.d("volume key: %s", keyCode);

            Byte volumeScanCode = hidMediaKeyCodes.get(keyEventKeys.get(keyCode));
            if (volumeScanCode != null) {
                keySender.addKey((byte) 0, volumeScanCode, KeySender.MEDIA_KEY);
                return;
            } else {
                Timber.e("keycode (%s) not found in hidMediaKeyCodes map", keyCode);
            }
        }

        byte[] tempHIDCodes = convertKeyToScanCodes(keyEventKeys.get(keyCode));

        // If convertKeyToScanCodes returns null then an error has occurred in translation
        if (tempHIDCodes == null) {
            String error = "key: '" + keyEventKeys.get(keyCode) + "' is not supported.";
            Timber.e(error);
            Snackbar.make(parentLayout, error, Snackbar.LENGTH_SHORT).show();
            return;
        }
        byte keyHIDCode = tempHIDCodes[1];

        // Add modifier to set (bypass toggle method because I don't want this to toggle it
        modifiers.add(tempHIDCodes[0]);

        // Sum all modifiers in modifiers Set
        Iterator<Byte> modifiersIterator = modifiers.iterator();
        byte modifiersSum = 0;
        while (modifiersIterator.hasNext()) {
            modifiersSum += modifiersIterator.next();
        }

        byte modifierHIDCode = modifiersSum;
        keySender.addKey(modifierHIDCode, keyHIDCode, KeySender.STANDARD_KEY);
        modifiers.clear();
    }

    // Converts (String) key to (byte) key scan code and (byte) modifier scan code and add to queue
    private void convertKeyAndSendKey(String key) {
        if (key.length() != 1) {
            Timber.e("convertKeyAndSendKey: key has incorrect length");
            return;
        }
        byte[] tempHIDCodes = convertKeyToScanCodes(key);
        if (tempHIDCodes == null) {
            String error = "key: '" + key + "' is not supported.";
            Timber.e(error);
            Snackbar.make(parentLayout, error, Snackbar.LENGTH_SHORT).show();
            return;
        }
        byte keyHIDCode = tempHIDCodes[1];

        // Sum all modifiers in modifiers Set
        Iterator<Byte> modifiersIterator = modifiers.iterator();
        byte modifiersSum = 0;
        while (modifiersIterator.hasNext()) {
            modifiersSum += modifiersIterator.next();
        }

        byte modifierHIDCode = (byte) (tempHIDCodes[0] + modifiersSum);
        keySender.addKey(modifierHIDCode, keyHIDCode, KeySender.STANDARD_KEY);
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