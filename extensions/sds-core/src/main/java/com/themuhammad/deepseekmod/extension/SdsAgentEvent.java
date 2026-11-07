package com.themuhammad.deepseekmod.extension;

/**
 * One entry of the agent activity timeline.
 *
 * The detail is deliberately short and free of assistant content: it names the
 * tool, the decision or the result status, never the conversation.
 */
public final class SdsAgentEvent {
    public enum Type {
        SESSION_STARTED,
        PROPOSAL_RECEIVED,
        APPROVED,
        DENIED,
        CANCELLED,
        STOP_REQUESTED,
        RUN_STARTED,
        RUN_FINISHED,
        RUN_FAILED,
        RESET
    }

    private final long atMs;
    private final Type type;
    private final String detail;

    public SdsAgentEvent(long atMs, Type type, String detail) {
        if (type == null) throw new IllegalArgumentException("type is required");
        this.atMs = atMs;
        this.type = type;
        this.detail = detail == null ? "" : detail;
    }

    public long getAtMs() { return atMs; }
    public Type getType() { return type; }
    public String getDetail() { return detail; }

    @Override
    public String toString() {
        return type + (detail.isEmpty() ? "" : ": " + detail);
    }
}
