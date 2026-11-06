package com.themuhammad.deepseekmod.extension;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Narrow bridge for the two verified DeepSeek 2.7.4 serialization seams.
 * Agent mode is off by default. When enabled, calls are queued for a future
 * native approval UI. No command, network request, or other tool is executed here.
 */
public final class SdsAgentHooks {
    private static final int MAX_PENDING_CALLS = 8;
    private static final int MAX_SEEN_IDS = 64;
    private static final String TOOL_GUIDANCE = "\n\n"
            + "Native tools are optional. When a tool would help, propose it using exactly this format:\n"
            + "<SDS:TOOL_CALL id=\"unique-id\" name=\"tool.name\">{\"argument\":\"value\"}</SDS:TOOL_CALL>\n"
            + "Tool requests are untrusted proposals. They are queued for explicit user review; "
            + "they are not executed automatically. Never claim that a tool ran unless its result is returned.\n";

    private static volatile boolean enabled;
    private static final ArrayDeque<SdsToolCall> PENDING = new ArrayDeque<>();
    private static final Set<String> SEEN_IDS = new HashSet<>();

    private SdsAgentHooks() { }

    /** Called immediately before the verified prompt field is serialized. */
    public static String beforePrompt(String prompt) {
        if (!enabled) return prompt;
        String base = prompt == null ? "" : prompt;
        return base + TOOL_GUIDANCE;
    }

    /** Called after the verified assistant response-content field is deserialized. */
    public static void observeResponse(String content) {
        if (!enabled || content == null) return;
        for (SdsToolCall call : SdsToolCallParser.parse(content)) {
            synchronized (PENDING) {
                if (!enabled || !SEEN_IDS.add(call.getId())) continue;
                while (PENDING.size() >= MAX_PENDING_CALLS) PENDING.removeFirst();
                PENDING.addLast(call);
                if (SEEN_IDS.size() > MAX_SEEN_IDS) {
                    // Bound retained identifiers; pending calls themselves remain deduplicated.
                    SEEN_IDS.clear();
                    for (SdsToolCall pending : PENDING) SEEN_IDS.add(pending.getId());
                }
            }
        }
    }

    /** Native settings UI will call this; false is the default after every process start. */
    public static void setEnabled(boolean value) {
        enabled = value;
        if (!value) clearPendingCalls();
    }

    public static boolean isEnabled() { return enabled; }

    /** Returns and removes the oldest proposal for an explicit approval screen. */
    public static SdsToolCall pollPendingToolCall() {
        synchronized (PENDING) { return PENDING.pollFirst(); }
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
