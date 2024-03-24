package me.arianb.usb_hid_client.shell_utils;

import android.util.Log;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import timber.log.Timber;

public abstract class RootState {
    private static String rootMethod;
    private static String sepolicyCommand;
    public static final String SU_BINARY = "su";

    private static final Map<String, String> sepolicyMap;
    private static final Map<String, String> rootBinaryMap;
    private static final String UNKNOWN = "unknown";
    private static final String UNROOTED = "unrooted";
    private static final String MAGISK = "magisk";
    private static final String KERNELSU = "kernelsu";

    static {
        sepolicyMap = new HashMap<>();
        sepolicyMap.put(MAGISK, "magiskpolicy --live");
        sepolicyMap.put(KERNELSU, "ksud sepolicy patch");

        rootBinaryMap = new HashMap<>();
        rootBinaryMap.put("magisk", MAGISK); // TODO: does this exist or did i imagine it?
        rootBinaryMap.put("magiskpolicy", MAGISK);
        rootBinaryMap.put("ksud", KERNELSU);
    }

    public static String getSepolicyCommand() {
        if (sepolicyCommand == null) {
            getRootMethod();
        } else if (sepolicyCommand.equals(UNKNOWN)) {
            return null;
        }

        return sepolicyCommand;
    }

    public static String getRootMethod() {
        if (rootMethod == null) {
            rootMethod = detectRootMethod();
            sepolicyCommand = sepolicyMap.get(rootMethod);
            Timber.d("Detected root method as: %s", rootMethod);
        } else if (rootMethod.equals(UNKNOWN)) {
            return null;
        }
        return rootMethod;
    }

    // Determine what method of rooting the user is using
    private static String detectRootMethod() {
        Set<String> binaryList = rootBinaryMap.keySet();
        for (String binary : binaryList) {
//            Timber.d("checking for binary: %s", binary);

            String[] command = new String[]{
                    SU_BINARY,
                    "-c",
                    "type " + binary,
            };
            ProcessBuilder processBuilder = new ProcessBuilder(command);

            try {
                Process shellProcess = processBuilder.start();
                if (!shellProcess.waitFor(1, TimeUnit.SECONDS)) {
                    Timber.e("Shell timed out while attempting to get root method.");
                    return UNKNOWN;
                }
                if (shellProcess.exitValue() == 0) {
                    return rootBinaryMap.get(binary);
                }
            } catch (IOException e) {
                Timber.e("Failed to get root method. Device is most likely not rooted.");
                Timber.e(Log.getStackTraceString(e));
                return UNROOTED;
            } catch (InterruptedException e) {
                Timber.e("Failed to get root method, shell process was interrupted before it could finish running.");
                Timber.e(Log.getStackTraceString(e));
                return UNKNOWN;
            }
        }

        return UNKNOWN;
    }
}