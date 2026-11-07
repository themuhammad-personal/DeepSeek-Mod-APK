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
                StringBuilder output = new StringBuilder();
                boolean truncated = false;
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), "UTF-8"));
                try {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (output.length() + line.length() + 1 > SdsSandboxConfig.MAX_OUTPUT_CHARS) {
                            truncated = true;
                            break;
                        }
                        output.append(line).append('\n');
                        if (context.isCancelled()) break;
                    }
                } finally {
                    reader.close();
                }
                int exitCode = -1;
                if (!context.isCancelled()) {
                    if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                        process.destroyForcibly();
                        return new SdsToolResult(SdsToolResult.Status.TIMED_OUT, output.toString(),
                                "the command did not finish in time");
                    }
                    exitCode = process.exitValue();
                } else {
                    process.destroyForcibly();
                    return new SdsToolResult(SdsToolResult.Status.CANCELLED, output.toString(),
                            "the command was stopped");
                }
                if (truncated) {
                    output.append("[output truncated]\n");
                }
                return new SdsToolResult(SdsToolResult.Status.SUCCESS, output.toString(),
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

    /** The commands the console offers, kept out of the tool itself. */
    public static List<String> suggestedCommands() {
        return java.util.Arrays.asList("uname -a", "cat /etc/os-release", "ls -la /root");
    }
}
