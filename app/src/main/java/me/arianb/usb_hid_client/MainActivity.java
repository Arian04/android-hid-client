package me.arianb.usb_hid_client;

import static me.arianb.usb_hid_client.KeyCodeTranslation.convertKeyToScanCodes;
import static me.arianb.usb_hid_client.KeyCodeTranslation.hidModifierCodes;
import static me.arianb.usb_hid_client.KeyCodeTranslation.keyEventKeys;
import static me.arianb.usb_hid_client.KeyCodeTranslation.keyEventModifierKeys;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.method.KeyListener;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import timber.log.Timber;

// TODO: 	- Check if everything is properly explained in comments
//       	- Custom view that can let the onKeyDown listener process all key events
// 			- add general media key handling (play/pause, previous, next, etc.)

// Notes on terminology:
// 		A key that has been pressed in conjunction with the shift key (ex: @ = 2 + shift, $ = 4 + shift, } = ] + shift, etc.)
// 		will be referred to as a "shifted" key. In the previous example, 2, 4, and ] would
// 		be considered the "unshifted" keys.
public class MainActivity extends AppCompatActivity {
	private EditText etDirectInput;
	private Button btnSubmit;
	private EditText etManualInput;
	private AlertDialog connectionWarningDialog;
	private static View parentLayout;

	private Set<Byte> modifiers;

	private KeySender keySender;

	private SharedPreferences preferences;

	private AudioManager audioManager;

	private static CharacterDevice characterDevice;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (BuildConfig.DEBUG) {
			Timber.plant(new Timber.DebugTree());
		}

		setContentView(R.layout.activity_main);

		// Initialize UI elements
		etDirectInput = findViewById(R.id.etDirectInput);
		btnSubmit = findViewById(R.id.btnKeyboard);
		etManualInput = findViewById(R.id.etManualInput);
		parentLayout = findViewById(android.R.id.content);

		modifiers = new HashSet<>();

		preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

		audioManager = (AudioManager) getApplicationContext().getSystemService(Context.AUDIO_SERVICE);

		characterDevice = new CharacterDevice(getApplicationContext());

		// Start thread to send keys
		keySender = new KeySender(this);
		new Thread(keySender).start();

