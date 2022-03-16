package me.arianb.usb_hid_client;

import android.content.res.AssetManager;
import android.net.http.SslCertificate;
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

/* TODO: fix these issues
    - ctrl-backspace doesn't work bc ctrl doesn't send a code when paired with backspace apparently
    - ctrl <arrow key> is broken in a weird way, seems to work on second arrow press only sometimes
*/
public class MainActivity extends AppCompatActivity {
	private static final String TAG = "hid-client";

	private EditText etInput;
	private Button btnSubmit;
	private TextView tvOutput;
	private EditText etManual;
	private Spinner dropdownLogging;

	private String appFileDirectory;
	private String hidGadgetPath;

	private Map<Integer, String> modifierKeys;
	private Map<Integer, String> keyEventCodes;
	private Map<String, String> shiftChars;

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

		appFileDirectory = "/data/data/me.arianb.usb_hid_client";
		hidGadgetPath = appFileDirectory + "/hid-gadget";

		// Copy over binary (could compare existence/hashes before copying)
        copyAssets("hid-gadget");

		dropdownLogging.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parentView, View selectedItemView, int position, long id) {
				if(loggingThread != null) {
					// Pretty sure if thread updates tvOutput one more time before finishing being interrupted
					// it might overwrite the new thread's output until it re-rewrites the output.
					// Implement locks if this is an issue.
					loggingThread.interrupt();
				}
				String choice = parentView.getSelectedItem().toString();
				Log.d(TAG,"logging choice: " + choice);
				displayLogs(choice);
			}

			@Override
			public void onNothingSelected(AdapterView<?> parentView) {}
		});


		etInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				Log.d(TAG,"Diff: " + s);
				Log.d(TAG, start + " " + before + " " + count);
				if (s.length() >= 1) {
					char newChar = s.subSequence(s.length() - 1, s.length()).charAt(0);
					String str = null;
					sendKey(Character.toString(newChar));
					Log.d(TAG, "textChanged key: " + newChar);
				}
			}
		});
    }

    // detects non-printing keys
    // TODO: handle issues of edittext watcher and onKeyDown listener detecting the same press
    //       currently not an issue, but it might become one later
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (KeyEvent.isModifierKey(keyCode)) {
            modifier = modifierKeys.get(event.getKeyCode());
            nextKeyModified = true;
			Log.d(TAG, "modifier: " + modifier);
        }

        String str = null;
        if ((str = keyEventCodes.get(event.getKeyCode())) != null) {
            sendKey(str);
			Log.d(TAG, "onKeyDown key: " + str);
        }
		Log.d(TAG, "keycode: " + event.getKeyCode());
        return true;
    }

	private void sendKey(String key) {
		if(key == null) {
			Log.e(TAG, "sendKey received null key value");
			return;
		}

		String options = "";
		String adjustedKey = key;
		if(nextKeyModified) {
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
			// Translate character
			case "\n":
				adjustedKey = "enter";
				break;
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
		Log.i(TAG, "raw key: " + key + " | sending key: " + adjustedKey);
		String[] shell = {"su", "-c", "echo \"" + adjustedKey + "\" " + options + " | " + hidGadgetPath + " /dev/hidg0 keyboard"};
		try {
			Process process = Runtime.getRuntime().exec(shell);
			String errors = getProcessStdError(process);
			if (!errors.isEmpty()) {
				Log.e(TAG, errors);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		// Bad workaround that clears the edittext after every key press to make arrow keys get
		// registered by onKeyDown(because it only triggers when the key doesn't touch the edittext)
		etInput.setText("");
	}

	private void copyAssets(String filename) {
		AssetManager assetManager = getAssets();

		Log.d("tag", "Attempting to copy this file: " + filename); // + " to: " +       assetCopyDestination);

		try {
			InputStream in = assetManager.open(filename);
			Log.d("tag", "outDir: " + appFileDirectory);
			File outFile = new File(appFileDirectory, filename);
			OutputStream out = new FileOutputStream(outFile);
			byte[] buffer = new byte[102400];
			int len = in.read(buffer);
			while (len != -1) {
				out.write(buffer, 0, len);
				len = in.read(buffer);
			}
			in.close();
			in = null;
			out.flush();
			out.close();
			out = null;
			File execFile = new File(hidGadgetPath);
			execFile.setExecutable(true);
		} catch (IOException e) {
			Log.e("tag", "Failed to copy asset file: " + filename, e);
		}
		Log.d("tag", "Copy success: " + filename);
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
		String verbosityLetter = verbosityFilter.substring(0,1);
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
			}
			catch (IOException e) {
				Log.e("tag", "ioexc in logging");
			}
		});
		loggingThread.start();
		Log.d("tag","logging started with verbosity: " + verbosityFilter);
	}
}