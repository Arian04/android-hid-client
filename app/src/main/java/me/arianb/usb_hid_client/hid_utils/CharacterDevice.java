package me.arianb.usb_hid_client.hid_utils;

import static me.arianb.usb_hid_client.ProcessStreamHelper.getProcessStdError;
import static me.arianb.usb_hid_client.ProcessStreamHelper.getProcessStdOutput;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

import me.arianb.usb_hid_client.R;
import timber.log.Timber;

public class CharacterDevice {
    // Path to the keyboard character device
    public static final String KEYBOARD_DEVICE_PATH = "/dev/hidg0";

    // TODO: remove the "doesn't exist yet" part of this comment once it's implemented
    // Path to the mouse character device (doesn't exist yet, it's on the roadmap though)
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
        return !new File(CharacterDevice.KEYBOARD_DEVICE_PATH).exists();
    }

    // TODO: add more error handling
    public boolean createCharacterDevice() {
        final String SCRIPT_FILENAME = "create_char_device.sh";
        final String scriptPath = appContext.getFilesDir().getPath() + "/" + SCRIPT_FILENAME;

        // Copying over script every time instead of doing a check for its existence because
        // this way allows me to update the script without having to do an existence check + diff
        try {
            InputStream ins = appContext.getResources().openRawResource(R.raw.create_char_device);
            byte[] buffer = new byte[ins.available()];
            ins.read(buffer);
            ins.close();
            FileOutputStream fos = appContext.openFileOutput(SCRIPT_FILENAME, Context.MODE_PRIVATE);
            fos.write(buffer);
            fos.close();
        } catch (IOException e) {
            Timber.e(Log.getStackTraceString(e));
            return false;
        }
        File file = appContext.getFileStreamPath(SCRIPT_FILENAME);
        file.setExecutable(true);

        try {
            Process createCharDeviceShell = Runtime.getRuntime().exec("su");
            DataOutputStream createCharDeviceOS = new DataOutputStream(createCharDeviceShell.getOutputStream());
            createCharDeviceOS.writeBytes(scriptPath + "\n");
            createCharDeviceOS.flush();
            createCharDeviceOS.writeBytes("exit" + "\n");
            createCharDeviceOS.flush();
            Timber.d("create device script: stdout=%s,stderr=%s", getProcessStdOutput(createCharDeviceShell), getProcessStdError(createCharDeviceShell));
            // TODO: process timeout + check return code
        } catch (IOException e) {
            Timber.e(Log.getStackTraceString(e));
            return false;
        }
        boolean returnVal;
        returnVal = fixCharacterDevicePermissions(KEYBOARD_DEVICE_PATH);
        //returnVal = returnVal && fixCharacterDevicePermissions(MOUSE_DEVICE_PATH);
        return returnVal;
    }

    // TODO: add more error handling
    public boolean fixCharacterDevicePermissions(String device) {
        try {
            // Get selinux context for app
            String context;
            final String appDataDir = appContext.getDataDir().getPath(); // ex: /data/user/0/me.arianb.usb_hid_client
            Process getContextShell = Runtime.getRuntime().exec("su");
            DataOutputStream getContextOS = new DataOutputStream(getContextShell.getOutputStream());
            getContextOS.writeBytes("stat -c %C " + appDataDir + "\n");
            getContextOS.flush();
            getContextOS.writeBytes("exit" + "\n");
            getContextOS.flush();

            // Get the part of the context that I need
            String getContextShellOutput = getProcessStdOutput(getContextShell);
            context = getContextShellOutput;
            //Timber.d("context: %s", context);
            for (int i = 0; i < context.length(); i++) {
                if (context.charAt(i) == ':' && context.substring(i).length() >= 3) { // If current char is : && there's at least 2 more chars
                    if (context.startsWith(":s0", i)) {
                        context = context.substring(i, context.length() - 1); // Trims off last char (newline)
                        break;
                    }
                }
            }
            //Timber.d("context (fixed): %s", context);

            // If it hasn't changed, then the previous piece of code failed to get the substring
            if (context.equals(getContextShellOutput)) {
                Timber.e("Failed to get app's selinux context");
            }
            //Timber.d("process output: stdout=%s,stderr=%s", getContextShellOutput, getProcessStdError(getContextShell));

            Process fixPermsShell = Runtime.getRuntime().exec("su");
            DataOutputStream fixPermsOS = new DataOutputStream(fixPermsShell.getOutputStream());
            fixPermsOS.writeBytes(String.format(Locale.US, "chown %d:%d %s\n", appUID, appUID, device));
            fixPermsOS.flush();
            fixPermsOS.writeBytes(String.format(Locale.US, "chmod 600 %s\n", device));
            fixPermsOS.flush();
            // Need to look into this issue a little more with multiple devices on different android versions
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S) { // <= Android 12
                fixPermsOS.writeBytes("magiskpolicy --live 'allow untrusted_app device chr_file { getattr open write }'" + "\n");
            } else { // >= Android 13
                fixPermsOS.writeBytes("magiskpolicy --live 'allow untrusted_app_30 device chr_file { getattr open write }'" + "\n");
            }
            fixPermsOS.flush();
            fixPermsOS.writeBytes(String.format(Locale.US, "chcon u:object_r:device%s %s\n", context, device));
            fixPermsOS.flush();
            fixPermsOS.writeBytes("exit" + "\n");
            fixPermsOS.flush();
            //Timber.d("process output: stdout=%s,stderr=%s", getProcessStdOutput(fixPermsShell), getProcessStdError(fixPermsShell));
        } catch (IOException e) {
            Timber.e(Log.getStackTraceString(e));
            return false;
        }
        return true;
    }
}
