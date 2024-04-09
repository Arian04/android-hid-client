package me.arianb.usb_hid_client.hid_utils;

import android.content.Context;
import android.content.res.Resources;

import androidx.annotation.NonNull;

import com.topjohnwu.superuser.Shell;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import me.arianb.usb_hid_client.R;
import me.arianb.usb_hid_client.shell_utils.NoRootPermissionsException;
import me.arianb.usb_hid_client.shell_utils.RootState;
import timber.log.Timber;

public class CharacterDevice {
    // character device paths
    public static final String KEYBOARD_DEVICE_PATH = "/dev/hidg0";
    public static final String MOUSE_DEVICE_PATH = "/dev/hidg1";
    public static final List<String> ALL_CHARACTER_DEVICE_PATHS = Arrays.asList(KEYBOARD_DEVICE_PATH, MOUSE_DEVICE_PATH);

    private final Resources appResources;
    private final int appUID;
    private final String appDataDirPath;

    public CharacterDevice(Context context) {
        appResources = context.getResources();
        appUID = context.getApplicationInfo().uid;
        appDataDirPath = context.getApplicationInfo().dataDir;
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
        if (!Shell.getShell().isRoot()) {
            throw new NoRootPermissionsException();
        }

        Shell.Result createCharDevices = Shell.cmd(appResources.openRawResource(R.raw.create_char_devices)).exec();
        Timber.d("create device script: \nstdout=%s\nstderr=%s", createCharDevices.getOut(), createCharDevices.getErr());

        fixSelinuxPermissions();

        // FIXME: wait until the respective devices exist before running these two lines
        fixCharacterDevicePermissions(KEYBOARD_DEVICE_PATH);
        fixCharacterDevicePermissions(MOUSE_DEVICE_PATH);

        return true;
    }

    private void fixSelinuxPermissions() {
        final String SELINUX_DOMAIN = "appdomain";
        final String SELINUX_POLICY = String.format("allow %s device chr_file { getattr open write }", SELINUX_DOMAIN);
        final String SELINUX_POLICY_COMMAND = String.format("%s '%s'", RootState.getSepolicyCommand(), SELINUX_POLICY);

        Shell.cmd(SELINUX_POLICY_COMMAND).exec();
    }

    public void fixCharacterDevicePermissions(String device) throws NoRootPermissionsException {
        if (!Shell.getShell().isRoot()) {
            throw new NoRootPermissionsException();
        }

        // Set Linux permissions -> only my app user can r/w to the char device
        final String CHOWN_COMMAND = String.format(Locale.US, "chown %d:%d %s", appUID, appUID, device);
        final String CHMOD_COMMAND = String.format(Locale.US, "chmod 600 %s", device);
        Shell.cmd(CHOWN_COMMAND).exec();
        Shell.cmd(CHMOD_COMMAND).exec();

        // Set SELinux permissions -> only my app's selinux context can r/w to the char device
        final String SELINUX_CATEGORIES = getSelinuxCategories();
        final String CHCON_COMMAND = String.format(Locale.US, "chcon u:object_r:device:s0:%s %s", SELINUX_CATEGORIES, device);
        Shell.cmd(CHCON_COMMAND).exec();
    }

    @NonNull
    private String getSelinuxCategories() {
        // Get selinux context for app
        Shell.Result commandResult = Shell.cmd("stat -c %C " + appDataDirPath).exec();

        // Get the part of the context that I need (categories) by grabbing everything after the last ':'
        String contextFromCommand = String.join("\n", commandResult.getOut());
        String categories = contextFromCommand;

        // TODO: handle the case that stdout doesn't include any ':' (idk why that would even happen tho)
        categories = categories.substring(categories.lastIndexOf(':') + 1);
        categories = categories.trim(); // trim whitespace
        Timber.d("context (before,after): (%s,%s)", contextFromCommand, categories);

        // If it hasn't changed, then the previous piece of code failed to get the substring
        if (categories.equals(contextFromCommand)) {
            Timber.e("Failed to get app's selinux context");
        }
        return categories;
    }
}