package me.arianb.usb_hid_client;

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
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
	public static final String TAG = "hid-client";

	private EditText etInput;
	private Button btnSubmit;
	private TextView tvOutput;
	private EditText etManual;
	private Spinner dropdownLogging;

	private Map<Integer, String> modifierKeys;
	private Map<Integer, String> keyEventCodes;
	private Map<String, String> shiftChars;
	private Map<String, String> hidKeyCodes;
	private Map<String, String> hidModifierCodes;

	private boolean nextKeyModified = false;
	private String modifier;

	private Thread loggingThread = null;

	private SharedPreferences preferences;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		KeyCodeTranslation code = new KeyCodeTranslation();
		modifierKeys = code.modifierKeys;
		keyEventCodes = code.keyEventCodes;
		shiftChars = code.shiftChars;
		hidKeyCodes = code.hidKeyCodes;
		hidModifierCodes = code.hidModifierCodes;

		etInput = findViewById(R.id.etKeyboardInput);
		btnSubmit = findViewById(R.id.btnKeyboard);
		tvOutput = findViewById(R.id.tvOutput);
		etManual = findViewById(R.id.etManual);
		dropdownLogging = findViewById(R.id.spinner);

		preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		new SettingsActivity.SettingsFragment(this);

		tvOutput.setMovementMethod(new ScrollingMovementMethod());

		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
				R.array.logging_options, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		dropdownLogging.setAdapter(adapter);

		dropdownLogging.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
				String choice = parentView.getSelectedItem().toString();
				displayLogs(choice);
			}

			@Override
			public void onNothingSelected(AdapterView<?> parentView) {
			}
		});

		btnSubmit.setOnClickListener(v -> {
			// Save text to send
			String sendStr = etManual.getText().toString();

			// Clear EditText if the user's preference is to clear it
			if (preferences.getBoolean("clearManualInput", false)) {
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
			@Override
			public void afterTextChanged(Editable s) {
			}

			@Override
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
	// Detects non-printing characters (tab, backspace, function keys, etc.) that aren't consumed by
	// the EditText
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
			String finalKey = key;
			new Thread(() -> sendKey(finalKey)).start();
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
		adjustedKey = hidKeyCodes.get(adjustedKey);
		if (adjustedKey == null) {
			Log.e(TAG, "key: '" + key + "' could not be converted to an HID code (it wasn't found in the map).");
			return;
		}
		// Convert modifier to HID code
		sendModifier = hidModifierCodes.get(sendModifier);
		if (sendModifier == null) {
			Log.e(TAG, "mod: '" + modifier + "' could not be converted to an HID code (it wasn't found in the map).");
			return;
		}

		try {
			Log.i(TAG, "raw key: " + key + " | sending key: " + adjustedKey + " | modifier: " + sendModifier);

			// TODO: give app user permissions to write to /dev/hidg0 because privilege escalation causes a very significant performance hit
			// echo -en "\modifier\0\key\0\0\0\0\0" > /dev/hidg0 (as root) (presses key)
			String[] sendKeyCmd = {"su", "-c", "echo", "-en", "\"\\" + sendModifier + "\\0\\" + adjustedKey + "\\0\\0\\0\\0\\0\" > /dev/hidg0"};
			// echo -en "\0\0\0\0\0\0\0\0" > /dev/hidg0 (as root) (releases key)
			String[] releaseKeyCmd = {"su", "-c", "echo", "-en", "\"\\0\\0\\0\\0\\0\\0\\0\\0\" > /dev/hidg0"};

			Process sendProcess = Runtime.getRuntime().exec(sendKeyCmd);
			// Kill process if it doesn't complete within 1 seconds
			if (!sendProcess.waitFor(1, TimeUnit.SECONDS)) {
				Log.e(TAG, "Timed out while sending key. Make sure a computer is connected.");
				sendProcess.destroy();
				return;
			}
			Process releaseProcess = Runtime.getRuntime().exec(releaseKeyCmd);

			String sendErrors = getProcessStdError(sendProcess);
			String releaseErrors = getProcessStdError(releaseProcess);
			if (!sendErrors.isEmpty()) {
				Log.e(TAG, sendErrors);
			}
			if (!releaseErrors.isEmpty()) {
				Log.e(TAG, releaseErrors);
			}
		} catch (IOException | InterruptedException e) {
			Log.e(TAG, Arrays.toString(e.getStackTrace()));
		}
	}

	private String getProcessStdOutput(Process process) throws IOException {
		BufferedReader stdInput = new BufferedReader(new
				InputStreamReader(process.getInputStream()));
		// Read the output from the command
		String s = null;
		StringBuilder returnStr = new StringBuilder();
		while ((s = stdInput.readLine()) != null) {
			returnStr.append(s).append("\n");
		}
		return returnStr.toString();
	}

	private String getProcessStdError(Process process) throws IOException {
		BufferedReader stdError = new BufferedReader(new
				InputStreamReader(process.getErrorStream()));
		// Read any errors from the attempted command
		String s = null;
		StringBuilder returnStr = new StringBuilder();
		while ((s = stdError.readLine()) != null) {
			returnStr.append(s).append("\n");
		}
		return returnStr.toString();
	}

	private void displayLogs(String verbosityFilter) {
		if (loggingThread != null) {
			// Pretty sure if thread updates tvOutput one more time before finishing being interrupted
			// it might overwrite the new thread's output until it re-rewrites the output.
			// Implement locks if this is an issue.
			loggingThread.interrupt();

			// IDK how I feel about this workaround
			Log.e(TAG, "[ignore] NOT AN ERROR. This is being logged to trigger the logging thread to check if it's been interrupted.");
			Log.d(TAG, "logging choice: " + verbosityFilter);
			try {
				if (loggingThread != null) {
					loggingThread.join();
				}
			} catch (InterruptedException e) {
				Log.e(TAG, Arrays.toString(e.getStackTrace()));
			}
		}

		// Trim filter down to just the first letter (because that's what logcat uses to filter the
		// logs) and make sure it's uppercase for good measure since that's also necessary
		String verbosityLetter = verbosityFilter.substring(0, 1).toUpperCase();
		loggingThread = new Thread(() -> {
			try {
				String command = String.format("logcat -s hid-client:%s -v raw", verbosityLetter);
				Process process = Runtime.getRuntime().exec(command);
				BufferedReader bufferedReader = new BufferedReader(
						new InputStreamReader(process.getInputStream()));

				// Discard the first line of logcat ("---beginning of main---")
				bufferedReader.readLine();

				StringBuilder log = new StringBuilder();
				String line;
				while (!Thread.interrupted()) {
					line = bufferedReader.readLine();
					if (!Thread.interrupted()) {
						if (line != null && !line.matches("\\[ignore\\].*")) {
							log.insert(0, line + "\n");
							runOnUiThread(() -> tvOutput.setText(log.toString()));
						}
					} else {
						Log.d(TAG, "Logging Thread interrupted. Logging Level: " + verbosityFilter);
						break;
					}
				}
				// Kill logcat process before ending thread
				process.destroy();
			} catch (IOException e) {
				Log.e(TAG, Arrays.toString(e.getStackTrace()));
			} finally {
				// Clear previous logs (reset it back to the default output)
				runOnUiThread(() -> tvOutput.setText(R.string.default_output));
				loggingThread = null;
			}
			//Log.e(TAG, "Thread actually ended: " + verbosityLetter); // DEBUG
		});
		loggingThread.start();
		Log.d(TAG, "logging started with verbosity: " + verbosityFilter);
	}

	public void setLoggingThread(Thread t) {
		loggingThread = t;
	}

	public Thread getLoggingThread() {
		return loggingThread;
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
				Log.e(TAG, Arrays.toString(e.getStackTrace()));
			}
		}
		return super.onOptionsItemSelected(item);
	}
}