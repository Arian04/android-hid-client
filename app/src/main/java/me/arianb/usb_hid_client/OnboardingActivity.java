package me.arianb.usb_hid_client;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;

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
		Button btnOnboardingContinue = findViewById(R.id.btnOnboardingContinue);

		btnOnboardingCreateCharDevice.setOnClickListener(view -> {
			CharacterDevice characterDevice = new CharacterDevice(getApplicationContext());
			characterDevice.createCharacterDevice();
		});

		switchOnboardingCreateDeviceOnBoot.setOnClickListener(view -> {
			SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
			SharedPreferences.Editor preferencesEditor = preferences.edit();
			preferencesEditor.putBoolean("create_character_device_on_boot", switchOnboardingCreateDeviceOnBoot.isChecked());
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