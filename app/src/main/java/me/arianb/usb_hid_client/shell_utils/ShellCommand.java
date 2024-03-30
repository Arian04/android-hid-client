package me.arianb.usb_hid_client.shell_utils;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import timber.log.Timber;

public record ShellCommand(String[] command, int exitCode, String stdout, String stderr) {
    private static final long DEFAULT_TIMEOUT = 1;
    private static final TimeUnit DEFAULT_TIMEOUT_UNIT = TimeUnit.SECONDS;

    public static ShellCommand runAsRoot(String command) throws IOException, InterruptedException {
        String[] fullCommand = new String[]{
                RootState.SU_BINARY,
                "-c",
                "'" + command + "'"
        };
        return runShellCommand(fullCommand);
    }

    public static ShellCommand runAsRoot(String[] command) throws IOException, InterruptedException {
        String[] fullCommand = mergeStringArrays(new String[]{
                RootState.SU_BINARY,
                "-c",
        }, command);
        return runShellCommand(fullCommand);
    }

    public static ShellCommand run(String[] command) throws IOException, InterruptedException {
        return runShellCommand(command);
    }

    private static ShellCommand runShellCommand(String[] command) throws IOException, InterruptedException {
        Timber.d("running command: %s", Arrays.toString(command));

        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command(command);

        final Process shellProcess = processBuilder.start();
        if (!shellProcess.waitFor(DEFAULT_TIMEOUT, DEFAULT_TIMEOUT_UNIT)) {
            Timber.e("Command timed out after: %s %s", DEFAULT_TIMEOUT, DEFAULT_TIMEOUT_UNIT);
            shellProcess.destroyForcibly();
            if (!shellProcess.waitFor(DEFAULT_TIMEOUT, DEFAULT_TIMEOUT_UNIT)) {
                Timber.e("Forceful destruction of command process timed out after: %s %s", DEFAULT_TIMEOUT, DEFAULT_TIMEOUT_UNIT);
                throw new IOException("Forceful destruction of command process timed out");
            }
        }

        // NOTE: stdout and stdin stream names are weirdly the opposite of what you'd expect
        //final DataOutputStream shellStdInputStream = new DataOutputStream(shellProcess.getOutputStream());
        final DataInputStream shellStdOutputStream = new DataInputStream(shellProcess.getInputStream());
        final DataInputStream shellStdErrorStream = new DataInputStream(shellProcess.getErrorStream());

        final int exitCode = shellProcess.exitValue();
        final String stdOutput = streamToString(shellStdOutputStream);
        final String stdError = streamToString(shellStdErrorStream);

        return new ShellCommand(
                command,
                exitCode,
                stdOutput,
                stdError
        );
    }

    public static ShellCommand addSelinuxPolicy(String policy) throws IOException, InterruptedException {
        String sepolicyCommand = RootState.getSepolicyCommand();

        if (RootState.getRootMethod() == null) {
            Timber.e("Unknown root method");
            return null;
        }

        if (sepolicyCommand == null) {
            Timber.e("Failed to get command for changing selinux policy");
            return null;
        }

        // NOTE: the policy being quoted matters (at least for ksud)
        String command = String.format("%s '%s'", sepolicyCommand, policy);

        return runAsRoot(command);
    }

    private static String streamToString(DataInputStream stream) throws IOException {
        BufferedReader streamReader = new BufferedReader(new InputStreamReader(stream));

        //noinspection UnusedAssignment: I think explicitly setting = null makes the code clearer here
        String s = null;
        StringBuilder returnStr = new StringBuilder();
        while ((s = streamReader.readLine()) != null) {
            returnStr.append(s).append("\n");
        }
        return returnStr.toString();
    }

    private static String[] mergeStringArrays(String[] first, String[] second) {
        String[] merged = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, merged, first.length, second.length);
        return merged;
    }
}