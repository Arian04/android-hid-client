package me.arianb.usb_hid_client.shell_utils;

import androidx.annotation.NonNull;

import com.topjohnwu.superuser.Shell;

import java.util.HashMap;
import java.util.Map;

import timber.log.Timber;

public abstract class RootState {
    private static final Map<RootMethod, String> sepolicyMap;
    private static final Map<String, RootMethod> rootBinaryMap;

    private enum RootMethod {
        UNKNOWN,
        UNROOTED,
        MAGISK,
        KERNELSU
    }

    static {
        sepolicyMap = new HashMap<>();
        sepolicyMap.put(RootMethod.UNKNOWN, null);
        sepolicyMap.put(RootMethod.UNROOTED, null);
        sepolicyMap.put(RootMethod.MAGISK, "magiskpolicy --live");
        sepolicyMap.put(RootMethod.KERNELSU, "ksud sepolicy patch");

        rootBinaryMap = new HashMap<>();
        rootBinaryMap.put("magisk", RootMethod.MAGISK); // TODO: does this exist or did i imagine it?
        rootBinaryMap.put("magiskpolicy", RootMethod.MAGISK);
        rootBinaryMap.put("ksud", RootMethod.KERNELSU);
    }

    public static String getSepolicyCommand() {
        RootMethod rootMethod = detectRootMethod();

        return sepolicyMap.get(rootMethod);
    }

    @NonNull
    private static RootMethod detectRootMethod() {
        if (!Shell.getShell().isRoot()) {
            Timber.e("Failed to get root shell. Device is most likely not rooted or hasn't given the app root permissions");
            return RootMethod.UNROOTED;
        }

        for (Map.Entry<String, RootMethod> entry : rootBinaryMap.entrySet()) {
            String binary = entry.getKey();
            RootMethod matchingRootMethod = entry.getValue();
            //Timber.d("checking for binary: %s", binary);

            Shell.Result commandResult = Shell.cmd("type " + binary).exec();
            if (commandResult.getCode() == 0) {
                Timber.d("Detected root method as: %s", matchingRootMethod);
                return matchingRootMethod;
            }
        }

        return RootMethod.UNKNOWN;
    }
}