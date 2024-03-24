package me.arianb.usb_hid_client.hid_utils;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

import me.arianb.usb_hid_client.R;
import me.arianb.usb_hid_client.shell_utils.RootShell;
import me.arianb.usb_hid_client.shell_utils.ShellCommand;
import timber.log.Timber;

public class CharacterDevice {
    // character device paths
    public static final String KEYBOARD_DEVICE_PATH = "/dev/hidg0";
    public static final String MOUSE_DEVICE_PATH = "/dev/hidg1";

    private final Context appContext;
    private final int appUID;

    public CharacterDevice(Context context) {
        this.appContext = context;
        appUID = context.getApplicationInfo().uid;
    }

    public static boolean characterDeviceMissing(String charDevicePath) {
        if (!(charDevicePath.equals(KEYBOARD_DEVICE_PATH) || charDevicePath.equals(MOUSE_DEVICE_PATH))) {
            return true;
        }
        return !new File(charDevicePath).exists();
    }

    // TODO: add more error handling
    public boolean createCharacterDevice() {
        final String SCRIPT_FILENAME = "create_char_device.sh";
        final String SCRIPT_PATH = appContext.getFilesDir().getPath() + "/" + SCRIPT_FILENAME;

        // TODO: change this to copy over in chunks. Doesn't really matter right now since I'm copying
        //       a tiny text file, but I believe this causes the entire file to be read into memory
        //       before being written, rather than reading it in chunks. If I was copying a large
        //       file (And I'm right about this) then it could potentially use lots of memory or cause
        //       the device to run out if the file size > free memory.
        // Copying over script every time instead of doing a check for its existence because
        // this way allows me to update the script without having to do an existence check + diff
        try {
            InputStream in = appContext.getResources().openRawResource(R.raw.create_char_device);
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
            ShellCommand createCharDevice = ShellCommand.runAsRoot(SCRIPT_PATH);
            Timber.d("create device script: stdout=%s,stderr=%s", createCharDevice.stdout(), createCharDevice.stderr());
        } catch (IOException | InterruptedException e) {
            Timber.e("Failed to run shell script that creates the character device.");
            Timber.e(Log.getStackTraceString(e));
            return false;
        }

        boolean returnVal;
        returnVal = fixCharacterDevicePermissions(KEYBOARD_DEVICE_PATH);
        returnVal = fixCharacterDevicePermissions(MOUSE_DEVICE_PATH) && returnVal;
        return returnVal;
    }

    public boolean fixCharacterDevicePermissions(String device) {
        try {
            RootShell fixPermissionsShell = new RootShell();

            // Set Linux permissions -> only my app user can r/w to the char device
            fixPermissionsShell.run(String.format(Locale.US, "chown %d:%d %s", appUID, appUID, device));
            fixPermissionsShell.run(String.format(Locale.US, "chmod 600 %s", device));

            // Set SELinux permissions -> only my app's selinux context can r/w to the char device
            final String SELINUX_CATEGORIES = getSelinuxCategories();
            final String SELINUX_DOMAIN = "appdomain";
            final String selinuxPolicy = String.format("allow %s device chr_file { getattr open write }", SELINUX_DOMAIN);
            fixPermissionsShell.addSelinuxPolicy(selinuxPolicy);
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