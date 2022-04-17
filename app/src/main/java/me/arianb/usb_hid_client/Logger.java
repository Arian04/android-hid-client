package me.arianb.usb_hid_client;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;

import timber.log.Timber;

public class Logger {
	private final Context mainContext;
	private final TextView textView;

	private static Thread loggingThread;
	private static Thread watchingLoggingThread;
	private static String currentLoggingLevel;

	public Logger(Context context, TextView textView) {
		this.mainContext = context;
		this.textView = textView;
	}

	private void displayLogs(String verbosityFilter) {
		Timber.d("logging choice: %s", verbosityFilter);

		// Trim filter down to just the first letter and make it uppercase, because logcat uses the
		// capitalized first letter of the logging level to filter
		String verbosityLetter = verbosityFilter.substring(0, 1).toUpperCase();

		loggingThread = new Thread(() -> {
			try {
				String command = String.format("logcat -s me.arianb.usb_hid_client:%s -v raw", verbosityLetter);
				Process process = Runtime.getRuntime().exec(command);
				BufferedReader bufferedReader = new BufferedReader(
						new InputStreamReader(process.getInputStream()));

				// Discard the first line of logcat ("---beginning of main---")
				bufferedReader.readLine();

				StringBuilder log = new StringBuilder();
				String line;
				while (!Thread.interrupted()) {
					line = bufferedReader.readLine();
					if (!Thread.interrupted()) {
						// Don't log null lines or lines starting with [ignore]
						if (line != null && !line.matches("\\[ignore\\].*")) {
							log.insert(0, line + "\n");
							((Activity) mainContext).runOnUiThread(() -> textView.setText(log.toString()));
						}
					} else {
						Timber.d("Logging Thread interrupted. Logging Level: %s", verbosityFilter);
						break;
					}
				}
				// Kill logcat process before ending thread
				process.destroy();
			} catch (IOException e) {
				Timber.e(Log.getStackTraceString(e));
			} finally {
				// Clear previous logs (reset it back to the default output)
				((Activity) mainContext).runOnUiThread(() -> textView.setText(R.string.default_output));
			}
			Timber.d("Thread actually ended: %s", verbosityLetter); // DEBUG
		});
		loggingThread.start();
		Timber.d("logging started with verbosity: %s", verbosityFilter);
	}

	public void watchForPreferenceChanges(SharedPreferences preferences) {
		watchingLoggingThread = new Thread(() -> {
			// Start logging based on preference
			String loggingLevelPref = preferences.getString("logging_level", "error");
			currentLoggingLevel = loggingLevelPref;
			displayLogs(loggingLevelPref);

			// While this thread isn't interrupted, constantly check if the logging thread
			// needs to be restarted with a new logging level
			while (!Thread.interrupted()) {
				loggingLevelPref = preferences.getString("logging_level", "error");
				if (!loggingLevelPref.equals(currentLoggingLevel)) {
					// Stop old thread that was logging at the previous logging level
					stopLoggingThread(loggingThread);

					// Start new thread logging at the updated logging level
					currentLoggingLevel = loggingLevelPref;
					displayLogs(loggingLevelPref);
				}
			}
			// Once this thread is interrupted, stop the logging thread
			stopLoggingThread(loggingThread);
		});
		watchingLoggingThread.start();
	}

	private void stopLoggingThread(Thread t) {
		if (t != null && !t.isInterrupted()) {
			// Pretty sure if thread updates tvOutput one more time before finishing being interrupted
			// it might overwrite the new thread's output until it re-rewrites the output.
			// Implement locks if this is an issue.
			t.interrupt();

			// IDK how I feel about this workaround
			Timber.e("[ignore] NOT AN ERROR. This is being logged to trigger the logging thread to check if it's been interrupted.");
			try {
				if (t.isAlive()) {
					t.join();
				}
			} catch (InterruptedException e) {
				Timber.e(Arrays.toString(e.getStackTrace()));
			}
		}
	}

	public static Thread getWatchingLoggingThread() {
		return watchingLoggingThread;
	}
}
