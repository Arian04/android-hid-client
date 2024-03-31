package me.arianb.usb_hid_client.hid_utils;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeoutException;

import me.arianb.usb_hid_client.R;
import me.arianb.usb_hid_client.shell_utils.NoRootPermissionsException;
import me.arianb.usb_hid_client.shell_utils.RootShell;
import me.arianb.usb_hid_client.shell_utils.ShellCommand;
import timber.log.Timber;

public class CharacterDevice {
    // character device paths
    public static final String KEYBOARD_DEVICE_PATH = "/dev/hidg0";
    public static final String MOUSE_DEVICE_PATH = "/dev/hidg1";
    public static final List<String> ALL_CHARACTER_DEVICE_PATHS = Arrays.asList(KEYBOARD_DEVICE_PATH, MOUSE_DEVICE_PATH);

    private static final String SCRIPT_FILENAME = "create_char_devices.sh";

    private final Context appContext;
    private final int appUID;

    public CharacterDevice(Context context) {
        appContext = context;
        appUID = context.getApplicationInfo().uid;
    }

    public static boolean characterDeviceMissing(String charDevicePath) {
        if (!(ALL_CHARACTER_DEVICE_PATHS.contains(charDevicePath))) {
            return true;
        }
        return !new File(charDevicePath).exists();
    }

    public static boolean anyCharacterDeviceMissing() {
        for (String charDevicePath : ALL_CHARACTER_DEVICE_PATHS) {
            if (!new File(charDevicePath).exists()) {
                return true;
            }
        }
        return false;
    }

    public boolean createCharacterDevice() throws NoRootPermissionsException {
        final String SCRIPT_PATH = appContext.getFilesDir().getPath() + "/" + SCRIPT_FILENAME;

        // TODO: change this to copy over in chunks. Doesn't really matter right now since I'm copying
        //       a tiny text file, but I believe this causes the entire file to be read into memory
        //       before being written, rather than reading it in chunks. If I was copying a large
        //       file (And I'm right about this) then it could potentially use lots of memory or cause
        //       the device to run out if the file size > free memory.
        // Copying over script every time instead of doing a check for its existence because
        // this way allows me to update the script without having to do an existence check + diff
        try {
            InputStream in = appContext.getResources().openRawResource(R.raw.create_char_devices);
            byte[] buffer = new byte[in.available()];
            in.read(buffer);
            in.close();
            FileOutputStream out = appContext.openFileOutput(SCRIPT_FILENAME, Context.MODE_PRIVATE);
            out.write(buffer);
            out.close();
        } catch (IOException e) {
            Timber.e("Failed to copy shell script that creates the character device.");
            Timber.e(Log.getStackTraceString(e));
            return false;
        }
        File scriptFile = appContext.getFileStreamPath(SCRIPT_FILENAME);
        scriptFile.setExecutable(true);

        try {
            // Run script to create character devices
            ShellCommand createCharDevice = ShellCommand.runAsRoot(SCRIPT_PATH);
            Timber.d("create device script: stdout=%s,stderr=%s", createCharDevice.stdout(), createCharDevice.stderr());
        } catch (IOException | InterruptedException e) {
            Timber.e("Failed to run shell script that creates the character device.");
            Timber.e(Log.getStackTraceString(e));
            return false;
        }

        // TODO: improve this code
        //  - switch to WatchService
        //  - make it not run on the main thread
        try {
            // Verify that character devices exist, exiting if they don't exist after TIMEOUT milliseconds.
            // during my testing it took around 500ms for the devices to show up
            boolean keyboardDeviceCreated = false;
            boolean mouseDeviceCreated = false;
            final long TIMEOUT = 1000;
            final long startTimeMillis = System.currentTimeMillis();
            while (!(keyboardDeviceCreated && mouseDeviceCreated)) {
                if (!keyboardDeviceCreated) {
                    if (new File(KEYBOARD_DEVICE_PATH).exists()) {
                        keyboardDeviceCreated = true;
                    } else {
                        Timber.d("keyboard device missing!!");
                    }
                }
                if (!mouseDeviceCreated) {
                    if (new File(MOUSE_DEVICE_PATH).exists()) {
                        mouseDeviceCreated = true;
                    } else {
                        Timber.d("mouse device missing!!");
                    }
                }

                final long timeElapsedMillis = System.currentTimeMillis() - startTimeMillis;
                if (timeElapsedMillis > TIMEOUT) {
                    throw new TimeoutException();
                }
            }
        } catch (TimeoutException e) {
            Timber.e("Shell script ran, but character devices were not created.");
            Timber.e(Log.getStackTraceString(e));
            return false;
        }

        fixCharacterDevicePermissions(KEYBOARD_DEVICE_PATH);
        fixCharacterDevicePermissions(MOUSE_DEVICE_PATH);
        return true;
    }

    public boolean fixCharacterDevicePermissions(String device) throws NoRootPermissionsException {
        try {
            RootShell fixPermissionsShell = new RootShell();

            // Set Linux permissions -> only my app user can r/w to the char device
            fixPermissionsShell.run(String.format(Locale.US, "chown %d:%d %s", appUID, appUID, device));
            fixPermissionsShell.run(String.format(Locale.US, "chmod 600 %s", device));

            // Set SELinux permissions -> only my app's selinux context can r/w to the char device
            final String SELINUX_CATEGORIES = getSelinuxCategories();
            final String SELINUX_DOMAIN = "appdomain";
            final String SELINUX_POLICY = String.format("allow %s device chr_file { getattr open write }", SELINUX_DOMAIN);
            fixPermissionsShell.addSelinuxPolicy(SELINUX_POLICY);
            fixPermissionsShell.run(String.format(Locale.US, "chcon u:object_r:device:s0:%s %s", SELINUX_CATEGORIES, device));

            fixPermissionsShell.close();
        } catch (IOException | InterruptedException e) {
            Timber.e("Failed to fix character device permissions");
            Timber.e(Log.getStackTraceString(e));
            return false;
        }
        return true;
    }

    @NonNull
    private String getSelinuxCategories() throws IOException, InterruptedException {
        // Get selinux context for app
        ShellCommand getContextCommand = ShellCommand.run(new String[]{"id", "-Z"}); // ex: u:r:runas_app:s0:c107,c257,c512,c768

        // Get the part of the context that I need (categories) by grabbing everything after the last ':'
        String getContextShellOutput = getContextCommand.stdout();
        String categories = getContextShellOutput;

        // TODO: handle the case that stdout doesn't include any ':' (idk why that would even happen tho)
        categories = categories.substring(categories.lastIndexOf(':') + 1);
        categories = categories.trim(); // trim whitespace
        Timber.d("context (before,after): (%s,%s)", getContextShellOutput, categories);

        // If it hasn't changed, then the previous piece of code failed to get the substring
        if (categories.equals(getContextShellOutput)) {
            Timber.e("Failed to get app's selinux context");
        }
        return categories;
    }
}