package com.themuhammad.deepseekmod.extension;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;

/**
 * The Linux Studio command tool.
 *
 * A command is only executed when every one of these is true:
 *
 * <ul>
 *   <li>the user turned the sandbox on in the console,</li>
 *   <li>the tool is on the allow-list,</li>
 *   <li>the user approved this exact command in the console,</li>
 *   <li>the PRoot runtime and the root filesystem are present.</li>
 * </ul>
 *
 * The command runs with the identity of the app inside the PRoot path
 * translation, with a fixed environment, with a fixed timeout and with a
 * bounded output. The timeout and the cancellation flag are honoured.
 *
 * PRoot is a userspace path translator and not a kernel sandbox, so the
 * sandbox is never treated as a security boundary.
 */
public final class SdsSandboxTool {
    public static final String RUN = "sandbox.run";

    private SdsSandboxTool() {
    }

    /** Builds a registry that contains exactly the sandbox tools. */
    public static SdsToolRegistry createRegistry() {
        SdsToolRegistry registry = new SdsToolRegistry();
        registry.register(new RunTool());
        return registry;
    }

    /** Runs one approved command inside the Linux Studio. */
    public static final class RunTool implements SdsTool {
        @Override
        public String getName() { return RUN; }

        @Override
        public String getDescription() {
            return "Runs one approved shell command inside the Linux Studio.";
        }

        @Override
        public String getSchema() { return "{\"command\": string}"; }

        @Override
        public boolean isReadOnly() { return false; }

        @Override
        public boolean requiresNetwork() { return false; }

        @Override
        public SdsToolResult execute(SdsToolContext context, SdsToolCall call) throws Exception {
            try {
                SdsToolArguments arguments = SdsToolArguments.parse(call.getArgumentsJson());
                arguments.requireOnly("command");
                String command = arguments.getString("command", "").trim();
                if (command.isEmpty()) {
                    return new SdsToolResult(SdsToolResult.Status.FAILED, "", "the command is required");
                }
                if (command.length() > SdsSandboxConfig.MAX_COMMAND_CHARS) {
                    return new SdsToolResult(SdsToolResult.Status.FAILED, "", "the command is too long");
                }
                SdsSandboxManager manager = SdsRuntimeRegistry.getSandboxManager();
                if (manager == null) {
                    return new SdsToolResult(SdsToolResult.Status.FAILED, "",
                            "the Linux Studio is not prepared");
                }
                if (!manager.isRuntimeAvailable()) {
                    return new SdsToolResult(SdsToolResult.Status.FAILED, "",
                            "the PRoot runtime is not bundled in this build");
                }
                if (!manager.isRuntimeIntact()) {
                    return new SdsToolResult(SdsToolResult.Status.FAILED, "",
                            "a file of the PRoot runtime does not match its checksum");
                }
                if (!manager.isRootfsReady()) {
                    return new SdsToolResult(SdsToolResult.Status.FAILED, "",
                            "the root filesystem is not ready");
                }
                File launcher = manager.findLauncher();
                File rootfs = manager.getRootfsDirectory();
                File workspace = manager.getWorkspaceDirectory();
                if (workspace != null && !workspace.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    workspace.mkdirs();
                }
                long timeoutMs = Math.min(context.getPolicy().getCommandTimeoutMs(),
                        SdsSandboxConfig.MAX_TIMEOUT_SECONDS * 1000L);
                long timeoutSeconds = Math.max(1L, timeoutMs / 1000L);

                java.util.List<String> arguments = new java.util.ArrayList<>();
                arguments.add(launcher.getAbsolutePath());
                arguments.add("-r");
                arguments.add(rootfs.getAbsolutePath());
                arguments.add("-0");
                arguments.add("--link2symlink");
                // The device nodes and the process list of the app are bound into
                // the guest, the same way the reference design of the proof of
                // concept did it. A path that does not exist on the device is
                // skipped, because PRoot refuses to start for a missing bind.
                for (String bind : new String[]{"/dev", "/proc"}) {
                    if (new File(bind).isDirectory()) {
                        arguments.add("-b");
                        arguments.add(bind);
                    }
                }
                arguments.add("-w");
                arguments.add("/root");
                arguments.add("/bin/sh");
                arguments.add("-c");
                arguments.add(command);
                ProcessBuilder builder = new ProcessBuilder(arguments);
                builder.redirectErrorStream(true);
                Map<String, String> environment = builder.environment();
                // The temporary directory of PRoot has to be writable by the app,
                // so it is the private directory of the app and not the workspace.
                File temporaryDirectory = manager.getFilesDirectory();
                if (temporaryDirectory == null) {
                    temporaryDirectory = context.getWorkspace();
                }
                for (String[] entry : SdsSandboxConfig.environment(
                        temporaryDirectory.getAbsolutePath(), "/root")) {
                    environment.put(entry[0], entry[1]);
                }
                // The environment of the app is not inherited, so a token of the
                // session can never reach the command.
                environment.keySet().retainAll(java.util.Arrays.asList(
                        "PROROOT_TMP_DIR", "HOME", "PATH", "TERM", "TMPDIR"));

                Process process = builder.start();
                // The output is read by a second thread. Reading it in the same
                // thread would block on the pipe, and the timeout could then never
                // be enforced.
                final StringBuilder output = new StringBuilder();
                final boolean[] truncated = new boolean[]{false};
                Thread reader = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        BufferedReader lines = null;
                        try {
                            lines = new BufferedReader(
                                    new InputStreamReader(process.getInputStream(), "UTF-8"));
                            String line;
                            while ((line = lines.readLine()) != null) {
                                synchronized (output) {
                                    if (output.length() + line.length() + 1
                                            > SdsSandboxConfig.MAX_OUTPUT_CHARS) {
                                        truncated[0] = true;
                                        break;
                                    }
                                    output.append(line).append('\n');
                                }
                            }
                        } catch (java.io.IOException ignored) {
                            // The process ended, so the stream is closed.
                        } finally {
                            if (lines != null) {
                                try {
                                    lines.close();
                                } catch (java.io.IOException ignored) {
                                    // Nothing else can be done here.
                                }
                            }
                        }
                    }
                }, "sds-sandbox-output");
                reader.setDaemon(true);
                reader.start();

