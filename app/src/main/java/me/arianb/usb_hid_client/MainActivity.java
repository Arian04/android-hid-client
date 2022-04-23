package me.arianb.usb_hid_client;

import static me.arianb.usb_hid_client.ProcessStreamHelper.getProcessStdError;
import static me.arianb.usb_hid_client.ProcessStreamHelper.getProcessStdOutput;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import timber.log.Timber;

// TODO: - Check if everything is properly explained in comments
//		 - Mod keys are broken
//       - Custom view that can let the onKeyDown listener process all key events
// 	     - settle on a way to refer to certain situations (such as referring to a key that has been
// 	       initially pressed with shift, like !@#$%^&*(), and/or referring to the original key if the
// 	       user had not pressed shift, in the example given above, 1234567890) and explain them here
// 		   rather than explaining them over and over in each comment.
public class MainActivity extends AppCompatActivity {
	private EditText etInput;
	private Button btnSubmit;
	private EditText etManual;
	private AlertDialog connectionWarningDialog;
	private static View parentLayout;

	private static final Map<Integer, String> modifierKeys = KeyCodeTranslation.modifierKeys;
	private static final Map<Integer, String> keyEventCodes = KeyCodeTranslation.keyEventCodes;
	private static final Map<String, String> shiftChars = KeyCodeTranslation.shiftChars;

	private String modifier;

	private KeySender keySender;

	private SharedPreferences preferences;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (BuildConfig.DEBUG) {
			Timber.plant(new Timber.DebugTree());
		}

		setContentView(R.layout.activity_main);

		// Initialize UI elements
		etInput = findViewById(R.id.etKeyboardInput);
		btnSubmit = findViewById(R.id.btnKeyboard);
		etManual = findViewById(R.id.etManual);
		parentLayout = findViewById(android.R.id.content);

		preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

		// Start thread to send keys
		keySender = new KeySender(this);
		new Thread(keySender).start();

		btnSubmit.setOnClickListener(v -> {
			// Save text to send
			String sendStr = etManual.getText().toString();

			// If empty, don't do anything
			if (sendStr.equals("")) {
				return;
			}

			// Clear EditText if the user's preference is to clear it
			if (preferences.getBoolean("clear_manual_input", false)) {
				runOnUiThread(() -> etManual.setText(""));
			}

			// TODO: maybe send on another thread.
			//  	 save thread and wait until it's finished (if not null) to maintain order
			// Sends all keys
			for (int i = 0; i < sendStr.length(); i++) {
				String key = sendStr.substring(i, i + 1);
				String unshiftedKey = shiftChars.get(key); // If the key is a key + shift, this will be the original key
				if (unshiftedKey != null) {
					keySender.addKey("left-shift", unshiftedKey);
				} else {
					keySender.addKey(null, key);
				}
			}
		});

		//*
		// Listens for changes to the edittext
		// Detects printable characters (a-z, 0-9, etc.)
		etInput.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				Timber.d("String: %s | before-count: %s-%s", s.toString(), before, count);
				// Using a hacky workaround that clears the edittext after certain key events to
				// make arrow keys (and some others) get registered by onKeyDown (because it only
				// triggers when the key isn't consumed by the EditText)

