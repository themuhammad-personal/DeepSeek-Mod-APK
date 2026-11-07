package com.themuhammad.deepseekmod.extension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The allow-list of executable tools.
 *
 * Only a tool that was registered explicitly can ever run. There is no dynamic
 * lookup by class name, no reflection and no command execution, so a model
 * cannot reach anything that the app did not put into this registry.
 */
public final class SdsToolRegistry {
    private static final int MAX_TOOLS = 32;

    private final Map<String, SdsTool> tools = new LinkedHashMap<>();

    /** Registers a tool. A duplicate name is refused. */
    public void register(SdsTool tool) {
        if (tool == null) throw new IllegalArgumentException("tool is required");
        String name = tool.getName();
        if (name == null || !name.matches("[A-Za-z][A-Za-z0-9_.-]{0,63}")) {
            throw new IllegalArgumentException("invalid tool name: " + name);
        }
        if (tools.containsKey(name)) {
            throw new IllegalArgumentException("tool is already registered: " + name);
        }
        if (tools.size() >= MAX_TOOLS) {
            throw new IllegalArgumentException("too many registered tools");
        }
        tools.put(name, tool);
    }

    /** Returns the registered tool or null when the name is not on the allow-list. */
    public SdsTool find(String name) {
        if (name == null) return null;
        return tools.get(name);
    }

    public boolean contains(String name) {
        return find(name) != null;
    }

    public List<String> names() {
        return Collections.unmodifiableList(new ArrayList<>(tools.keySet()));
    }

    /** A description of the allow-list for the console. */
    public List<String> describe() {
        List<String> lines = new ArrayList<>();
        for (SdsTool tool : tools.values()) {
            lines.add(tool.getName()
                    + (tool.isReadOnly() ? " [read-only]" : " [writes]")
                    + (tool.requiresNetwork() ? " [network]" : "")
                    + " - " + tool.getDescription()
                    + "\n  schema: " + tool.getSchema());
        }
        return lines;
    }
}
