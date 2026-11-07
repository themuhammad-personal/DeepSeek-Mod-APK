package com.themuhammad.deepseekmod.extension;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The runtime state of the native features.
 *
 * The holder is free of Android types so that the whole feature logic can be
 * regression tested on a plain JVM. The Android glue fills the holder once, when
 * the app starts. Everything stays empty and harmless when it was never filled.
 */
public final class SdsRuntimeRegistry {
    public static final int MAX_MEMORY_PROMPT_CHARS = 2_000;
    public static final int MAX_PROJECT_PROMPT_CHARS = 1_000;
    public static final int MAX_FILE_PROMPT_CHARS = 2_000;

    private static volatile File workspace;
    private static volatile SdsMemoryStore memoryStore;
    private static volatile SdsProjectRegistry projectRegistry;
    private static volatile SdsArtifactStore artifactStore;
    private static volatile SdsUiSpec lastUiSpec;
    private static volatile SdsSandboxManager sandboxManager;
    private static volatile SdsMcpConfigStore mcpConfigStore;
    private static volatile boolean networkAllowed;
    private static volatile boolean sandboxEnabled;
    private static volatile boolean includeMemoryInPrompt;
    private static volatile boolean defaultsRegistered;

    private SdsRuntimeRegistry() {
    }

    public static File getWorkspace() {
        return workspace;
    }

    public static void setWorkspace(File value) {
        workspace = value;
    }

    public static SdsMemoryStore getMemoryStore() {
        return memoryStore;
    }

    public static void setMemoryStore(SdsMemoryStore value) {
        memoryStore = value;
    }

    public static SdsProjectRegistry getProjectRegistry() {
        return projectRegistry;
    }

    public static void setProjectRegistry(SdsProjectRegistry value) {
        projectRegistry = value;
    }

    /** The MCP server list, or null when it was never prepared. */
    public static SdsMcpConfigStore getMcpConfigStore() {
        return mcpConfigStore;
    }

    public static void setMcpConfigStore(SdsMcpConfigStore value) {
        mcpConfigStore = value;
    }

    /**
     * True when the agent may use the network.
     *
     * It is off by default, so a tool that needs the network is refused until
     * the user turns it on in the console.
     */
    public static boolean isNetworkAllowed() {
        return networkAllowed;
    }

    public static void setNetworkAllowed(boolean value) {
        networkAllowed = value;
    }

    /** The policy the executor uses, built from the current switches. */
    public static SdsExecutionPolicy currentPolicy() {
        return new SdsExecutionPolicy(true, 60_000L, SdsExecutionPolicy.MAX_OUTPUT_CHARS,
                networkAllowed, SdsExecutionPolicy.MAX_TOOL_CALLS_PER_TURN);
    }

    /** The Linux Studio runtime, or null when it was never prepared. */
    public static SdsSandboxManager getSandboxManager() {
        return sandboxManager;
    }

    public static void setSandboxManager(SdsSandboxManager value) {
        sandboxManager = value;
    }

    /** True when the Linux Studio command tool is offered to the agent. Off by default. */
    public static boolean isSandboxEnabled() {
        return sandboxEnabled;
    }

    public static void setSandboxEnabled(boolean value) {
        sandboxEnabled = value;
    }

    /** The interface the assistant asked for last, or null. */
    public static SdsUiSpec getLastUiSpec() {
        return lastUiSpec;
    }

    public static void setLastUiSpec(SdsUiSpec value) {
        lastUiSpec = value;
    }

    public static SdsArtifactStore getArtifactStore() {
        return artifactStore;
    }

    public static void setArtifactStore(SdsArtifactStore value) {
        artifactStore = value;
    }

    /** True when the memories are added to every prompt. Off by default. */
    public static boolean isIncludeMemoryInPrompt() {
        return includeMemoryInPrompt;
    }

    public static void setIncludeMemoryInPrompt(boolean value) {
        includeMemoryInPrompt = value;
    }

    /**
     * Registers the built-in slash commands once.
     *
     * The built-in commands only read: they never delete a memory, never create
     * a project and never touch anything outside the workspace.
     */
    public static synchronized void ensureDefaults() {
        if (defaultsRegistered) return;
        defaultsRegistered = true;

        SdsSlashCommands.register("/help", "Lists the available slash commands.", new SdsSlashCommands.Expander() {
            @Override
            public String expand(String arguments) {
                StringBuilder builder = new StringBuilder(
                        "The user asked for the list of the available slash commands.\n");
                for (String line : SdsSlashCommands.describe()) {
                    builder.append(line).append('\n');
                }
                return builder.toString();
            }
        });

        SdsSlashCommands.register("/memory", "Adds the stored memories to the prompt.", new SdsSlashCommands.Expander() {
            @Override
            public String expand(String arguments) {
                SdsMemoryStore store = getMemoryStore();
                if (store == null) return "There are no stored memories.";
                String block = store.asPromptBlock(MAX_MEMORY_PROMPT_CHARS);
                return block.isEmpty()
                        ? "There are no stored memories."
                        : "The user asked for the stored memories:\n" + block;
            }
        });

        SdsSlashCommands.register("/projects", "Lists the native projects.", new SdsSlashCommands.Expander() {
            @Override
            public String expand(String arguments) {
                SdsProjectRegistry registry = getProjectRegistry();
                if (registry == null) return "There are no projects.";
                String block = registry.asPromptBlock(MAX_PROJECT_PROMPT_CHARS);
                return block.isEmpty()
                        ? "There are no projects."
                        : "The user asked for the projects:\n" + block;
            }
        });

        SdsSlashCommands.register("/files", "Lists the files of the private workspace.", new SdsSlashCommands.Expander() {
            @Override
            public String expand(String arguments) {
                File root = getWorkspace();
                if (root == null || !root.isDirectory()) return "The workspace is empty.";
                StringBuilder builder = new StringBuilder("The user asked for the workspace files:\n");
                File[] children = root.listFiles();
                if (children == null) return "The workspace is empty.";
                java.util.Arrays.sort(children);
                for (File child : children) {
                    if (builder.length() > MAX_FILE_PROMPT_CHARS) break;
                    builder.append("- ").append(child.getName())
                            .append(child.isDirectory() ? "/" : "")
                            .append('\n');
                }
                return builder.toString();
            }
        });
    }

    /** The memories that are added to a prompt, or an empty string. */
    public static String memoryPromptBlock() {
        if (!includeMemoryInPrompt) return "";
        SdsMemoryStore store = getMemoryStore();
        if (store == null) return "";
        String block = store.asPromptBlock(MAX_MEMORY_PROMPT_CHARS);
        return block.isEmpty() ? "" : "\n\nStored memories:\n" + block;
    }

    /** The slash commands that are currently registered. */
    public static List<String> commandNames() {
        return Collections.unmodifiableList(new ArrayList<>(SdsSlashCommands.names()));
    }
}
