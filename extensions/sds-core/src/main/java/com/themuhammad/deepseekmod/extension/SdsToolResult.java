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
        if (status == null) throw new IllegalArgumentException("status is required");
        this.status = status;
        this.output = SdsExecutionPolicy.SAFE_DEFAULT.limitOutput(output);
        this.safeError = safeError == null ? "" :
                SdsExecutionPolicy.SAFE_DEFAULT.limitOutput(safeError);
    }

    public Status getStatus() { return status; }
    public String getOutput() { return output; }
    public String getSafeError() { return safeError; }
}
