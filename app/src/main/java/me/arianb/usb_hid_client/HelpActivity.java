package me.arianb.usb_hid_client;

import android.content.Intent;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

public class HelpActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);

        setTitle(R.string.help);

        // Enable back button
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        // Make links clickable
        TextView tvHelpFAQ;
        tvHelpFAQ = findViewById(R.id.tvHelpFAQ_a1);
        tvHelpFAQ.setMovementMethod(LinkMovementMethod.getInstance());
        tvHelpFAQ = findViewById(R.id.tvHelpFAQ_a3);
        tvHelpFAQ.setMovementMethod(LinkMovementMethod.getInstance());
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        // Make activity back button work
        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        startActivity(intent);
        return true;
    }
}