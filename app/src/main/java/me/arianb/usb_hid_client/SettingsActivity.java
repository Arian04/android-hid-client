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

import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;

import me.arianb.usb_hid_client.shell_utils.NoRootPermissionsException;
import me.arianb.usb_hid_client.shell_utils.ShellCommand;
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
                            // TODO: after coming back to this at a later date, I think I should change this to not use
                            //       root because a permission prompt is (imo) better than unnecessarily using root, because
                            //       my app only really requires root at character device creation time + for fixing their
                            //       permissions.
                            // Get logs
                            // Using root to get logs instead of just $(logcat) because it was prompting
                            // the user asking for permission to view logs and I don't feel that's necessary
                            // and we already have root permissions so why not
                            ShellCommand getLogsCommand = ShellCommand.runAsRoot(String.format("logcat -d --pid=$(pidof -s %s)", BuildConfig.APPLICATION_ID));

                            final String stderr = getLogsCommand.stderr();
                            final String LOG_CONTENT = getLogsCommand.stdout();
                            if (!stderr.isEmpty()) {
                                String errorMessage = "Error occurred while getting logs";
                                Timber.e(errorMessage + ": " + stderr);
                                Snackbar.make(requireView(), errorMessage, Snackbar.LENGTH_LONG).show();
                                return;
                            }

                            // Write out file
                            OutputStream output = SettingsFragment.this.requireContext().getContentResolver().openOutputStream(uri);
                            output.write(LOG_CONTENT.getBytes());
                            output.flush();
                            output.close();
                            Timber.d("Successfully exported logs");
                        } catch (IOException | InterruptedException e) {
                            Timber.e("Error occurred while exporting logs");
                            Timber.e(Log.getStackTraceString(e));
                        } catch (NoRootPermissionsException e) {
                            Timber.e("Failed to export logs, missing root permissions");
                            Snackbar.make(requireView(), "ERROR: Missing root permissions.", Snackbar.LENGTH_INDEFINITE).show();
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
}