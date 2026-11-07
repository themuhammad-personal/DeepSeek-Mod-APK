package com.themuhammad.deepseekmod.extension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The MCP tools.
 *
 * Every tool of an enabled server becomes one entry of the allow-list, named
 * {@code mcp.<server>.<tool>}. The entry needs the network, so the executor
 * refuses it while the network policy forbids it, and it is not read-only, so
 * it needs an explicit approval for every single call.
 *
 * The arguments of an MCP tool are nested, so the strict flat reader cannot
 * take them directly. The tool therefore takes one flat string that holds the
 * JSON document of the arguments, and that document is read with the strict
 * reader.
 */
public final class SdsMcpTools {
    public static final String ARGUMENTS = "arguments";

    private static final Map<String, List<String>> KNOWN_TOOLS = new LinkedHashMap<>();

    private SdsMcpTools() {
    }

    /** Remembers a tool of a server, so it can be put on the allow-list. */
    public static synchronized void rememberTool(String server, String tool) {
        if (server == null || tool == null || server.isEmpty() || tool.isEmpty()) return;
        List<String> tools = KNOWN_TOOLS.get(server);
        if (tools == null) {
            tools = new ArrayList<>();
            KNOWN_TOOLS.put(server, tools);
        }
        if (!tools.contains(tool) && tools.size() < 128) {
            tools.add(tool);
        }
    }

    /** The remembered tools of a server. */
    public static synchronized List<String> knownTools(String server) {
        List<String> tools = KNOWN_TOOLS.get(server);
        return tools == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(tools));
    }

    /** Forgets every remembered tool. */
    public static synchronized void forgetAll() {
        KNOWN_TOOLS.clear();
    }

    /** Builds the allow-list entries of the enabled servers. */
    public static SdsToolRegistry createRegistry(SdsMcpConfigStore store) {
        SdsToolRegistry registry = new SdsToolRegistry();
        if (store == null) return registry;
        for (SdsMcpConfigStore.Server server : store.enabledServers()) {
            for (String tool : knownTools(server.getName())) {
                String name = server.toolPrefix() + tool;
                if (registry.contains(name)) continue;
                registry.register(new McpTool(server, tool));
            }
        }
        return registry;
    }

    /**
     * Asks the servers for their tools.
     *
     * @return the tools that were found
     */
    public static List<SdsMcpClient.ToolInfo> refresh(SdsMcpConfigStore store) throws Exception {
        List<SdsMcpClient.ToolInfo> found = new ArrayList<>();
        if (store == null) return found;
        for (SdsMcpConfigStore.Server server : store.enabledServers()) {
            SdsMcpClient client = new SdsMcpClient(server, null);
            List<SdsMcpClient.ToolInfo> tools = client.listTools();
            for (SdsMcpClient.ToolInfo tool : tools) {
                rememberTool(server.getName(), tool.getName());
                found.add(tool);
            }
        }
        return found;
    }

    /** One tool of an MCP server. */
    public static final class McpTool implements SdsTool {
        private final SdsMcpConfigStore.Server server;
        private final String toolName;

        McpTool(SdsMcpConfigStore.Server server, String toolName) {
            this.server = server;
            this.toolName = toolName;
        }

        @Override
        public String getName() { return server.toolPrefix() + toolName; }

        @Override
        public String getDescription() {
            return "Calls the tool " + toolName + " of the MCP server " + server.getName() + ".";
        }

        @Override
        public String getSchema() { return "{\"" + ARGUMENTS + "\": string}"; }

        @Override
        public boolean isReadOnly() { return false; }

        @Override
        public boolean requiresNetwork() { return true; }

        @Override
        public SdsToolResult execute(SdsToolContext context, SdsToolCall call) {
            try {
                SdsToolArguments arguments = SdsToolArguments.parse(call.getArgumentsJson());
                arguments.requireOnly(ARGUMENTS);
                String document = arguments.getString(ARGUMENTS, "");
                if (!document.trim().isEmpty()) {
                    // The document has to be a real JSON object, so nothing that
                    // is not a document reaches the server.
                    SdsJson.parseObject(document);
                }
                SdsMcpClient client = new SdsMcpClient(server, null);
                String output = client.callTool(toolName, document);
                return new SdsToolResult(SdsToolResult.Status.SUCCESS, output, "");
            } catch (IllegalArgumentException refused) {
                return new SdsToolResult(SdsToolResult.Status.FAILED, "",
                        "the arguments did not match the schema");
            } catch (Exception refused) {
                return new SdsToolResult(SdsToolResult.Status.FAILED, "",
                        "the MCP server could not be reached");
            }
        }
    }
}
