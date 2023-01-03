package me.arianb.usb_hid_client;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.RadioGroup;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.google.android.material.switchmaterial.SwitchMaterial;

public class OnboardingActivity extends AppCompatActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_onboarding);

		setTitle(R.string.onboarding_title);

		Button btnOnboardingCreateCharDevice = findViewById(R.id.btnOnboardingCreateCharDevice);
		SwitchMaterial switchOnboardingCreateDeviceOnBoot = findViewById(R.id.switchOnboardingCreateDeviceOnBoot);
		RadioGroup radioGroupErrorPromptAction = findViewById(R.id.radioGroupErrorPromptAction);
		Button btnOnboardingContinue = findViewById(R.id.btnOnboardingContinue);

		btnOnboardingCreateCharDevice.setOnClickListener(view -> MainActivity.characterDevice.createCharacterDevice());

		switchOnboardingCreateDeviceOnBoot.setOnClickListener(view -> {
			SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
			SharedPreferences.Editor preferencesEditor = preferences.edit();
			preferencesEditor.putBoolean("create_character_device_on_boot", switchOnboardingCreateDeviceOnBoot.isChecked());
			preferencesEditor.apply();
		});

		radioGroupErrorPromptAction.setOnCheckedChangeListener((radioGroup, i) -> {
			SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
			SharedPreferences.Editor preferencesEditor = preferences.edit();
			if (i == R.id.radioErrorPromptAction_askEveryTime) {
				preferencesEditor.putString("issue_prompt_action", getString(R.string.error_action_ask_every_time));
			} else if (i == R.id.radioErrorPromptAction_fix) {
				preferencesEditor.putString("issue_prompt_action", getString(R.string.error_action_fix));
			} else if (i == R.id.radioErrorPromptAction_ignore) {
				preferencesEditor.putString("issue_prompt_action", getString(R.string.error_action_ignore));
			} else {
				// i == -1
				// All radio buttons are unchecked, so do nothing
			}
			preferencesEditor.apply();
		});

		btnOnboardingContinue.setOnClickListener(view -> {
			SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
			SharedPreferences.Editor preferencesEditor = preferences.edit();
			preferencesEditor.putBoolean("onboarding_done", true);
			preferencesEditor.apply();
			finish();
		});
	}

	// This is here so the user can't click the back button to get to the MainActivity and bypass this
	// setup screen
	@Override
	public void onBackPressed() {
		moveTaskToBack(true);
	}
}