				// Ignore if there is no text to send
				if (s.length() <= 0) {
					return;
				}
				if (s.toString().contains(" ")) {
					etInput.setText(null);
				}
				if (count - before > 1) { // If > 1 one character has changed, handle as an array
					// This should typically only contain a single character at a time, but I'm
					// handling it as an array of strings since if the app lags badly, it can
					// sometimes take a bit before it registers and it sends as several characters.
					String[] allKeys = s.toString().substring(before, count).split("");
					for (String key : allKeys) {
						keySender.addKey(null, key);
					}
				} else if (count > before && (count >= 0 && before >= 0)) { // If there is <= one more character in the edittext, just send the key
					keySender.addKey(null, s.subSequence(before, count).toString());
				} else {
					etInput.setText(null);
				}
				//etInput.getText().clear(); // Mitigates some of InputConnection warnings
			}

			public void afterTextChanged(Editable s) {
			}

			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}
		});
		//*/

		// Check if USB device connected
		// TODO: test if this code is device-specific, if so, grab file path from /sys/class/udc
		Activity mainContext = this;
		new Thread(() -> {
			String usbState;
			try {
				usbState = checkFileContentAsRoot("/sys/class/udc/a600000.dwc3/state");
			} catch (IOException e) {
				Timber.e(Log.getStackTraceString(e));
				Toast.makeText(mainContext, "USB Connection check failed.", Toast.LENGTH_SHORT).show();
				return;
			}
			if (!usbState.equals("configured")) {
				Timber.d("Cmd Output: %s", usbState);
				AlertDialog.Builder connectionWarningBuilder = new AlertDialog.Builder(MainActivity.this)
						.setTitle("USB state has been detected as disconnected.")
						.setMessage("The app will not work without a connected device. Connect a device now or ignore this warning.")
						.setNegativeButton(R.string.ignore, null)
						.setIcon(android.R.drawable.ic_dialog_alert);
				mainContext.runOnUiThread(() -> {
					connectionWarningDialog = connectionWarningBuilder.create();
					connectionWarningDialog.show();
				});
				final boolean[] isDialogDismissed = {false};
				while (!isDialogDismissed[0]) {
					try {
						String newUsbState;
						try {
							newUsbState = checkFileContentAsRoot("/sys/class/udc/a600000.dwc3/state");
						} catch (IOException e) {
							connectionWarningDialog.dismiss();
							Timber.e(Log.getStackTraceString(e));
							Toast.makeText(mainContext, "USB Connection check failed.", Toast.LENGTH_SHORT).show();
							break;
						}
						if (newUsbState.equals("configured")) {
							connectionWarningDialog.dismiss();
							break;
						}
						// Sleep a little between checks
						Thread.sleep(100);
					} catch (InterruptedException e) {
						connectionWarningDialog.dismiss();
						Timber.e(Log.getStackTraceString(e));
					}
				}
				connectionWarningDialog.setOnDismissListener(dialog -> isDialogDismissed[0] = true);
			}
		}).start();
	}

	// Listens for KeyEvents
	// Detects non-printing characters (tab, backspace, function keys, etc.) that aren't consumed
	// by the EditText
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (!etInput.hasFocus()) {
			Timber.d("Ignoring KeyEvent because the direct input edittext was not focused");
			return false;
		}
		if (KeyEvent.isModifierKey(keyCode)) {
			String modifier = modifierKeys.get(event.getKeyCode());
			Timber.d("modifier: %s", modifier);
		}

		String key = null;
		if ((key = keyEventCodes.get(event.getKeyCode())) != null) {
			keySender.addKey(modifier, key);
			modifier = null;
			Timber.d("onKeyDown key: %s", key);
		}
		Timber.d("keycode: %s", event.getKeyCode());
		return true;
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
			Toast.makeText(this, "help clicked", Toast.LENGTH_SHORT).show(); // DEBUG
		} else if (itemId == R.id.menuInfo) {
			Toast.makeText(this, "info clicked", Toast.LENGTH_SHORT).show(); // DEBUG
		} else if (itemId == R.id.menuDebug) {
			// Menu option that just runs whatever code I want to test in the app
			//SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
			/*
			Log.d(TAG, "PREF: " + preferences.getString("logging_level", "error"));
			try {
				Process proc = Runtime.getRuntime().exec("ls -l /data/data/me.arianb.usb_hid_client/hidg0");
				Log.d(TAG, "OUT: " + getProcessStdOutput(proc));
				Log.d(TAG, "ERR: " + getProcessStdError(proc));
			} catch (IOException e) {
				Log.e(TAG, Log.getStackTraceString(e));
			}
			*/
			makeSnackbar("debug", Snackbar.LENGTH_SHORT);
		}
		return super.onOptionsItemSelected(item);
	}

	private String checkFileContentAsRoot(String path) throws IOException {
		try {
			Process checkUSBConnectionProcess = Runtime.getRuntime().exec(new String[]{"su", "-c", "cat", path});
			// If this times out or fails, show error message as toast
			String processStdError = null;
			if (!checkUSBConnectionProcess.waitFor(1, TimeUnit.SECONDS) || !(processStdError = getProcessStdError(checkUSBConnectionProcess)).isEmpty()) {
				Timber.e(processStdError);
				throw new IOException();
			}
			// If USB device is not shown as "configured" then assume it's disconnected
			return getProcessStdOutput(checkUSBConnectionProcess).trim();
		} catch (InterruptedException | IOException e) {
			Timber.e(Log.getStackTraceString(e));
			throw new IOException();
		}
	}

	public static void makeSnackbar(String message, int length) {
		Snackbar.make(parentLayout, message, length).show();
	}
}