package me.arianb.usb_hid_client;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreference;

import com.topjohnwu.superuser.Shell;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;

import timber.log.Timber;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);

        setTitle(R.string.settings);

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new SettingsFragment())
                    .commit();
        }

        // Enable back button
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            final String LOG_MIME_TYPE = "text/plain";
            ActivityResultLauncher<String> createLogFile = registerForActivityResult(new ActivityResultContracts.CreateDocument(LOG_MIME_TYPE),
                    uri -> {
                        // If the user doesn't choose a location to save the file, don't continue
                        if (uri == null) {
                            return;
                        }

                        try {
                            StringBuilder stringBuilder = new StringBuilder();
                            String command;

                            if (Shell.getShell().isRoot()) {
                                command = String.format("logcat -e '%s' -t 1000", BuildConfig.APPLICATION_ID);
                                stringBuilder.append(getCommandInLogFormatString(command, "Logcat"));

                                command = "ls -lAhZ /config/usb_gadget";
                                stringBuilder.append(getCommandInLogFormatString(command, "Gadgets Directory"));

                                command = "echo KERNEL_VERSION=$(uname -r |cut -d '-' -f1 ) && (gunzip -c /proc/config.gz | grep -i configfs | sed 's/# //; s/ is not set/=NOT_SET/')";
                                stringBuilder.append(getCommandInLogFormatString(command, "Kernel Config"));
                            } else {
                                stringBuilder.append("Could not create root shell. Was the app given root permissions?").append("\n");
                            }

                            Timber.d(stringBuilder.toString());

                            // Write out file
                            OutputStream output = requireContext().getContentResolver().openOutputStream(uri);
                            if (output == null) {
                                Timber.e("Failed to open output stream for writing log file.");
                                return;
                            }
                            output.write(stringBuilder.toString().getBytes());
                            output.flush();
                            output.close();
                            Timber.d("Successfully exported logs");
                        } catch (IOException e) {
                            Timber.e("Error occurred while exporting logs");
                            Timber.e(Log.getStackTraceString(e));
                        }
                    });

            setPreferencesFromResource(R.xml.root_preferences, rootKey);

            SwitchPreference debugModeSwitch = findPreference(getString(R.string.debug_mode_key));
            if (debugModeSwitch != null) {
                debugModeSwitch.setOnPreferenceChangeListener((preference, newValue) -> {
                    SwitchPreference switchPreference = ((SwitchPreference) preference);
                    if (switchPreference.isChecked()) {
                        Timber.plant(new Timber.DebugTree()); // Enable logging
                    } else {
                        Timber.uprootAll(); // Disable logging
                    }
                    return true;
                });
            } else {
                Timber.e("SettingsActivity.java: debugModeSwitch is null");
            }

            Preference exportLogsButton = findPreference(getString(R.string.export_debug_logs_btn_key));
            if (exportLogsButton != null) {
                exportLogsButton.setOnPreferenceClickListener(preference -> {
                    // Export file containing log info
                    long unixTime = System.currentTimeMillis() / 1000;
                    createLogFile.launch(String.format(Locale.US, "debug_log_%s_%d.txt", BuildConfig.APPLICATION_ID, unixTime));
                    return true;
                });
            } else {
                Timber.e("SettingsActivity.java: exportLogsButton is null");
            }
        }
    }

    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        // Make activity back button work
        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        startActivity(intent);
        return true;
    }

    public static String getCommandInLogFormatString(String command, String title) {
        StringBuilder logStringBuilder = new StringBuilder();
        final String halfDivider = "------------------------------";
        final String divider = halfDivider + halfDivider;

        Shell.Result commandResult = Shell.cmd(command).exec();
        String commandResultMultiline = String.join("\n", commandResult.getOut());

        logStringBuilder.append(String.format("%s %s %s", halfDivider, title, halfDivider)).append("\n");
        logStringBuilder.append(commandResultMultiline).append("\n");
        logStringBuilder.append(divider + "\n");

        return logStringBuilder.toString();
    }
}