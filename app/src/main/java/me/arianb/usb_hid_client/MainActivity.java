package me.arianb.usb_hid_client;

import android.content.res.AssetManager;
import android.os.Bundle;
import android.os.Message;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

// TODO: fix these issues
// ctrl-backspace doesn't work bc backspace is (unless et is empty) registered outside of the keyListener
// some keys after +,',( sends twice. i have no idea why.
public class MainActivity extends AppCompatActivity {
	private EditText input;
	private Button btn;

	private String appFileDirectory;
	private String hidGadgetPath;

	private Map<Integer, String> modifierKeys;
	private Map<Integer, String> keyEventCodes;
	private Map<Character, String> shiftChars;

	private boolean nextKeyModified = false;
	private String modifier;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		input = findViewById(R.id.etKeyboardInput);
		btn = findViewById(R.id.btnKeyboard);

		modifierKeys = new HashMap<Integer, String>();
		keyEventCodes = new HashMap<Integer, String>();
		shiftChars = new HashMap<Character, String>();

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
		shiftChars.put('<', ",");
		shiftChars.put('>', ".");
		shiftChars.put('?', "/");
		shiftChars.put(':', ";");
		shiftChars.put('"', "'");
		shiftChars.put('{', "[");
		shiftChars.put('}', "]");
		shiftChars.put('|', "\\");
		shiftChars.put('~', "`");
		shiftChars.put('!', "1");
		shiftChars.put('@', "2");
		shiftChars.put('#', "3");
		shiftChars.put('$', "4");
		shiftChars.put('%', "5");
		shiftChars.put('^', "6");
		shiftChars.put('&', "7");
		shiftChars.put('*', "8");
		shiftChars.put('(', "9");
		shiftChars.put(')', "0");
		shiftChars.put('_', "-");
		shiftChars.put('+', "=");

		appFileDirectory = "/data/data/me.arianb.usb_hid_client";
		hidGadgetPath = appFileDirectory + "/hid-gadget";

		// Copy over binary (could compare existence/hashes before copying)
        copyAssets("hid-gadget");

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {}

			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				System.out.println("Diff: " + s); // DEBUG
				System.out.println(start + " " + before + " " + count); // DEBUG
				if (s.length() >= 1) {
					char newChar = s.subSequence(s.length() - 1, s.length()).charAt(0);
					String str = null;
					// TODO: move this if statement to sendKey fn
					if ((str = shiftChars.get(newChar)) != null) {
						sendKey(str, true);
						System.out.println("elif: " + str); // DEBUG
					} else {
						sendKey(Character.toString(newChar), false);
						System.out.println("else: " + newChar); // DEBUG
					}
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
            System.out.println("mod: " + modifier);
            return false;
        }

        String str = null;
        if ((str = keyEventCodes.get(event.getKeyCode())) != null) {
            sendKey(str, false);
            System.out.println("if: " + str); // DEBUG
        }
        System.out.println("CODE: " + event.getKeyCode());
        return true;
    }

	private void sendKey(String str, Boolean pressShift) {
		String options = "";
		String key = str;
		if(nextKeyModified) {
		    options += modifier;
		    nextKeyModified = false;
        }
		if (pressShift) {
			options += " --left-shift";
		}

		// Switch case is probably cleaner for this, i should fix it later
		// Translate character
		if (str.equals("\n")) {
			key = "enter";
		}
		// Escape characters
		else if (str.equals("\"")) {
			key = "\\\"";
		} else if (str.equals("\\")) {
		    key = "\\\\";
        } else if (str.equals("`")) {
            key = "\\`";
        }

		if (str.length() == 1 && Character.isUpperCase(str.charAt(0))) {
			options = "--left-shift";
			key = str.toLowerCase();
		}
		String[] shell = {"su", "-c", "echo \"" + key + "\" " + options + " | " + hidGadgetPath + " /dev/hidg0 keyboard"};
		try {
			Process process = Runtime.getRuntime().exec(shell);
			//System.out.println(printProcessStdOutput(process));
			System.out.println(printProcessStdError(process));
		} catch (IOException e) {
			e.printStackTrace();
		}
		// Bad workaround that clears the edittext after every key press to make arrow keys get
		// registered by onKeyDown(because it only triggers when the key doesn't touch the edittext)
		input.setText("");
	}

	private void copyAssets(String filename) {
		AssetManager assetManager = getAssets();

		InputStream in = null;
		OutputStream out = null;
		Log.d("tag", "Attempting to copy this file: " + filename); // + " to: " +       assetCopyDestination);

		try {
			in = assetManager.open(filename);
			Log.d("tag", "outDir: " + appFileDirectory);
			File outFile = new File(appFileDirectory, filename);
			out = new FileOutputStream(outFile);
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

	private String printProcessStdOutput(Process process) throws IOException {
		BufferedReader stdInput = new BufferedReader(new
				InputStreamReader(process.getInputStream()));
		// Read the output from the command
		System.out.println("Here is the standard output of the command:\n");
		String s = null;
		StringBuilder returnStr = new StringBuilder();
		while ((s = stdInput.readLine()) != null) {
			returnStr.append(s).append("\n");
		}
		return returnStr.toString();
	}

	private String printProcessStdError(Process process) throws IOException {
		BufferedReader stdError = new BufferedReader(new
				InputStreamReader(process.getErrorStream()));
		// Read any errors from the attempted command
		System.out.println("Here is the standard error of the command (if any):\n");
		String s = null;
		StringBuilder returnStr = new StringBuilder();
		while ((s = stdError.readLine()) != null) {
			returnStr.append(s).append("\n");
		}
		return returnStr.toString();
	}
}