package me.arianb.usb_hid_client;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.ScrollingMovementMethod;
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

import java.util.Map;

import timber.log.Timber;

// TODO: - Check if everything is properly explained in comments
//		 - Mod keys are broken
// 		 - direct input broken with multiple words, honestly implementing a KeyboardView is probably
// 		   the best solution, this is causing more issues than it's worth
// 	     - settle on a way to refer to certain situations (such as referring to a key that has been
// 	       initially pressed with shift, like !@#$%^&*(), and/or referring to the original key if the
// 	       user had not pressed shift, in the example given above, 1234567890) and explain them here
// 		   rather than explaining them over and over in each comment.
public class MainActivity extends AppCompatActivity {
	private EditText etInput;
	private Button btnSubmit;
	private TextView tvOutput;
	private EditText etManual;

	private static final Map<Integer, String> modifierKeys = KeyCodeTranslation.modifierKeys;
	private static final Map<Integer, String> keyEventCodes = KeyCodeTranslation.keyEventCodes;
	private static final Map<String, String> shiftChars = KeyCodeTranslation.shiftChars;

	private Logger logger;

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
		tvOutput = findViewById(R.id.tvOutput);
		etManual = findViewById(R.id.etManual);

		preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		SettingsActivity.SettingsFragment.setContext(this);

		// Logging
		// TODO: logging is currently temporarily disabled, remove it later.
		logger = new Logger(this, tvOutput);
		logger.watchForPreferenceChanges(preferences);

		// Start thread to send keys
		keySender = new KeySender(getApplicationContext());
		new Thread(keySender).start();

		tvOutput.setMovementMethod(new ScrollingMovementMethod());

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
				if (count < before) { // If the amount of text has decreased, send backspace
					keySender.addKey(null, "backspace");
					etInput.setText("");
				} else if (count - before > 1) { // If > 1 one character has changed, handle as an array
					// This should typically only contain a single character at a time, but I'm
					// handling it as an array of strings since if the app lags badly, it can
					// sometimes take a bit before it registers and it sends as several characters.
					String[] allKeys = s.toString().substring(before, count).split("");
					for (String key : allKeys) {
						keySender.addKey(null, key);
					}
				} else { // If there is <= one more character in the edittext, just send the key
					keySender.addKey(null, s.subSequence(before, count).toString());
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
			Timber.d("Ignoring KeyEvent because the direct input edittext was not focused");
			return false;
		}
		if (KeyEvent.isModifierKey(keyCode)) {
			String modifier = modifierKeys.get(event.getKeyCode());
			Timber.d("modifier: %s", modifier);
		}

		String key = null;
		if ((key = keyEventCodes.get(event.getKeyCode())) != null) {
			// Hacky workaround that clears the edittext after every key press to
			// make arrow keys get registered by onKeyDown (because it only triggers
			// when the key doesn't touch the EditText)
			//etInput.getText().clear(); // Mitigates some of InputConnection warnings
			etInput.setText("");

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
		}
		return super.onOptionsItemSelected(item);
	}
}