package com.themuhammad.deepseekmod.extension;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Narrow bridge for the two inspected DeepSeek 2.7.4 serialization seams.
 * Agent mode is off by default. Parsed calls remain proposals; no tool executes here.
 */
public final class SdsAgentHooks {
    private static final int MAX_PENDING_CALLS = 8;
    private static final int MAX_SEEN_IDS = 64;
    private static final String TOOL_GUIDANCE = "\n\n"
            + "Native tools are optional. When a tool would help, propose it using exactly this format:\n"
            + "<SDS:TOOL_CALL id=\"unique-id\" name=\"tool.name\">{\"argument\":\"value\"}</SDS:TOOL_CALL>\n"
            + "Tool requests are untrusted proposals. They are collected for explicit user review; "
            + "they are not executed automatically. Never claim that a tool ran unless its result is returned.\n";

    private static volatile boolean enabled;
    private static final ArrayDeque<SdsToolCall> PENDING = new ArrayDeque<>();
    private static final Set<String> SEEN_IDS = new HashSet<>();

    private SdsAgentHooks() { }

    /** Called immediately before the inspected prompt field is serialized. */
    public static String beforePrompt(String prompt) {
        if (!enabled) return prompt;
        return (prompt == null ? "" : prompt) + TOOL_GUIDANCE;
    }

    /** Called after the inspected assistant response-content field is deserialized. */
    public static void observeResponse(String content) {
        if (!enabled || content == null) return;
        for (SdsToolCall call : SdsToolCallParser.parse(content)) {
            synchronized (PENDING) {
                if (!enabled || !SEEN_IDS.add(call.getId())) continue;
                while (PENDING.size() >= MAX_PENDING_CALLS) PENDING.removeFirst();
                PENDING.addLast(call);
                if (SEEN_IDS.size() > MAX_SEEN_IDS) {
                    SEEN_IDS.clear();
                    for (SdsToolCall pending : PENDING) SEEN_IDS.add(pending.getId());
                }
            }
        }
    }

    /** Session-only until a separately verified preference initialization hook exists. */
    public static void setEnabled(boolean value) {
        enabled = value;
        if (!value) clearPendingCalls();
    }

    public static boolean isEnabled() { return enabled; }

    /** Non-destructive snapshot for the native proposal-review screen. */
    public static List<SdsToolCall> snapshotPendingToolCalls() {
        synchronized (PENDING) {
            return Collections.unmodifiableList(new ArrayList<>(PENDING));
        }
    }

    /** Removes a reviewed/dismissed proposal. This does not approve or execute it. */
    public static boolean dismissPendingToolCall(String id) {
        if (id == null) return false;
        synchronized (PENDING) {
            boolean removed = false;
            java.util.Iterator<SdsToolCall> iterator = PENDING.iterator();
            while (iterator.hasNext()) {
                if (id.equals(iterator.next().getId())) {
                    iterator.remove();
                    removed = true;
                    break;
                }
            }
            return removed;
        }
    }

    public static int pendingCount() {
        synchronized (PENDING) { return PENDING.size(); }
    }

    public static void clearPendingCalls() {
        synchronized (PENDING) {
            PENDING.clear();
            SEEN_IDS.clear();
        }
    }
}
