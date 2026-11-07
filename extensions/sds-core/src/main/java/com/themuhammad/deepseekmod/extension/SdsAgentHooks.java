package com.themuhammad.deepseekmod.extension;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Narrow bridge for the inspected DeepSeek 2.7.4 prompt serializer and mutable
 * SSE ResponseFragment content mutations. Agent mode is off by default.
 * Parsed calls remain proposals; no tool executes here.
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
    private static final AtomicLong PROMPT_HOOK_CALLS = new AtomicLong();
    private static final AtomicLong PROMPT_HOOK_FAILURES = new AtomicLong();
    private static final ArrayDeque<SdsToolCall> PENDING = new ArrayDeque<>();
    private static final Set<String> SEEN_IDS = new HashSet<>();

    private SdsAgentHooks() { }

    /**
     * Called immediately before the inspected prompt field is serialized.
     *
     * A slash command at the start of the prompt is expanded by the native
     * command registry, and the stored memories are added when the user turned
     * that on. Nothing else about the prompt is changed.
     */
    public static String beforePrompt(String prompt) {
        PROMPT_HOOK_CALLS.incrementAndGet();
        if (!enabled || prompt == null) return prompt;
        try {
            String expanded = SdsSlashCommands.expand(prompt);
            if (expanded == null) return prompt;
            String memory = SdsRuntimeRegistry.memoryPromptBlock();
            return expanded + TOOL_GUIDANCE + (memory == null ? "" : memory);
        } catch (Throwable ignored) {
            // Fail open: a native Agent feature must never block official chat requests.
            PROMPT_HOOK_FAILURES.incrementAndGet();
            return prompt;
        }
    }

    /** Request-hook counters help distinguish a local hook failure from a server/network failure. */
    public static String promptHookSummary() {
        return "request hook calls " + PROMPT_HOOK_CALLS.get()
                + " | fail-open " + PROMPT_HOOK_FAILURES.get();
    }

    /**
     * Called after a ResponseFragment content SET or APPEND mutation.
     * Both hooks pass the complete accumulated content, not just a delta.
     *
     * The observer diagnostics are recorded even when proposal mode is off, so a
     * device test can prove that the hook fires and that it receives the complete
     * accumulated content. The diagnostics never store the content itself.
     */
    public static void observeResponse(String completeContent) {
        if (completeContent == null) return;
        try {
            SdsObserverDiagnostics.get().onResponse(completeContent);
        } catch (Throwable ignored) {
            // Diagnostics must never interrupt the official response stream.
        }
        if (!enabled) return;
        try {
            for (SdsToolCall call : SdsToolCallParser.parse(completeContent)) {
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
        } catch (Throwable ignored) {
            // Parsing an optional Agent proposal must never interrupt chat rendering.
        }
    }

    /**
     * Called on the accumulated content before the app stores it.
     *
     * It observes the content exactly like {@link #observeResponse(String)} and
     * it can add the result of an approved tool run to the content, so the
     * model reads what the tool returned. The handback is off by default and
     * it never happens twice, because the marker is checked first.
     *
     * @return the content that the app has to store
     */
    public static String observeResponseAppend(String completeContent) {
        try {
            observeResponse(completeContent);
            return SdsToolResultHandoff.applyTo(completeContent);
        } catch (Throwable ignored) {
            // Fail open: always return the original response content to DeepSeek.
            return completeContent;
        }
    }

    /** The observer metrics of this process. */
    public static SdsObserverDiagnostics getDiagnostics() {
        return SdsObserverDiagnostics.get();
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