		// Button sends text in manualInput TextView
		btnSubmit.setOnClickListener(v -> {
			// Save text to send
			String sendStr = etManualInput.getText().toString();

			// If empty, don't do anything
			if (sendStr.equals("")) {
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
		etDirectInput.setKeyListener(new KeyListener() {
			@Override
			public boolean onKeyDown(View view, Editable editable, int i, KeyEvent keyEvent) {
				Timber.d("onKeyDown DEBUG KEY: %s", keyEvent.getKeyCode());
				int keyCode = keyEvent.getKeyCode();

				if (KeyEvent.isModifierKey(keyCode)) { // Handle modifier keys
					byte modifier = hidModifierCodes.get(keyEventModifierKeys.get(keyCode));
					addModifier(modifier);
					Timber.d("modifier: %s", modifier);
				} else { // Handle non-modifier keys
					if (keyEventKeys.containsKey(keyCode)) {
						convertKeyEventAndSendKey(keyCode);
						Timber.d("key: %s", keyEventKeys.get(keyCode));
					} else {
						Snackbar.make(parentLayout, "That key is not supported yet, file a bug report", Snackbar.LENGTH_SHORT).show();
					}
				}
				return true;
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
			public int getInputType() {
				return 0;
			}
			@Override
			public void clearMetaKeyState(View view, Editable editable, int i) {}
		});

		// Detect when Direct Input gets focus, since for some reason, the keyboard doesn't open
		// when the EditText is focused after I made it use a KeyListener
		etDirectInput.setOnFocusChangeListener(new View.OnFocusChangeListener() {
			@Override
			public void onFocusChange(View v, boolean hasFocus) {
				if(hasFocus){
					InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
					etDirectInput.postDelayed(new Runnable() {
						@Override
						public void run() {
							etDirectInput.requestFocus();
							imm.showSoftInput(etDirectInput, 0);
						}
					}, 100);
				}
			}
		});

		// Warns user if character device doesn't exist and shows a button to fix it
		if (!new File("/dev/hidg0").exists()) { // If it doesn't exist
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle("Error: Nonexistent character device");
			builder.setMessage("/dev/hidg0 does not exist, would you like for it to be created for you?\n\n" +
					"Don't decline unless you would rather create it yourself and know how to do that.");
			builder.setPositiveButton("YES", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					if (!characterDevice.createCharacterDevice()) {
						Snackbar.make(parentLayout, "ERROR: Failed to create character device.", Snackbar.LENGTH_SHORT).show();
					} else if (!characterDevice.fixCharacterDevicePermissions("/dev/hidg0")) {
						Snackbar.make(parentLayout, "ERROR: Failed to fix character device permissions.", Snackbar.LENGTH_SHORT).show();
					}
					dialog.dismiss();
				}
			});
			builder.setNegativeButton("NO", null);
			AlertDialog alert = builder.create();
			alert.show();
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
			Toast.makeText(this, "help clicked", Toast.LENGTH_SHORT).show(); // TODO: help page
		} else if (itemId == R.id.menuInfo) {
			Toast.makeText(this, "info clicked", Toast.LENGTH_SHORT).show(); // TODO: info page
		}
		return super.onOptionsItemSelected(item);
	}

	// Converts (int) KeyEvent code to (byte) key scan code and (byte) modifier scan code and add to queue
	private void convertKeyEventAndSendKey(int keyCode) {
		// If key is volume (up or down) key and volume key passthrough is not enabled
		// then increase phone volume like normal (must be done manually since KeyListener consumes it)
		if( (keyCode == 24 || keyCode == 25) && !preferences.getBoolean("volume_button_passthrough", false) ) {
			Timber.d("volume key: %s", keyCode);
			switch (keyCode) {
				case 24: // Volume up
					audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
					break;
				case 25: // Volume down
					audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
					break;
			}
			return;
		}

		byte[] tempHIDCodes = convertKeyToScanCodes(keyEventKeys.get(keyCode));
		byte keyHIDCode = tempHIDCodes[1];

		// Sum all modifiers in modifiers Set
		Iterator<Byte> modifiersIterator = modifiers.iterator();
		byte modifiersSum = 0;
		while(modifiersIterator.hasNext()) {
			modifiersSum += modifiersIterator.next();
		}

		byte modifierHIDCode = (byte)(tempHIDCodes[0] + modifiersSum);
		keySender.addKey(modifierHIDCode, keyHIDCode);
		modifiers.clear();
	}

	// Converts (String) key to (byte) key scan code and (byte) modifier scan code and add to queue
	private void convertKeyAndSendKey(String key) {
		byte[] tempHIDCodes = convertKeyToScanCodes(key);
		byte keyHIDCode = tempHIDCodes[1];

		// Sum all modifiers in modifiers Set
		Iterator<Byte> modifiersIterator = modifiers.iterator();
		byte modifiersSum = 0;
		while(modifiersIterator.hasNext()) {
			modifiersSum += modifiersIterator.next();
		}

		byte modifierHIDCode = (byte)(tempHIDCodes[0] + modifiersSum);
		keySender.addKey(modifierHIDCode, keyHIDCode);
		modifiers.clear();
	}

	// Toggles the presence of a modifier in the Set of modifiers
	// In other words, if the modifier is in the set, remove it, if the modifier isn't, add it.
	// The purpose of this is so that when modifiers are pressed a second time, they get unselected
	// ex: User is using Hacker's keyboard and clicks ctrl key, then clicks it again to toggle it off
	private void addModifier(byte modifier) {
		if (modifiers.contains(modifier)) {
			modifiers.remove(modifier);
		} else {
			modifiers.add(modifier);
		}
	}

	// These are messy and I don't like it but I can't figure out another way to handle showing
	// snackbars for errors within KeySender, if anyone is reading this and knows how, tell me please :)
	public static void makeSnackbar(String message, int length) {
		Snackbar.make(parentLayout, message, length).show();
	}

	public static void makeFixPermissionsSnackbar() {
		Snackbar snackbar = Snackbar.make(parentLayout, "ERROR: Character device permissions seem incorrect.", Snackbar.LENGTH_INDEFINITE);
		snackbar.setAction("FIX", v -> characterDevice.fixCharacterDevicePermissions("/dev/hidg0"));
		snackbar.show();
	}

}