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

public class SettingsActivity extends AppCompatActivity {

	private static final String TAG = MainActivity.TAG;

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

		private static final String TAG = MainActivity.TAG;
		private TextView tvOutput;
		private Context mContext;

		public SettingsFragment() {
		}

		public SettingsFragment(Context context) {
			this.mContext = context;
			tvOutput = ((Activity) mContext).findViewById(R.id.tvOutput);
		}

		@Override
		public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
			setPreferencesFromResource(R.xml.root_preferences, rootKey);

			ListPreference loggingLevelList = findPreference("logging_level");
			SeekBarPreference fontSizeSeekBar = findPreference("font_size");
			SwitchPreference clearInputSwitch = findPreference("clear_manual_input");

			loggingLevelList.setOnPreferenceChangeListener((preference, newValue) -> {
				// displayLogs(newValue);
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