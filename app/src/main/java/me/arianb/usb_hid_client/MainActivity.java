package me.arianb.usb_hid_client;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.AssetManager;
import android.os.Bundle;
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

// TODO: package C binary with app
//       - i did it but now some keys are broken
// TODO: make it detect all keys (function keys, SysRq, etc.)
//       - i think i might just have to create some manual special buttons for that purpose

public class MainActivity extends AppCompatActivity {
    private EditText input;
    private Button btn;

    private String appFileDirectory;
    private String hidGadgetPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        input = findViewById(R.id.etKeyboardInput);
        btn = findViewById(R.id.btnKeyboard);

        //appFileDirectory = getFilesDir().getPath();
        appFileDirectory = "/data/data/me.arianb.usb_hid_client";
        hidGadgetPath = appFileDirectory + "/hid-gadget";

        // If binary isn't present, copy it over.
        if (!new File(hidGadgetPath).exists()) {
            copyAssets("hid-gadget");
        }

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                System.out.println("Diff: " + s);
                System.out.println(start + " " + before + " " + count);
                if (before > count) { // I think this conditional is accurate but haven't finished testing
                    System.out.println("Backspace");
                    sendKey("backspace");
                } else {
                    CharSequence newChar = s.subSequence(s.length() - 1, s.length());
                    System.out.println(newChar);
                    sendKey(newChar.toString());
                }
            }
        });
    }


    private void sendKey(String str) {
        String options = "";
        String key = str;
        if (str.length() == 1 && Character.isUpperCase(str.charAt(0))) {
            options = "--left-shift";
            key = str.toLowerCase();
            System.out.println(str);
        }
        System.out.println(hidGadgetPath);
        String[] shell = {"su", "-c", "echo " + key + " " + options + " | " + hidGadgetPath + " /dev/hidg0 keyboard"};
        try {
            Process process = Runtime.getRuntime().exec(shell);
            BufferedReader stdInput = new BufferedReader(new
                    InputStreamReader(process.getInputStream()));

            BufferedReader stdError = new BufferedReader(new
                    InputStreamReader(process.getErrorStream()));

            // Read the output from the command
            System.out.println("Here is the standard output of the command:\n");
            String s = null;
            while ((s = stdInput.readLine()) != null) {
                System.out.println(s);
            }
            // Read any errors from the attempted command
            System.out.println("Here is the standard error of the command (if any):\n");
            while ((s = stdError.readLine()) != null) {
                System.out.println(s);
            }

        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("ioexception");
        }
    }

    // detects when enter is pressed??
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        System.out.println("CODE: " + event.getKeyCode());
        return false;
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
}