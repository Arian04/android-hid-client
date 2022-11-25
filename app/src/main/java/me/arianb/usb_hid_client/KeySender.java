package me.arianb.usb_hid_client;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.google.android.material.snackbar.Snackbar;

import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import timber.log.Timber;

public class KeySender implements Runnable {
	private static Queue<Byte> keyQueue;
	private static Queue<Byte> modQueue;


	private static final ReentrantLock queueLock = new ReentrantLock(true);
	private static final Condition queueNotEmptyCondition = queueLock.newCondition();

	public KeySender(Context context) {
		keyQueue = new LinkedList<>();
		modQueue = new LinkedList<>();
	}

	@Override
	public void run() {
		Timber.d("keySender thread started");
		while (!Thread.interrupted()) {
			queueLock.lock();
			// Wait for the queue(s) to actually contain keys
			if (keyQueue.isEmpty()) {
				//Timber.d("Waiting for queue to not be empty.");
				try {
					queueNotEmptyCondition.await();
				} catch (InterruptedException e) {
					Timber.e(Log.getStackTraceString(e));
					queueLock.unlock();
				}
			}
			sendKey(modQueue.remove(), keyQueue.remove());
			//Timber.d("sending key");
			queueLock.unlock();
		}
	}

	public void addKey(byte modifier, byte key) {
		//Timber.d("trying to lock");
		queueLock.lock();
		modQueue.add(modifier);
		keyQueue.add(key);
		queueNotEmptyCondition.signal();
		queueLock.unlock();
		//Timber.d("unlocked");
	}

	public void sendKey(byte modifier, byte key) {
		//Log.i(TAG, "raw key: " + key + " | sending key: " + adjustedKey + " | modifier: " + sendModifier);

		Timber.d("SELinux code is running. Directly writing to character device.");

		// Send key
		writeHIDReport("/dev/hidg0", modifier, key);

		// TODO: kill thread if it is alive longer than the specified timeout
		// 		 - I no longer remember what i was thinking when I wrote this comment
		// Release key
		writeHIDReport("/dev/hidg0", (byte) 0, (byte) 0);
	}

	private void writeHIDReport(String device, byte modifier, byte key) {
		Timber.d("hid report: %s - %s", modifier, key);
		byte[] report = new byte[]{modifier, (byte) 0, key, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0};
		try (FileOutputStream outputStream = new FileOutputStream(device)) {
			outputStream.write(report);
		} catch (IOException e) {
			MainActivity.makeSnackbar("Error: Failed to send key.", Snackbar.LENGTH_SHORT);
			Timber.e(Log.getStackTraceString(e));
		}
	}
}