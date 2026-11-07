package com.themuhammad.deepseekmod.extension;

/** Bounded result envelope. A tool adapter must never place secrets in output. */
public final class SdsToolResult {
    public enum Status {
        SUCCESS, APPROVAL_REQUIRED, DENIED, FAILED, CANCELLED, TIMED_OUT
    }

    private final Status status;
    private final String output;
    private final String safeError;

    public SdsToolResult(Status status, String output, String safeError) {
        this(status, output, safeError, SdsExecutionPolicy.SAFE_DEFAULT);
    }

    public SdsToolResult(Status status, String output, String safeError, SdsExecutionPolicy policy) {
        if (status == null) throw new IllegalArgumentException("status is required");
        SdsExecutionPolicy effective = policy == null ? SdsExecutionPolicy.SAFE_DEFAULT : policy;
        this.status = status;
        this.output = effective.limitOutput(output);
        this.safeError = safeError == null ? "" : effective.limitOutput(safeError);
    }

    public Status getStatus() { return status; }
    public String getOutput() { return output; }
    public String getSafeError() { return safeError; }
}
