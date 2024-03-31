package me.arianb.usb_hid_client.shell_utils;

import android.util.Log;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

import timber.log.Timber;

public class RootShell {
    private final Process shellProcess;

    // NOTE: stdout and stdin names are weirdly the opposite of what you'd expect
    private final DataOutputStream shellOutputStream; // stdin
    private final DataInputStream shellInputStream; // stdout
    private final DataInputStream shellErrorStream; // stderr

    public RootShell() throws IOException, NoRootPermissionsException {
        if (RootState.getRootMethod() == RootState.RootMethod.UNROOTED) {
            throw new NoRootPermissionsException();
        }

        ProcessBuilder processBuilder = new ProcessBuilder(RootState.SU_BINARY);

        shellProcess = processBuilder.start();

        shellOutputStream = new DataOutputStream(shellProcess.getOutputStream());
        shellInputStream = new DataInputStream(shellProcess.getInputStream());
        shellErrorStream = new DataInputStream(shellProcess.getErrorStream());
    }

    public int run(String command) throws IOException {
        Timber.d("running (with root) command: %s", command);

        String shellCommand = command + "\n";
        shellOutputStream.writeBytes(shellCommand);
        shellOutputStream.flush();
        return 0;
    }

    public void close() throws IOException {
        final String EXIT_COMMAND = "exit";
        final long DEFAULT_TIMEOUT = 1;
        final TimeUnit DEFAULT_TIMEOUT_UNIT = TimeUnit.SECONDS;

        run(EXIT_COMMAND);
        try {
            if (!shellProcess.waitFor(DEFAULT_TIMEOUT, DEFAULT_TIMEOUT_UNIT)) {
                Timber.e("Command timed out after: %s %s", DEFAULT_TIMEOUT, DEFAULT_TIMEOUT_UNIT);
                shellProcess.destroyForcibly();
                if (!shellProcess.waitFor(DEFAULT_TIMEOUT, DEFAULT_TIMEOUT_UNIT)) {
                    Timber.e("Forceful destruction of command process timed out after: %s %s", DEFAULT_TIMEOUT, DEFAULT_TIMEOUT_UNIT);
                    throw new IOException("Forceful destruction of command process timed out");
                }
            }
        } catch (InterruptedException e) {
            Timber.e("Failed to close shell");
            Timber.e(Log.getStackTraceString(e));
        }
    }

    public int addSelinuxPolicy(String policy) throws IOException {
        String sepolicyCommand = RootState.getSepolicyCommand();

        if (sepolicyCommand == null) {
            Timber.e("Command for changing SELinux policy using this root method is unknown");
            return 1;
        }

        // NOTE: the policy being quoted matters (at least for ksud)
        String command = String.format("%s '%s'", sepolicyCommand, policy);

        return run(command);
    }

    public String getStdOutput() throws IOException {
        return getShellStdStream(shellInputStream);
    }

    public String getStdError() throws IOException {
        return getShellStdStream(shellErrorStream);
    }

    private String getShellStdStream(DataInputStream stream) throws IOException {
        BufferedReader streamReader = new BufferedReader(new InputStreamReader(stream));

        // Turn the stream's contents into a string
        //noinspection UnusedAssignment: I think explicitly setting = null makes the code clearer here
        String s = null;
        StringBuilder returnStr = new StringBuilder();
        while ((s = streamReader.readLine()) != null) {
            returnStr.append(s).append("\n");
        }
        return returnStr.toString();
    }
}