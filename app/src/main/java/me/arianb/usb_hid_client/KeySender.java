package me.arianb.usb_hid_client;

import static me.arianb.usb_hid_client.ProcessStreamHelper.getProcessStdError;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import org.apache.commons.codec.binary.Hex;

import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import timber.log.Timber;

public class KeySender implements Runnable {
	private static Queue<String> keyQueue;
	private static Queue<String> modQueue;

	private static DataOutputStream rootShell;

	private static SharedPreferences preferences;

	private static final ReentrantLock queueLock = new ReentrantLock(true);
	private static final Condition queueNotEmptyCondition = queueLock.newCondition();

	public KeySender(Context context) {
		keyQueue = new LinkedList<>();
		modQueue = new LinkedList<>();
		preferences = PreferenceManager.getDefaultSharedPreferences(context);
		// Get root shell
		try {
			Process p = Runtime.getRuntime().exec("su");
			rootShell = new DataOutputStream(p.getOutputStream());
		} catch (IOException e) {
			Timber.e(Log.getStackTraceString(e));
		}
	}

	@Override
	public void run() {
		Timber.d("keySender thread started");
		while (!Thread.interrupted()) {
			queueLock.lock();
			// Wait for the queue/s to actually contain keys
			if (keyQueue.isEmpty()) {
				Timber.d("Waiting for queue to not be empty.");
				try {
					queueNotEmptyCondition.await();
				} catch (InterruptedException e) {
					Timber.e(Log.getStackTraceString(e));
					queueLock.unlock();
				}
			}
			sendKey(modQueue.remove(), keyQueue.remove());
			Timber.d("sending key");
			queueLock.unlock();
		}
	}

	public void addKey(String modifier, String key) {
		Timber.d("trying to lock");
		queueLock.lock();
		modQueue.add(modifier);
		keyQueue.add(key);
		queueNotEmptyCondition.signal();
		queueLock.unlock();
		Timber.d("unlocked");
	}

	// TODO: add support for sending multiple modifier keys at once
	//		 - i think you add up modifier scan codes to send multiple? so find method to add hex
	// 		 - use an array to hold mod keys and add to them instead of overwriting?
	public void sendKey(String modifier, String key) {
		try {
			//Log.i(TAG, "raw key: " + key + " | sending key: " + adjustedKey + " | modifier: " + sendModifier);

			// TODO: give app user permissions to write to /dev/hidg0 because opening a shell
			//  	 causes a very significant performance hit
			// 		 - once i complete this, remove performance mode, because it'll be fast by default

			/*
			int SELinuxMode = 0;

			// TODO: checking if SELinux is disabled every single time a key is pressed is inefficient.
			// 		 If this solution sticks for a while, I should add a setting for it or check on startup
			Process checkSELinuxModeProcess = Runtime.getRuntime().exec("cat /sys/fs/selinux/enforce");
			// If this throws an error, SELinux is enabled, otherwise it'll set selinuxMode to 1.
			try {
				checkSELinuxModeProcess.waitFor();
				SELinuxMode = Integer.parseInt(getProcessStdOutput(checkSELinuxModeProcess).trim());
			} catch (NumberFormatException e) {
				SELinuxMode = 1;
			}
			*/

			byte modifierScanCode = KeyCodeTranslation.convertModifierToScanCode(modifier);
			byte keyScanCode = KeyCodeTranslation.convertKeyToScanCode(key);

			// If key is shift + another key, add left-shift scan code
			if (KeyCodeTranslation.isShiftedKey(key)) {
				modifierScanCode = 0x02;
			}

			if (false) { // Only works if selinux is disabled
				Timber.d("SELinux is disabled. Directly writing to device.");

				// Send key
				writeHIDReport("/dev/hidg0", modifierScanCode, keyScanCode);

				// TODO: kill thread if it is alive longer than the specified timeout
				// Release key
				writeHIDReport("/dev/hidg0", (byte) 0, (byte) 0);
			} else if (preferences.getBoolean("performance_mode", false)) {
				Timber.d("\"Performance mode\" code active.");

				// Convert byte to hexadecimal String and prepend x (Ex: (byte) 0x15 -> (String) x15)
				String sendModifier = "x" + Hex.encodeHexString(new byte[]{modifierScanCode});
				String sendKey = "x" + Hex.encodeHexString(new byte[]{keyScanCode});

				// echo -en "\modifier\0\key\0\0\0\0\0" > /dev/hidg0 (as root) (presses key)
				String sendKeyCmd = "echo -en \"\\" + sendModifier + "\\0\\" + sendKey + "\\0\\0\\0\\0\\0\" > /dev/hidg0";
				Timber.d(sendKey);

				String releaseKeyCmd = "echo -en \"\\0\\0\\0\\0\\0\\0\\0\\0\" > /dev/hidg0";
				// Send key
				rootShell.writeBytes(sendKeyCmd + "\n");
				rootShell.flush();
				// Release key
				rootShell.writeBytes(releaseKeyCmd + "\n");
				rootShell.flush();
			} else {
				Timber.d("\"Performance mode\" DISABLED");

				// Convert byte to hexadecimal String and prepend x (Ex: (byte) 0x15 -> (String) x15)
				String sendModifier = "x" + Hex.encodeHexString(new byte[]{modifierScanCode});
				String sendKey = "x" + Hex.encodeHexString(new byte[]{keyScanCode});

				// echo -en "\modifier\0\key\0\0\0\0\0" > /dev/hidg0 (as root) (presses key)
				String[] sendKeyCmd = {"su", "-c", "echo", "-en", "\"\\" + sendModifier + "\\0\\" + sendKey + "\\0\\0\\0\\0\\0\" > /dev/hidg0"};
				// echo -en "\0\0\0\0\0\0\0\0" > /dev/hidg0 (as root) (releases key)
				String[] releaseKeyCmd = {"su", "-c", "echo", "-en", "\"\\0\\0\\0\\0\\0\\0\\0\\0\" > /dev/hidg0"};

				// Send key
				Process sendProcess = Runtime.getRuntime().exec(sendKeyCmd);
				// Kill process if it doesn't complete within 1 seconds
				if (!sendProcess.waitFor(300, TimeUnit.MILLISECONDS)) {
					Timber.e("Timed out while sending key. Make sure a computer is connected.");
					sendProcess.destroy();
					return;
				}
				// Release key
				Process releaseProcess = Runtime.getRuntime().exec(releaseKeyCmd);

				// Log errors if the processes returned any
				String sendErrors = getProcessStdError(sendProcess);
				String releaseErrors = getProcessStdError(releaseProcess);
				if (!sendErrors.isEmpty()) {
					Timber.e(sendErrors);
				}
				if (!releaseErrors.isEmpty()) {
					Timber.e(releaseErrors);
				}
			}
		} catch (IOException | InterruptedException e) {
			Timber.e(Log.getStackTraceString(e));
		}
	}

	private void writeHIDReport(String device, byte modifier, byte key) {
		byte[] report = new byte[]{modifier, (byte) 0, key, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0};
		try (FileOutputStream outputStream = new FileOutputStream(device)) {
			outputStream.write(report);
		} catch (IOException e) {
			Timber.e(Log.getStackTraceString(e));
		}
	}
}
