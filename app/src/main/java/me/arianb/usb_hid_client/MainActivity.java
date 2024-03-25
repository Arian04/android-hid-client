package me.arianb.usb_hid_client;

import static me.arianb.usb_hid_client.hid_utils.CharacterDevice.KEYBOARD_DEVICE_PATH;
import static me.arianb.usb_hid_client.hid_utils.KeyCodeTranslation.convertKeyToScanCodes;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.google.android.material.snackbar.Snackbar;

import me.arianb.usb_hid_client.hid_utils.CharacterDevice;
import timber.log.Timber;

// TODO:
//  - properly handle the case of the app not being given root permissions. Currently it just fails a lot.
//  - add a switch in the app for disabling/enabling the character device so you can use USB debugging again
//  - improve UI a lot
//  - improve touchpad click handling
//      - I want a single tap to be sent immediately, I don't want to handle double taps or anything like that
//      - However, if multiple fingers tap at once, I do want to handle that in a special way (2 = right click, 3 = middle click)
//  - Convert my customized Views into fully custom View subclasses to move that code out of this cluttered MainActivity

// Notes on terminology:
// 		A key that has been pressed in conjunction with the shift key (ex: @ = 2 + shift, $ = 4 + shift, } = ] + shift, etc.)
// 		will be referred to as a "shifted" key. In the previous example, 2, 4, and ] would
// 		be considered the "unshifted" keys.
public class MainActivity extends AppCompatActivity {
    private View parentLayout;

    private SharedPreferences preferences;

    public static CharacterDevice characterDevice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        if (BuildConfig.DEBUG || preferences.getBoolean("debug_mode", false)) {
            Timber.plant(new Timber.DebugTree());
        }

        setContentView(R.layout.activity_main);

        parentLayout = findViewById(android.R.id.content);

        // If this is the first time the app has been opened, then show OnboardingActivity
        boolean onboardingDone = preferences.getBoolean("onboarding_done", false);
        if (!onboardingDone) {
            // Start OnboardingActivity
            startActivity(new Intent(this, OnboardingActivity.class));
        }

        characterDevice = new CharacterDevice(getApplicationContext());

        // Start threads to send key and mouse events
        final KeySender keySender = new KeySender(parentLayout);
        new Thread(keySender).start();
        final MouseSender mouseSender = new MouseSender(parentLayout);
        new Thread(mouseSender).start();

        // Set up input Views
        final Button btnSubmit = findViewById(R.id.btnKeyboard);
        final EditText etManualInput = findViewById(R.id.etManualInput);
        final DirectInputKeyboardView etDirectInput = findViewById(R.id.etDirectInput);
        final TouchpadView touchpad = findViewById(R.id.tvTouchpad);

        setupManualKeyboardInput(etManualInput, btnSubmit, keySender);
        setupDirectKeyboardInput(etDirectInput, keySender);
        touchpad.setTouchListeners(mouseSender);

        if (onboardingDone) {
            promptUserIfNonExistentCharacterDevice();
        }
    }

    private void setupManualKeyboardInput(EditText etManualInput, Button btnSubmit, KeySender keySender) {
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

                // Converts (String) key to (byte) key scan code and (byte) modifier scan code and add to queue
                byte[] tempScanCodes = convertKeyToScanCodes(key);
                if (tempScanCodes == null) {
                    String error = "key: '" + key + "' is not supported.";
                    Timber.e(error);
                    Snackbar.make(parentLayout, error, Snackbar.LENGTH_SHORT).show();
                    return;
                }

                byte modifierScanCode = tempScanCodes[0];
                byte keyScanCode = tempScanCodes[1];

                keySender.addKey(modifierScanCode, keyScanCode, KeySender.STANDARD_KEY);
            }
        });
    }

    private void setupDirectKeyboardInput(DirectInputKeyboardView etDirectInput, KeySender keySender) {
        etDirectInput.setKeyListeners(keySender);

        // TODO: move these two method calls below into DirectInputKeyboardView eventually
        // Show soft keyboard when Direct Input gets focus
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
    }

    // Inflate the options menu when the user opens it for the first time
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
        // Warns user if character device doesn't exist and shows a button to fix it
        if (CharacterDevice.characterDeviceMissing(KEYBOARD_DEVICE_PATH)) {
            if (default_prompt_action_pref.equals("Fix")) {
                if (!characterDevice.createCharacterDevice()) {
                    Snackbar.make(parentLayout, "ERROR: Failed to create character device.", Snackbar.LENGTH_SHORT).show();
                }
            } else { // If pref isn't "fix" then it's "ask every time", so ask
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
}