package me.arianb.usb_hid_client;

import android.Manifest;
import android.content.Intent;
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
import androidx.core.app.ActivityCompat;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
	public static final String TAG = "hid-client";

	private EditText etInput;
	private Button btnSubmit;
	protected static TextView tvOutput;
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

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		modifierKeys = new HashMap<>();
		keyEventCodes = new HashMap<>();
		shiftChars = new HashMap<>();
		hidKeyCodes = new HashMap<>();
		hidModifierCodes = new HashMap<>();

		// TODO: if i feel like it later, I can change the maps to be more efficient, but if i do, then
		// 		 I should probably add comments labeling each line with its human-readable key
		// 		 current: keycode/char -> usable key -> hid code
		// 		 proposed: keycode/char -> hid code

		// Translate modifier keycodes into key
		modifierKeys.put(113, "left-ctrl");
		modifierKeys.put(114, "right-ctrl");
		modifierKeys.put(59, "left-shift");
		modifierKeys.put(60, "right-shift");
		modifierKeys.put(57, "left-alt");
		modifierKeys.put(58, "right-alt");
		modifierKeys.put(117, "left-meta");
		modifierKeys.put(118, "right-meta");

		// Translate keycodes into key
		keyEventCodes.put(29, "a");
		keyEventCodes.put(30, "b");
		keyEventCodes.put(31, "c");
		keyEventCodes.put(32, "d");
		keyEventCodes.put(33, "e");
		keyEventCodes.put(34, "f");
		keyEventCodes.put(35, "g");
		keyEventCodes.put(36, "h");
		keyEventCodes.put(37, "i");
		keyEventCodes.put(38, "j");
		keyEventCodes.put(39, "k");
		keyEventCodes.put(40, "l");
		keyEventCodes.put(41, "m");
		keyEventCodes.put(42, "n");
		keyEventCodes.put(43, "o");
		keyEventCodes.put(44, "p");
		keyEventCodes.put(45, "q");
		keyEventCodes.put(46, "r");
		keyEventCodes.put(47, "s");
		keyEventCodes.put(48, "t");
		keyEventCodes.put(49, "u");
		keyEventCodes.put(50, "v");
		keyEventCodes.put(51, "w");
		keyEventCodes.put(52, "x");
		keyEventCodes.put(53, "y");
		keyEventCodes.put(54, "z");
		keyEventCodes.put(131, "f1");
		keyEventCodes.put(132, "f2");
		keyEventCodes.put(133, "f3");
		keyEventCodes.put(134, "f4");
		keyEventCodes.put(135, "f5");
		keyEventCodes.put(136, "f6");
		keyEventCodes.put(137, "f7");
		keyEventCodes.put(138, "f8");
		keyEventCodes.put(139, "f9");
		keyEventCodes.put(140, "f10");
		keyEventCodes.put(141, "f11");
		keyEventCodes.put(142, "f12");
		keyEventCodes.put(19, "up");
		keyEventCodes.put(20, "down");
		keyEventCodes.put(21, "left");
		keyEventCodes.put(22, "right");
		keyEventCodes.put(61, "tab");
		keyEventCodes.put(67, "backspace");
		keyEventCodes.put(111, "escape");
		keyEventCodes.put(120, "print"); // and SysRq
		keyEventCodes.put(116, "scroll-lock");
		keyEventCodes.put(143, "num-lock");
		keyEventCodes.put(121, "pause");
		keyEventCodes.put(124, "insert");
		keyEventCodes.put(112, "delete");

		// Chars that are represented by another key + shift
		shiftChars.put("<", ",");
		shiftChars.put(">", ".");
		shiftChars.put("?", "/");
		shiftChars.put(":", ";");
		shiftChars.put("\"", "'");
		shiftChars.put("{", "[");
		shiftChars.put("}", "]");
		shiftChars.put("|", "\\");
		shiftChars.put("~", "`");
		shiftChars.put("!", "1");
		shiftChars.put("@", "2");
		shiftChars.put("#", "3");
		shiftChars.put("$", "4");
		shiftChars.put("%", "5");
		shiftChars.put("^", "6");
		shiftChars.put("&", "7");
		shiftChars.put("*", "8");
		shiftChars.put("(", "9");
		shiftChars.put(")", "0");
		shiftChars.put("_", "-");
		shiftChars.put("+", "=");

		// convert modifier to its HID scan code
		hidModifierCodes.put("no-modifier", "0");
		hidModifierCodes.put("left-ctrl", "x01");
		hidModifierCodes.put("left-shift", "x02");
		hidModifierCodes.put("left-alt", "x04");
		hidModifierCodes.put("left-meta", "x08");
		hidModifierCodes.put("right-ctrl", "x10");
		hidModifierCodes.put("right-shift", "x20");
		hidModifierCodes.put("right-alt", "x40");
		hidModifierCodes.put("right-meta", "x80");

		// convert character to its HID scan code
		hidKeyCodes.put("a", "x04");
		hidKeyCodes.put("b", "x05");
		hidKeyCodes.put("c", "x06");
		hidKeyCodes.put("d", "x07");
		hidKeyCodes.put("e", "x08");
		hidKeyCodes.put("f", "x09");
		hidKeyCodes.put("g", "x0a");
		hidKeyCodes.put("h", "x0b");
		hidKeyCodes.put("i", "x0c");
		hidKeyCodes.put("j", "x0d");
		hidKeyCodes.put("k", "x0e");
		hidKeyCodes.put("l", "x0f");
		hidKeyCodes.put("m", "x10");
		hidKeyCodes.put("n", "x11");
		hidKeyCodes.put("o", "x12");
		hidKeyCodes.put("p", "x13");
		hidKeyCodes.put("q", "x14");
		hidKeyCodes.put("r", "x15");
		hidKeyCodes.put("s", "x16");
		hidKeyCodes.put("t", "x17");
		hidKeyCodes.put("u", "x18");
		hidKeyCodes.put("v", "x19");
		hidKeyCodes.put("w", "x1a");
		hidKeyCodes.put("x", "x1b");
		hidKeyCodes.put("y", "x1c");
		hidKeyCodes.put("z", "x1d");

		hidKeyCodes.put("1", "x1e");
		hidKeyCodes.put("2", "x1f");
		hidKeyCodes.put("3", "x20");
		hidKeyCodes.put("4", "x21");
		hidKeyCodes.put("5", "x22");
		hidKeyCodes.put("6", "x23");
		hidKeyCodes.put("7", "x24");
		hidKeyCodes.put("8", "x25");
		hidKeyCodes.put("9", "x26");
		hidKeyCodes.put("0", "x27");

		hidKeyCodes.put("\n", "x28"); // line break -> enter
		hidKeyCodes.put("escape", "x29");
		hidKeyCodes.put("backspace", "x2a");
		hidKeyCodes.put("tab", "x2b");
		hidKeyCodes.put(" ", "x2c"); // " " -> space
		hidKeyCodes.put("-", "x2d");
		hidKeyCodes.put("=", "x2e");
		hidKeyCodes.put("[", "x2f");
		hidKeyCodes.put("]", "x30");
		hidKeyCodes.put("\\", "x31");
		hidKeyCodes.put("#", "x32"); // I think this is only for non-US layouts
		hidKeyCodes.put(";", "x33");
		hidKeyCodes.put("'", "x34");
		hidKeyCodes.put("`", "x35");
		hidKeyCodes.put(",", "x36");
		hidKeyCodes.put(".", "x37");
		hidKeyCodes.put("/", "x38");
		hidKeyCodes.put("caps-lock", "x39"); // Haven't tested a keyboard with a caps-lock key

		hidKeyCodes.put("f1", "x3a");
		hidKeyCodes.put("f2", "x3b");
		hidKeyCodes.put("f3", "x3c");
		hidKeyCodes.put("f4", "x3d");
		hidKeyCodes.put("f5", "x3e");
		hidKeyCodes.put("f6", "x3f");
		hidKeyCodes.put("f7", "x40");
		hidKeyCodes.put("f8", "x41");
		hidKeyCodes.put("f9", "x42");
		hidKeyCodes.put("f10", "x43");
		hidKeyCodes.put("f11", "x44");
		hidKeyCodes.put("f12", "x45");

		hidKeyCodes.put("print", "x46"); // and SysRq
		hidKeyCodes.put("scroll-lock", "x47");
		hidKeyCodes.put("pause", "x48");
		hidKeyCodes.put("insert", "x49");
		hidKeyCodes.put("home", "x4a");
		hidKeyCodes.put("page-up", "x4b");
		hidKeyCodes.put("delete", "x4c");
		hidKeyCodes.put("end", "x4d");
		hidKeyCodes.put("page-down", "x4e");

		hidKeyCodes.put("right", "x4f");
		hidKeyCodes.put("left", "x50");
		hidKeyCodes.put("down", "x51");
		hidKeyCodes.put("up", "x52");

		etInput = findViewById(R.id.etKeyboardInput);
		btnSubmit = findViewById(R.id.btnKeyboard);
		tvOutput = findViewById(R.id.tvOutput);
		etManual = findViewById(R.id.etManual);
		dropdownLogging = findViewById(R.id.spinner);

		tvOutput.setMovementMethod(new ScrollingMovementMethod());

		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
				R.array.logging_options, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		dropdownLogging.setAdapter(adapter);

		// TODO: fix issues with more verbose logging levels' output sometimes overwriting the less
		// 		 verbose outputs when switching from a more verbose level to less verbose level.
		dropdownLogging.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
				if (loggingThread != null) {
					// Pretty sure if thread updates tvOutput one more time before finishing being interrupted
					// it might overwrite the new thread's output until it re-rewrites the output.
					// Implement locks if this is an issue.
					loggingThread.interrupt();
				}
				String choice = parentView.getSelectedItem().toString();
				Log.d(TAG, "logging choice: " + choice);
				displayLogs(choice);
			}

			@Override
			public void onNothingSelected(AdapterView<?> parentView) {
			}
		});

		btnSubmit.setOnClickListener(v -> {
			// Debugging lines
			//ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 1);

			String sendStr = etManual.getText().toString();

			// Splits string into array with 1 character per element
			String[] sendStrArr = sendStr.split("");

			new Thread(() -> {
				for (String key: sendStrArr) {
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
					for (String key: allKeys) {
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
		if(!etInput.hasFocus()) {
			Log.d(TAG,"Ignoring KeyEvent because the direct input edittext was not focused");
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
		if(adjustedKey == null) {
			Log.e(TAG, "key: '" + key + "' could not be converted to an HID code (it wasn't found in the map).");
			return;
		}
		// Convert modifier to HID code
		sendModifier = hidModifierCodes.get(sendModifier);
		if(sendModifier == null) {
			Log.e(TAG, "mod: '" + modifier + "' could not be converted to an HID code (it wasn't found in the map).");
			return;
		}

		try {
			Log.i(TAG, "raw key: " + key + " | sending key: " + adjustedKey + " | modifier: " + sendModifier);

			// TODO: give app user permissions to write to /dev/hidg0 because privilege escalation causes a very significant performance hit
			// echo -en "\0\0\key\0\0\0\0\0" > /dev/hidg0 (as root) (presses key)
			String[] sendKeyCmd = {"su", "-c", "echo", "-en","\"\\" + sendModifier + "\\0\\" + adjustedKey + "\\0\\0\\0\\0\\0\" > /dev/hidg0"};
			// echo -en "\0" > /dev/hidg0 (as root) (releases key)
			String[] releaseKeyCmd = {"su", "-c", "echo", "-en", "\"\\0\" > /dev/hidg0"};

			Process sendProcess = Runtime.getRuntime().exec(sendKeyCmd);
			// Kill process if it doesn't complete within 1 seconds
			if(!sendProcess.waitFor(1, TimeUnit.SECONDS)) {
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
			if(!releaseErrors.isEmpty()) {
				Log.e(TAG, releaseErrors);
			}
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
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
		// Clear previous logs (reset it back to the default output)
		runOnUiThread(() -> tvOutput.setText(R.string.default_output));

		// Trim filter down to just the first letter because that's what logcat uses to filter
		String verbosityLetter = verbosityFilter.substring(0, 1);
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
					if (!Thread.interrupted() && line != null) { // TODO: find out if this extra thread interrupted check helps
						log.insert(0, line + "\n");
						runOnUiThread(() -> tvOutput.setText(log.toString()));
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				loggingThread = null;
			}
		});
		loggingThread.start();
		Log.d(TAG, "logging started with verbosity: " + verbosityFilter);
	}

	// method to inflate the options menu when
	// the user opens the menu for the first time
	@Override
	public boolean onCreateOptionsMenu( Menu menu ) {
		getMenuInflater().inflate(R.menu.main, menu);
		return super.onCreateOptionsMenu(menu);
	}

	// methods to control the operations that will
	// happen when user clicks on the action buttons
	@Override
	public boolean onOptionsItemSelected( @NonNull MenuItem item ) {

		switch (item.getItemId()){
			case R.id.menuSettings:
				//Toast.makeText(this, "settings Clicked", Toast.LENGTH_SHORT).show(); // DEBUG
				Intent intent = new Intent(this, SettingsActivity.class);
				startActivity(intent);
				break;
			case R.id.menuHelp:
				Toast.makeText(this, "help Clicked", Toast.LENGTH_SHORT).show(); // DEBUG
				break;
			case R.id.menuInfo:
				Toast.makeText(this, "info Clicked", Toast.LENGTH_SHORT).show(); // DEBUG
				break;
		}
		return super.onOptionsItemSelected(item);
	}
}