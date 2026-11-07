package com.themuhammad.deepseekmod.extension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The agent state machine.
 *
 * It records what the user decided about a proposal, keeps a bounded activity
 * timeline and owns the cancellation flag that the Stop control sets. A tool is
 * only ever run after an explicit approval, and the session never decides that
 * on its own.
 *
 * The class is free of Android types so that it can be regression tested on a
 * plain JVM.
 */
public final class SdsAgentSession {
    public static final int MAX_EVENTS = 64;

    public enum State {
        /** No proposal is being handled. */
        IDLE,
        /** A proposal was received and is waiting for a decision. */
        COLLECTING,
        /** The proposal was approved and may be executed. */
        AWAITING_APPROVAL,
        /** An approved tool is running. */
        RUNNING,
        /** The last run finished successfully. */
        COMPLETED,
        /** The proposal was denied. */
        DENIED,
        /** The run was cancelled by the user. */
        CANCELLED,
        /** The last run failed. */
        FAILED
    }

    private final Object lock = new Object();
    private final List<SdsAgentEvent> events = new ArrayList<>();
    private final SdsCancellation cancellation = new SdsCancellation();

    private State state = State.IDLE;
    private String pendingCallId = "";
    private String pendingToolName = "";
    private SdsToolResult lastResult;

    public SdsAgentSession() {
        record(SdsAgentEvent.Type.SESSION_STARTED, "agent session started");
    }

    public State getState() {
        synchronized (lock) { return state; }
    }

    public String getPendingCallId() {
        synchronized (lock) { return pendingCallId; }
    }

    public String getPendingToolName() {
        synchronized (lock) { return pendingToolName; }
    }

    public SdsToolResult getLastResult() {
        synchronized (lock) { return lastResult; }
    }

    public SdsCancellation getCancellation() {
        return cancellation;
    }

    public List<SdsAgentEvent> getEvents() {
        synchronized (lock) {
            return Collections.unmodifiableList(new ArrayList<>(events));
        }
    }

    /**
     * Accepts a proposal from the model output. The proposal is untrusted and is
     * only registered here; nothing is executed.
     */
    public boolean onProposal(SdsToolCall call) {
        if (call == null) return false;
        synchronized (lock) {
            if (state != State.IDLE && state != State.COLLECTING
                    && state != State.COMPLETED && state != State.DENIED
                    && state != State.FAILED && state != State.CANCELLED) {
                return false;
            }
            pendingCallId = call.getId();
            pendingToolName = call.getName();
            // A new proposal is a new turn, so the cancellation of an earlier
            // turn must not stop this one before it even starts.
            cancellation.reset();
            state = State.COLLECTING;
            record(SdsAgentEvent.Type.PROPOSAL_RECEIVED, call.getName());
            return true;
        }
    }

    /** The user approved the pending proposal. */
    public boolean approve(String callId) {
        synchronized (lock) {
            if (state != State.COLLECTING) return false;
            if (callId == null || !callId.equals(pendingCallId)) return false;
            state = State.AWAITING_APPROVAL;
            record(SdsAgentEvent.Type.APPROVED, pendingToolName);
            return true;
        }
    }

    /** The user denied the pending proposal. */
    public boolean deny(String callId) {
        synchronized (lock) {
            if (state != State.COLLECTING && state != State.AWAITING_APPROVAL) return false;
            if (callId == null || !callId.equals(pendingCallId)) return false;
            state = State.DENIED;
            record(SdsAgentEvent.Type.DENIED, pendingToolName);
            return true;
        }
    }

    /** Marks the start of an approved run. */
    public boolean onRunStarted() {
        synchronized (lock) {
            if (state != State.AWAITING_APPROVAL) return false;
            state = State.RUNNING;
            record(SdsAgentEvent.Type.RUN_STARTED, pendingToolName);
            return true;
        }
    }

    /** Marks a successful run. */
    public boolean onRunSucceeded(SdsToolResult result) {
        synchronized (lock) {
            if (state != State.RUNNING) return false;
            lastResult = result;
            state = State.COMPLETED;
            record(SdsAgentEvent.Type.RUN_FINISHED, describe(result));
            return true;
        }
    }

    /** Marks a failed, timed out or rejected run. */
    public boolean onRunFailed(SdsToolResult result) {
        synchronized (lock) {
            if (state != State.RUNNING) return false;
            lastResult = result;
            state = State.FAILED;
            record(SdsAgentEvent.Type.RUN_FAILED, describe(result));
            return true;
        }
    }

    /**
     * The Stop control. It cancels a waiting or running tool and records the
     * decision. It never approves anything.
     */
    public boolean requestStop() {
        synchronized (lock) {
            if (state != State.COLLECTING && state != State.AWAITING_APPROVAL
                    && state != State.RUNNING) {
                return false;
            }
            cancellation.cancel();
            state = State.CANCELLED;
            record(SdsAgentEvent.Type.STOP_REQUESTED, pendingToolName);
            record(SdsAgentEvent.Type.CANCELLED, pendingToolName);
            return true;
        }
    }

    /** Clears the session and starts a new one. */
    public void reset() {
        synchronized (lock) {
            state = State.IDLE;
            pendingCallId = "";
            pendingToolName = "";
            lastResult = null;
            cancellation.reset();
            record(SdsAgentEvent.Type.RESET, "agent session reset");
        }
    }

    private void record(SdsAgentEvent.Type type, String detail) {
        events.add(new SdsAgentEvent(System.currentTimeMillis(), type, detail));
        while (events.size() > MAX_EVENTS) {
            events.remove(0);
        }
    }

    private static String describe(SdsToolResult result) {
        if (result == null) return "no result";
        String error = result.getSafeError();
        return error.isEmpty() ? result.getStatus().name() : result.getStatus().name() + " " + error;
    }
}
