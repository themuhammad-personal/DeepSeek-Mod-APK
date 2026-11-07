package com.themuhammad.deepseekmod.extension;

/**
 * A native tool that the agent may run.
 *
 * A tool is only reachable through {@link SdsToolRegistry}, so an unregistered
 * tool name can never be executed. Every tool declares its own strict schema and
 * whether it writes, needs the network, or only reads the private workspace.
 */
public interface SdsTool {
    /** The stable name the model has to use in a tool call. */
    String getName();

    /** A one line description shown in the console. */
    String getDescription();

    /** The exact argument schema the tool accepts. */
    String getSchema();

    /** True when the tool only reads and never writes. */
    boolean isReadOnly();

    /** True when the tool needs network access. Such tools are refused by default. */
    boolean requiresNetwork();

    /** Runs the tool. The implementation has to respect the context cancellation. */
    SdsToolResult execute(SdsToolContext context, SdsToolCall call) throws Exception;
}
