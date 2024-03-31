package me.arianb.usb_hid_client;

import static me.arianb.usb_hid_client.hid_utils.CharacterDevice.anyCharacterDeviceMissing;
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
import me.arianb.usb_hid_client.input_views.DirectInputKeyboardView;
import me.arianb.usb_hid_client.input_views.TouchpadView;
import me.arianb.usb_hid_client.report_senders.KeySender;
import me.arianb.usb_hid_client.report_senders.MouseSender;
import me.arianb.usb_hid_client.shell_utils.NoRootPermissionsException;
import timber.log.Timber;

// TODO: move all misc strings used in snackbars and alerts throughout the app into strings.xml for translation purposes.

// Notes on terminology:
// 		A key that has been pressed in conjunction with the shift key (ex: @ = 2 + shift, $ = 4 + shift, } = ] + shift)
// 		will be referred to as a "shifted" key. In the previous example, 2, 4, and ] would be considered
// 		the "un-shifted" keys.
public class MainActivity extends AppCompatActivity {
    private View parentLayout;
    private DirectInputKeyboardView etDirectInput;

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
        etDirectInput = findViewById(R.id.etDirectInput); // can't be a local var because menu item needs access to it
        final TouchpadView touchpad = findViewById(R.id.tvTouchpad);

        setupManualKeyboardInput(etManualInput, btnSubmit, keySender);
        setupDirectKeyboardInput(etDirectInput, keySender);
        touchpad.setTouchListeners(mouseSender);

        // TODO: if this fails here, I need to make it incredibly clear that the app will not work.
        //       right now, you can still try to use it and it'll fail. It should just "lock" the inputs
        //       if this fails I think.
        if (anyCharacterDeviceMissing()) {
            showCreateCharDevicesPrompt();
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
    }

    // Inflate the options menu when the user opens it for the first time
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    // Run code on menu item selected
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();

        if (itemId == R.id.menuDirectInput) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            etDirectInput.postDelayed(() -> {
                etDirectInput.requestFocus();
                imm.showSoftInput(etDirectInput, 0);
            }, 100);
        } else if (itemId == R.id.menuSettings) {
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

    private void showCreateCharDevicesPrompt() {
        String default_prompt_action_pref = preferences.getString("issue_prompt_action", "Ask Every Time");

        // Warns user if character device doesn't exist and shows a button to fix it
        if (default_prompt_action_pref.equals("Fix")) {
            try {
                if (!characterDevice.createCharacterDevice()) {
                    Snackbar.make(parentLayout, "ERROR: Failed to create character device.", Snackbar.LENGTH_INDEFINITE).show();
                }
            } catch (NoRootPermissionsException e) {
                Timber.e("Failed to create character device, missing root permissions");
                Snackbar.make(parentLayout, "ERROR: Missing root permissions.", Snackbar.LENGTH_INDEFINITE).show();
            }
        } else { // If pref isn't "fix" then it's "ask every time", so ask
            AlertDialog.Builder builder = getCreateCharDevicesAlert();
            builder.show();
        }
    }

    @NonNull
    private AlertDialog.Builder getCreateCharDevicesAlert() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setTitle("Character device(s) do not exist");
        builder.setMessage("Add HID functions to the default USB gadget? This must be re-done after every reboot.\n\n**The app will not work if you decline**");

        builder.setPositiveButton("YES", (dialog, which) -> {
            try {
                if (!characterDevice.createCharacterDevice()) {
                    Snackbar.make(parentLayout, "ERROR: Failed to create character device.", Snackbar.LENGTH_INDEFINITE).show();
                }
            } catch (NoRootPermissionsException e) {
                Timber.e("Failed to create character device, missing root permissions");
                Snackbar.make(parentLayout, "ERROR: Missing root permissions.", Snackbar.LENGTH_INDEFINITE).show();
            } finally {
                dialog.dismiss();
            }
        });
        builder.setNegativeButton("NO", (dialog, which) -> {
            this.finish(); // Exit app

        });

        // The response to this dialog is critical for the app to function, so don't let user skip it.
        builder.setCancelable(false);

        return builder;
    }
}