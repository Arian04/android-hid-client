package me.arianb.usb_hid_client;

import static me.arianb.usb_hid_client.ProcessStreamHelper.getProcessStdError;
import static me.arianb.usb_hid_client.ProcessStreamHelper.getProcessStdOutput;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import org.apache.commons.codec.binary.Hex;

import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
	public static final String TAG = "hid-client";

	private EditText etInput;
	private Button btnSubmit;
	private TextView tvOutput;
	private EditText etManual;

	private Map<Integer, String> modifierKeys;
	private Map<Integer, String> keyEventCodes;
	private Map<String, String> shiftChars;
	private Map<String, Byte> hidKeyCodes;
	private Map<String, Byte> hidModifierCodes;

	private boolean nextKeyModified = false;
	private String modifier;

	public Logger logger;

	private SharedPreferences preferences;

	private DataOutputStream rootShell;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		// Initialize maps to translate keycodes into hid codes
		KeyCodeTranslation code = new KeyCodeTranslation();
		modifierKeys = code.modifierKeys;
		keyEventCodes = code.keyEventCodes;
		shiftChars = code.shiftChars;
		hidKeyCodes = code.hidKeyCodes;
		hidModifierCodes = code.hidModifierCodes;

		// Initialize UI elements
		etInput = findViewById(R.id.etKeyboardInput);
		btnSubmit = findViewById(R.id.btnKeyboard);
		tvOutput = findViewById(R.id.tvOutput);
		etManual = findViewById(R.id.etManual);

		preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		SettingsActivity.SettingsFragment.setContext(this);

		// Logging
		logger = new Logger(this, tvOutput);
		logger.watchForPreferenceChanges(preferences);

		tvOutput.setMovementMethod(new ScrollingMovementMethod());

		// Get root shell
		try {
			Process p = Runtime.getRuntime().exec("su");
			rootShell = new DataOutputStream(p.getOutputStream());
		} catch (IOException e) {
			Log.e(TAG, Log.getStackTraceString(e));
		}

		btnSubmit.setOnClickListener(v -> {
			// Save text to send
			String sendStr = etManual.getText().toString();

			// Clear EditText if the user's preference is to clear it
			if (preferences.getBoolean("clear_manual_input", false)) {
				runOnUiThread(() -> etManual.setText(""));
			}

			// Splits string into array with 1 character per element
			String[] sendStrArr = sendStr.split("");

			// Sends all keys
			new Thread(() -> {
				for (String key : sendStrArr) {
					sendKey(key);
				}
			}).start();
		});

		// Listens for changes to the edittext
		// Detects printable characters (a-z, 0-9, etc.)
		etInput.addTextChangedListener(new TextWatcher() {
			// Methods not used
			public void afterTextChanged(Editable s) {
			}

			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				Log.d(TAG, "Diff: " + s);
				Log.d(TAG, start + " " + before + " " + count);
				if (s.length() >= 1) {
					// This should typically only contain a single character at a time, but I'm
					// handling it as an array of strings since if the app lags badly, it can
					// sometimes take a bit before it registers and it sends as several characters.
					String[] allKeys = s.toString().split("");
					for (String key : allKeys) {
						sendKey(key);
						Log.d(TAG, "textChanged key: " + key);

						// Hacky workaround that clears the edittext after every key press to
						// make arrow keys get registered by onKeyDown (because it only triggers
						// when the key doesn't touch the EditText)
						//etInput.getText().clear(); // Mitigates some of InputConnection warnings
						etInput.setText("");
					}
				}
			}
		});
	}

	// Listens for KeyEvents
	// Detects non-printing characters (tab, backspace, function keys, etc.) that aren't consumed
	// by the EditText
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (!etInput.hasFocus()) {
			Log.d(TAG, "Ignoring KeyEvent because the direct input edittext was not focused");
			return false;
		}
		if (KeyEvent.isModifierKey(keyCode)) {
			modifier = modifierKeys.get(event.getKeyCode());
			nextKeyModified = true;
			Log.d(TAG, "modifier: " + modifier);
		}

		String key = null;
		if ((key = keyEventCodes.get(event.getKeyCode())) != null) {
			sendKey(key);
			Log.d(TAG, "onKeyDown key: " + key);
		}
		Log.d(TAG, "keycode: " + event.getKeyCode());
		return true;
	}

	// TODO: un-multithread all calls to sendKey method OR figure out how to guarantee the order.

	// TODO: add support for sending multiple modifier keys at once
	//		 - i think you add up modifier scan codes to send multiple? so find method to add hex
	// 		 - use an arraylist to hold mod keys and add to them instead of overwriting?
	private void sendKey(String key) {
		if (key == null) {
			Log.e(TAG, "sendKey received null key value");
			return;
		}

		String sendModifier = "no-modifier";
		String adjustedKey = key;
		if (nextKeyModified) {
			sendModifier = modifier;
			nextKeyModified = false;
		}
		String str = null;
		if ((str = shiftChars.get(key)) != null) {
			adjustedKey = str;
			sendModifier = "left-shift";
			Log.d(TAG, "adding shift option to make: " + adjustedKey + " -> " + key);
		}

		// If character is uppercase, send the lowercase char + shift key
		if (key.length() == 1 && Character.isUpperCase(key.charAt(0))) {
			sendModifier = "left-shift";
			adjustedKey = key.toLowerCase();
		}

		// Convert key to HID code
		byte keyScanCode = 0;
		try {
			keyScanCode = hidKeyCodes.get(adjustedKey);
		} catch (NullPointerException e) {
			Log.e(TAG, "key: '" + key + "' could not be converted to an HID code (it wasn't found in the map).");
		}

		// Convert modifier to HID code
		byte modifierScanCode = 0;
		try {
			modifierScanCode = hidModifierCodes.get(sendModifier);
		} catch (NullPointerException e) {
			Log.e(TAG, "mod: '" + modifier + "' could not be converted to an HID code (it wasn't found in the map).");
		}

		try {
			Log.i(TAG, "raw key: " + key + " | sending key: " + adjustedKey + " | modifier: " + sendModifier);

			// TODO: give app user permissions to write to /dev/hidg0 because privilege escalation
			//  	 causes a very significant performance hit
			// 		 - once i complete this, remove performance mode, because it'll be fast by default
			Process checkSELinuxModeProcess = Runtime.getRuntime().exec("cat /sys/fs/selinux/enforce");
			int SELinuxMode;
			// If this throws an error, SELinux is enabled, otherwise it'll set selinuxMode to 1.
			try {
				SELinuxMode = Integer.parseInt(getProcessStdOutput(checkSELinuxModeProcess).trim());
			} catch (NumberFormatException e) {
				SELinuxMode = 1;
			}

			if (SELinuxMode == 0) { // Only works if selinux is disabled
				Log.i(TAG, "SELinux is disabled. Directly writing to device.");
				byte[] send = new byte[]{modifierScanCode, (byte) 0, keyScanCode, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0};
				Log.d(TAG, "send str: " + Arrays.toString(send));
				writeByteArrayToFile("/dev/hidg0", send);

				byte[] release = new byte[]{(byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0};
				Log.d(TAG, "release bytes: " + Arrays.toString(release));
				writeByteArrayToFile("/dev/hidg0", release);
			} else if (preferences.getBoolean("performance_mode", false)) {
				Log.i(TAG, "\"Performance mode\" code active.");

				// Convert byte to hexadecimal String and prepend x (Ex: (byte) 0x15 -> (String) x15)
				sendModifier = "x" + Hex.encodeHexString(new byte[]{modifierScanCode});
				adjustedKey = "x" + Hex.encodeHexString(new byte[]{keyScanCode});

				// echo -en "\modifier\0\key\0\0\0\0\0" > /dev/hidg0 (as root) (presses key)
				String sendKeyCmd = "echo -en \"\\" + sendModifier + "\\0\\" + adjustedKey + "\\0\\0\\0\\0\\0\" > /dev/hidg0";
				// echo -en "\0\0\0\0\0\0\0\0" > /dev/hidg0 (as root) (releases key)
				String releaseKeyCmd = "echo -en \"\\0\\0\\0\\0\\0\\0\\0\\0\" > /dev/hidg0";
				// Send key
				rootShell.writeBytes(sendKeyCmd + "\n");
				rootShell.flush();
				// Release key
				rootShell.writeBytes(releaseKeyCmd + "\n");
				rootShell.flush();
			} else {
				Log.i(TAG, "\"Performance mode\" DISABLED");

				// Convert byte to hexadecimal String and prepend x (Ex: (byte) 0x15 -> (String) x15)
				sendModifier = "x" + Hex.encodeHexString(new byte[]{modifierScanCode});
				adjustedKey = "x" + Hex.encodeHexString(new byte[]{keyScanCode});

				// echo -en "\modifier\0\key\0\0\0\0\0" > /dev/hidg0 (as root) (presses key)
				String[] sendKeyCmd = {"su", "-c", "echo", "-en", "\"\\" + sendModifier + "\\0\\" + adjustedKey + "\\0\\0\\0\\0\\0\" > /dev/hidg0"};
				// echo -en "\0\0\0\0\0\0\0\0" > /dev/hidg0 (as root) (releases key)
				String[] releaseKeyCmd = {"su", "-c", "echo", "-en", "\"\\0\\0\\0\\0\\0\\0\\0\\0\" > /dev/hidg0"};

				// Send key
				Process sendProcess = Runtime.getRuntime().exec(sendKeyCmd);
				// Kill process if it doesn't complete within 1 seconds
				if (!sendProcess.waitFor(1, TimeUnit.SECONDS)) {
					Log.e(TAG, "Timed out while sending key. Make sure a computer is connected.");
					sendProcess.destroy();
					return;
				}
				// Release key
				Process releaseProcess = Runtime.getRuntime().exec(releaseKeyCmd);

				// Log errors if the processes returned any
				String sendErrors = getProcessStdError(sendProcess);
				String releaseErrors = getProcessStdError(releaseProcess);
				if (!sendErrors.isEmpty()) {
					Log.e(TAG, sendErrors);
				}
				if (!releaseErrors.isEmpty()) {
					Log.e(TAG, releaseErrors);
				}
				String sendOut = getProcessStdOutput(sendProcess);
				String releaseOut = getProcessStdOutput(releaseProcess);
				if (!sendOut.isEmpty()) {
					Log.e(TAG, sendOut); // TODO Logging level
				}
				if (!releaseOut.isEmpty()) {
					Log.e(TAG, releaseOut);
				}
			}
		} catch (IOException | InterruptedException e) {
			Log.e(TAG, Log.getStackTraceString(e));
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
			Toast.makeText(this, "help Clicked", Toast.LENGTH_SHORT).show(); // DEBUG
		} else if (itemId == R.id.menuInfo) {
			Toast.makeText(this, "info Clicked", Toast.LENGTH_SHORT).show(); // DEBUG
		} else if (itemId == R.id.menuDebug) {
			// Menu option that just runs whatever code I want to test in the app
			//SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
			Log.d(TAG, "PREF: " + preferences.getString("logging_level", "error"));
			try {
				Process proc = Runtime.getRuntime().exec("ls -l /data/data/me.arianb.usb_hid_client/hidg0");
				Log.d(TAG, "OUT: " + getProcessStdOutput(proc));
				Log.d(TAG, "ERR: " + getProcessStdError(proc));
			} catch (IOException e) {
				Log.e(TAG, Log.getStackTraceString(e));
			}
		}
		return super.onOptionsItemSelected(item);
	}

	private void writeByteArrayToFile(String filename, byte[] byteArray) {
		try (FileOutputStream outputStream = new FileOutputStream(filename)) {
			outputStream.write(byteArray);
		} catch (IOException e) {
			Log.e(TAG, Log.getStackTraceString(e));
		}
	}
}