package me.arianb.usb_hid_client;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {
    private EditText input;
    private Button btn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        input = findViewById(R.id.etKeyboardInput);
        btn = findViewById(R.id.btnKeyboard);

        btn.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                InputMethodManager imm = (InputMethodManager)
                        getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(getCurrentFocus(), InputMethodManager.SHOW_IMPLICIT);
                Toast.makeText(MainActivity.this, "Button Clicked", Toast.LENGTH_SHORT).show();
            }

        });

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                System.out.println("String: " + s.toString());
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // TODO Auto-generated method stub
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                System.out.println("Diff: " + s);
                System.out.println(start + " " + before + " " + count);
            }
        });
    }
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        Log.i("key pressed", (Character.valueOf((char)event.getUnicodeChar())).toString());
        System.out.println(event.getUnicodeChar());
        return super.dispatchKeyEvent(event);
    }

}