                // The wait for the process runs in its own thread, because the
                // timed variant of waitFor needs a newer platform than the app
                // supports and because the stop of the user has to be noticed
                // while the command is running.
                final Process running = process;
                final int[] exitCode = new int[]{-1};
                final boolean[] finished = new boolean[]{false};
                Thread waiter = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            exitCode[0] = running.waitFor();
                        } catch (InterruptedException ignored) {
                            // The thread is a daemon, so the end of the program
                            // ends it as well.
                        } finally {
                            finished[0] = true;
                        }
                    }
                }, "sds-sandbox-wait");
                waiter.setDaemon(true);
                waiter.start();

                if (context.isCancelled()) {
                    terminate(running, waiter);
                    return new SdsToolResult(SdsToolResult.Status.CANCELLED, snapshot(output),
                            "the command was stopped");
                }
                long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
                boolean done = false;
                while (!done) {
                    waiter.join(200L);
                    done = finished[0];
                    if (done) break;
                    if (context.isCancelled()) {
                        terminate(running, waiter);
                        return new SdsToolResult(SdsToolResult.Status.CANCELLED, snapshot(output),
                                "the command was stopped");
                    }
                    if (System.currentTimeMillis() >= deadline) {
                        terminate(running, waiter);
                        return new SdsToolResult(SdsToolResult.Status.TIMED_OUT, snapshot(output),
                                "the command did not finish in time");
                    }
                }
                // The reader needs a moment to drain what is left in the pipe.
                reader.join(500);
                String text = snapshot(output);
                if (truncated[0]) {
                    text = text + "[output truncated]\n";
                }
                return new SdsToolResult(SdsToolResult.Status.SUCCESS, text,
                        exitCode[0] == 0 ? "" : "the command exited with " + exitCode[0]);
            } catch (IllegalArgumentException refused) {
                return new SdsToolResult(SdsToolResult.Status.FAILED, "",
                        "the arguments did not match the schema");
            } catch (java.io.IOException refused) {
                return new SdsToolResult(SdsToolResult.Status.FAILED, "",
                        "the Linux Studio could not be started");
            }
        }
    }

    /**
     * Stops a command.
     *
     * The soft stop is used first. The hard stop needs a newer platform than
     * the app supports, so it is only tried when the platform has it and the
     * failure is ignored when it has not.
     */
    private static void terminate(Process process, Thread waiter) {
        try {
            process.destroy();
        } catch (Throwable ignored) {
            // The process is gone already.
        }
        try {
            waiter.join(500L);
        } catch (InterruptedException ignored) {
            // The thread is a daemon.
        }
        try {
            if (waiter.isAlive()) {
                process.destroyForcibly();
            }
        } catch (Throwable ignored) {
            // The platform does not know the hard stop, the soft stop was enough.
        }
    }

    /** A copy of the output that the reader thread may still be filling. */
    private static String snapshot(StringBuilder output) {
        synchronized (output) {
            return output.toString();
        }
    }

    /** The commands the console offers, kept out of the tool itself. */
    public static List<String> suggestedCommands() {
        return java.util.Arrays.asList("uname -a", "cat /etc/os-release", "ls -la /root");
    }
}
