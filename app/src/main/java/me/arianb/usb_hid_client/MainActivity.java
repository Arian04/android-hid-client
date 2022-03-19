package me.arianb.usb_hid_client;

import android.content.res.AssetManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
	private static final String TAG = "hid-client";

	private EditText etInput;
	private Button btnSubmit;
	private TextView tvOutput;
	private EditText etManual;
	private Spinner dropdownLogging;

	private Map<Integer, String> modifierKeys;
	private Map<Integer, String> keyEventCodes;
	private Map<String, String> shiftChars;
	private Map<String, String> hidCodes;

	private boolean nextKeyModified = false;
	private String modifier;

	private Thread loggingThread;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		modifierKeys = new HashMap<>();
		keyEventCodes = new HashMap<>();
		shiftChars = new HashMap<>();
		hidCodes = new HashMap<>();

		// Translate modifier keycodes into key
		modifierKeys.put(113, "--left-ctrl");
		modifierKeys.put(114, "--right-ctrl");
		modifierKeys.put(59, "--left-shift");
		modifierKeys.put(60, "--right-shift");
		modifierKeys.put(57, "--left-alt");
		modifierKeys.put(58, "--right-alt");
		modifierKeys.put(117, "--left-meta");
		modifierKeys.put(118, "--right-meta");

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

		// convert character to its HID scan code
		hidCodes.put("a", "x04");
		hidCodes.put("b", "x05");
		hidCodes.put("c", "x06");
		hidCodes.put("d", "x07");
		hidCodes.put("e", "x08");
		hidCodes.put("f", "x09");
		hidCodes.put("g", "x0a");
		hidCodes.put("h", "x0b");
		hidCodes.put("i", "x0c");
		hidCodes.put("j", "x0d");
		hidCodes.put("k", "x0e");
		hidCodes.put("l", "x0f");
		hidCodes.put("m", "x10");
		hidCodes.put("n", "x11");
		hidCodes.put("o", "x12");
		hidCodes.put("p", "x13");
		hidCodes.put("q", "x14");
		hidCodes.put("r", "x15");
		hidCodes.put("s", "x16");
		hidCodes.put("t", "x17");
		hidCodes.put("u", "x18");
		hidCodes.put("v", "x19");
		hidCodes.put("w", "x1a");
		hidCodes.put("x", "x1b");
		hidCodes.put("y", "x1c");
		hidCodes.put("z", "x1d");

		hidCodes.put("1", "x1e");
		hidCodes.put("2", "x1f");
		hidCodes.put("3", "x20");
		hidCodes.put("4", "x21");
		hidCodes.put("5", "x22");
		hidCodes.put("6", "x23");
		hidCodes.put("7", "x24");
		hidCodes.put("8", "x25");
		hidCodes.put("9", "x26");
		hidCodes.put("0", "x27");

		hidCodes.put("\n", "x28"); // line break -> enter
		hidCodes.put("escape", "x29");
		hidCodes.put("backspace", "x2a");
		hidCodes.put("tab", "x2b");
		hidCodes.put(" ", "x2c"); // " " -> space
		hidCodes.put("-", "x2d");
		hidCodes.put("=", "x2e");
		hidCodes.put("[", "x2f");
		// TODO: finish map (i stopped to test the app and am now fixing that instead)

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

		btnSubmit.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				String sendStr = etManual.getText().toString();

				// Splits string into array with 1 character per element
				String[] sendStrArr = sendStr.split("");

				for (String key: sendStrArr) {
					new Thread(() -> sendKey(key)).start();
				}
			}
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
						new Thread(() -> sendKey(key)).start();
						Log.d(TAG, "textChanged key: " + key);

						// Hacky workaround that clears the edittext after every key press to
						// make arrow keys get registered by onKeyDown (because it only triggers
						// when the key doesn't touch the edittext)
						etInput.setText("");
					}
				}
			}
		});
	}

	// Listens for KeyEvents
	// Detects non-printing characters (tab, backspace, function keys, etc.)
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

	private void sendKey(String key) {
		if (key == null) {
			Log.e(TAG, "sendKey received null key value");
			return;
		}

		String options = "";
		String adjustedKey = key;
		if (nextKeyModified) {
			options += modifier;
			nextKeyModified = false;
		}
		String str = null;
		if ((str = shiftChars.get(key)) != null) {
			adjustedKey = str;
			options += " --left-shift";
			Log.d(TAG, "adding shift option to make: " + adjustedKey + " -> " + key);
		}

		switch (key) {
			// Escape characters (Escape them once for Java and again for the shell command)
			case "\"":
				adjustedKey = "\\\""; // \" = "
				break;
			case "\\":
				adjustedKey = "\\\\"; // \\ = \
				break;
			case "`":
				adjustedKey = "\\`"; // \` = `
				break;
		}

		if (key.length() == 1 && Character.isUpperCase(key.charAt(0))) {
			options = "--left-shift";
			adjustedKey = str.toLowerCase();
		}

		// Convert key to HID code
		adjustedKey = hidCodes.get(adjustedKey);
		if(adjustedKey == null) {
			Log.e(TAG, "key: '" + key + "' could not be converted to an HID code (it wasn't found in the map).");
			return;
		}

		try {
			Log.i(TAG, "raw key: " + key + " | sending key: " + adjustedKey);
			// TODO: give app user permissions to write to /dev/hidg0 because privilege escalation causes a very significant performance hit
			// echo -en "\0\0\key\0\0\0\0\0" > /dev/hidg0 (as root) (presses key)
			String[] sendKeyCmd = {"su", "-c", "echo", "-en","\"\\0\\0\\" + adjustedKey + "\\0\\0\\0\\0\\0\" > /dev/hidg0"};
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
			String errors = getProcessStdError(sendProcess);
			if (!errors.isEmpty()) {
				Log.e(TAG, errors);
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
		// Clear previous logs
		runOnUiThread(() -> tvOutput.setText("No Output"));

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
					if (line != null) {
						log.insert(0, line + "\n");
						runOnUiThread(() -> tvOutput.setText(log.toString()));
					}
				}
			} catch (IOException e) {
				Log.e("tag", "ioexc in logging");
			}
		});
		loggingThread.start();
		Log.d("tag", "logging started with verbosity: " + verbosityFilter);
	}
}