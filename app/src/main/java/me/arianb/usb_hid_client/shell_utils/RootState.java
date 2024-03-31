package me.arianb.usb_hid_client.shell_utils;

import android.util.Log;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import timber.log.Timber;

public abstract class RootState {
    private static RootMethod rootMethod;
    private static String sepolicyCommand;
    public static final String SU_BINARY = "su";

    private static final Map<RootMethod, String> sepolicyMap;
    private static final Map<String, RootMethod> rootBinaryMap;

    public enum RootMethod {
        UNKNOWN,
        UNROOTED,
        MAGISK,
        KERNELSU
    }

    static {
        sepolicyMap = new HashMap<>();
        sepolicyMap.put(RootMethod.MAGISK, "magiskpolicy --live");
        sepolicyMap.put(RootMethod.KERNELSU, "ksud sepolicy patch");

        rootBinaryMap = new HashMap<>();
        rootBinaryMap.put("magisk", RootMethod.MAGISK); // TODO: does this exist or did i imagine it?
        rootBinaryMap.put("magiskpolicy", RootMethod.MAGISK);
        rootBinaryMap.put("ksud", RootMethod.KERNELSU);
    }

    public static String getSepolicyCommand() {
        if (sepolicyCommand == null) {
            getRootMethod();
        }

        return sepolicyCommand;
    }

    @NonNull
    public static RootMethod getRootMethod() {
        if (rootMethod == null) {
            rootMethod = detectRootMethod();
            sepolicyCommand = sepolicyMap.get(rootMethod);
            Timber.d("Detected root method as: %s", rootMethod);
        }
        return rootMethod;
    }

    // TODO: the detected root method is cached, BUT if the user leaves the app, but doesn't completely
    //       exit it, they could give it root permissions, but the app wouldn't re-evaluate.
    //       I could either force re-evaluation of these types of things "onResume()" or just
    //       not cache these results.
    // Determine what method of rooting the user is using
    @NonNull
    private static RootMethod detectRootMethod() {
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
                    return RootMethod.UNKNOWN;
                }
                if (shellProcess.exitValue() == 0) {
                    return Objects.requireNonNullElse(rootBinaryMap.get(binary), RootMethod.UNKNOWN);
                }
            } catch (IOException e) {
                Timber.e("Failed to get root method. Device is most likely not rooted or hasn't given the app root permissions");
                Timber.e(Log.getStackTraceString(e));
                return RootMethod.UNROOTED;
            } catch (InterruptedException e) {
                Timber.e("Failed to get root method, shell process was interrupted before it could finish running.");
                Timber.e(Log.getStackTraceString(e));
                return RootMethod.UNKNOWN;
            }
        }

        return RootMethod.UNKNOWN;
    }
}