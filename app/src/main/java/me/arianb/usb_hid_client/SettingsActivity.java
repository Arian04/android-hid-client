package me.arianb.usb_hid_client;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreference;

import timber.log.Timber;

public class SettingsActivity extends AppCompatActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.settings_activity);

		setTitle("Settings");

		if (savedInstanceState == null) {
			getSupportFragmentManager()
					.beginTransaction()
					.replace(R.id.settings, new SettingsFragment())
					.commit();
		}
		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setDisplayHomeAsUpEnabled(true);
		}
	}

	public static class SettingsFragment extends PreferenceFragmentCompat {
		private static TextView tvOutput;
		private static Context mainContext;

		public static void setContext(Context c) {
			mainContext = c;
			tvOutput = ((Activity) mainContext).findViewById(R.id.tvOutput);
		}

		@Override
		public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
			setPreferencesFromResource(R.xml.root_preferences, rootKey);
			tvOutput = ((Activity) mainContext).findViewById(R.id.tvOutput);

			ListPreference loggingLevelList = findPreference("logging_level");
			SeekBarPreference fontSizeSeekBar = findPreference("font_size");
			SwitchPreference clearInputSwitch = findPreference("clear_manual_input");

			loggingLevelList.setOnPreferenceChangeListener((preference, newValue) -> {
				Timber.d("Logging pref changed to: %s", newValue.toString());

				// Logger listens for preference changes and will automatically update its
				// logging level when this preference changes

				return true;
			});

			fontSizeSeekBar.setOnPreferenceChangeListener((preference, newValue) -> {
				int fontSize = (int) newValue;

				((SeekBarPreference) preference).setValue(fontSize);
				tvOutput.setTextSize(fontSize);

				return true;
			});
		}
	}

	public boolean onOptionsItemSelected(MenuItem item) {
		// Make activity back button work
		Intent intent = new Intent(getApplicationContext(), MainActivity.class);
		startActivity(intent);

		return true;
	}
}