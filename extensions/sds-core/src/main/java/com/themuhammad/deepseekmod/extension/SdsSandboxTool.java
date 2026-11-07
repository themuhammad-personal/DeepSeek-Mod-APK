package com.themuhammad.deepseekmod.extension;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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

                ProcessBuilder builder = new ProcessBuilder(
                        launcher.getAbsolutePath(),
                        "-r", rootfs.getAbsolutePath(),
                        "-0",
                        "--link2symlink",
                        "-w", "/root",
                        "/bin/sh", "-c", command);
                builder.redirectErrorStream(true);
                Map<String, String> environment = builder.environment();
                for (String[] entry : SdsSandboxConfig.environment(
                        context.getWorkspace().getAbsolutePath(), "/root")) {
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

                int exitCode = -1;
                if (context.isCancelled()) {
                    process.destroyForcibly();
                    return new SdsToolResult(SdsToolResult.Status.CANCELLED, snapshot(output),
                            "the command was stopped");
                }
                if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    return new SdsToolResult(SdsToolResult.Status.TIMED_OUT, snapshot(output),
                            "the command did not finish in time");
                }
                exitCode = process.exitValue();
                // The reader needs a moment to drain what is left in the pipe.
                reader.join(500);
                String text = snapshot(output);
                if (truncated[0]) {
                    text = text + "[output truncated]\n";
                }
                return new SdsToolResult(SdsToolResult.Status.SUCCESS, text,
                        exitCode == 0 ? "" : "the command exited with " + exitCode);
            } catch (IllegalArgumentException refused) {
                return new SdsToolResult(SdsToolResult.Status.FAILED, "",
                        "the arguments did not match the schema");
            } catch (java.io.IOException refused) {
                return new SdsToolResult(SdsToolResult.Status.FAILED, "",
                        "the Linux Studio could not be started");
            }
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
