package me.arianb.usb_hid_client;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreference;

import com.google.android.material.snackbar.Snackbar;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

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
            ActivityResultLauncher<String> createLogFile = registerForActivityResult(new ActivityResultContracts.CreateDocument(),
                    uri -> {
                        // If the user doesn't choose a location to save the file, don't continue
                        if (uri == null) {
                            return;
                        }

                        try {
                            // Get logs
                            // Using root to get logs instead of just $(logcat) because it was prompting
                            // the user asking for permission to view logs and I don't feel that's necessary
                            // and we already have root permissions so why not
                            final String LOG_CONTENT;
                            Process getLogsShell = Runtime.getRuntime().exec("su");
                            DataOutputStream getLogsOS = new DataOutputStream(getLogsShell.getOutputStream());
                            getLogsOS.writeBytes(String.format("logcat -d --pid=$(pidof -s %s)", BuildConfig.APPLICATION_ID) + "\n");
                            getLogsOS.flush();
                            getLogsOS.writeBytes("exit" + "\n");
                            getLogsOS.flush();

                            // Error checking for getting logs
                            if (!getLogsShell.waitFor(1000, TimeUnit.MILLISECONDS)) {
                                String errorMessage = "Process timed out while getting logs";
                                Timber.e(errorMessage);
                                Snackbar.make(requireView(), errorMessage, Snackbar.LENGTH_LONG).show();
                                getLogsShell.destroy();
                                return;
                            }
                            String stderr = ProcessStreamHelper.getProcessStdError(getLogsShell);
                            String stdout = ProcessStreamHelper.getProcessStdOutput(getLogsShell);
                            if (!stderr.isEmpty()) {
                                String errorMessage = "Error occurred while getting logs";
                                Timber.e(errorMessage + ": " + stderr);
                                Snackbar.make(requireView(), errorMessage, Snackbar.LENGTH_LONG).show();
                                return;
                            }
                            LOG_CONTENT = stdout;

                            // Write out file
                            OutputStream output = SettingsFragment.this.requireContext().getContentResolver().openOutputStream(uri);
                            output.write(LOG_CONTENT.getBytes());
                            output.flush();
                            output.close();
                            Timber.d("Successfully exported logs");
                        } catch (IOException e) {
                            Timber.e("Error occurred while exporting logs: %s", Log.getStackTraceString(e));
                        } catch (InterruptedException e) {
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

    public boolean onOptionsItemSelected(MenuItem item) {
        // Make activity back button work
        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        startActivity(intent);
        return true;
    }
}