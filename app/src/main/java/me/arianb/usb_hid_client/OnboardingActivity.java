package me.arianb.usb_hid_client;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

public class OnboardingActivity extends AppCompatActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_onboarding);

		setTitle("Initial Setup");

		// TODO: Remember to call finish() on "finish" button click
	}

	// This is here so the user can't click the back button to get to the MainActivity and bypass this
	// setup screen
	@Override
	public void onBackPressed() {
		moveTaskToBack(true);
	}
}