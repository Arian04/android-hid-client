package me.arianb.usb_hid_client;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Locale;

// TODO: package C binary with app
// TODO: make it detect all keys (function keys, SysRq, etc.)
//       - i think i might just have to create some manual special buttons for that purpose
// pretty sure in c code, single quote is mapped to double quote

public class MainActivity extends AppCompatActivity {
    private EditText input;
    private Button btn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        input = findViewById(R.id.etKeyboardInput);
        btn = findViewById(R.id.btnKeyboard);

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {}

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                System.out.println("Diff: " + s);
                System.out.println(start + " " + before + " " + count);
                if(before > count) {
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
        if(str.length() == 1 && Character.isUpperCase(str.charAt(0))) {
            options = "--left-shift";
            key = str.toLowerCase();
            System.out.println(str);
        }
        String[] shell = {"su", "-c", "echo " + key + " " + options + " | hid-gadget /dev/hidg0 keyboard"};
        try {
            Process process = Runtime.getRuntime().exec(shell);
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("ioexc");
        }
    }

    // detects when enter is pressed??
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        System.out.println("CODE: " + event.getKeyCode());
        return false;
    }
}