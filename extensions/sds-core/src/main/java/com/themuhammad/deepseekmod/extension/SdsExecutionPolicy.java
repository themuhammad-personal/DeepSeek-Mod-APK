package com.themuhammad.deepseekmod.extension;

public final class SdsExecutionPolicy {
    public static final long MIN_TIMEOUT_MS = 1_000L;
    public static final long MAX_TIMEOUT_MS = 120_000L;
    public static final int MAX_OUTPUT_CHARS = 12_000;
    public static final int MAX_TOOL_CALLS_PER_TURN = 8;

    public static final SdsExecutionPolicy SAFE_DEFAULT =
            new SdsExecutionPolicy(true, 60_000L, MAX_OUTPUT_CHARS, false,
                    MAX_TOOL_CALLS_PER_TURN);

    private final boolean approvalRequired;
    private final long commandTimeoutMs;
    private final int maxOutputChars;
    private final boolean networkAllowed;
    private final int maxToolCallsPerTurn;

    public SdsExecutionPolicy(
            boolean approvalRequired,
            long commandTimeoutMs,
            int maxOutputChars,
            boolean networkAllowed,
            int maxToolCallsPerTurn) {
        this.approvalRequired = approvalRequired;
        this.commandTimeoutMs = clampTimeout(commandTimeoutMs);
        this.maxOutputChars = clampOutputLimit(maxOutputChars);
        this.networkAllowed = networkAllowed;
        this.maxToolCallsPerTurn = Math.max(1,
                Math.min(MAX_TOOL_CALLS_PER_TURN, maxToolCallsPerTurn));
    }

    public boolean isApprovalRequired() { return approvalRequired; }
    public long getCommandTimeoutMs() { return commandTimeoutMs; }
    public int getMaxOutputChars() { return maxOutputChars; }
    public boolean isNetworkAllowed() { return networkAllowed; }
    public int getMaxToolCallsPerTurn() { return maxToolCallsPerTurn; }

    public static long clampTimeout(long requestedMs) {
        return Math.max(MIN_TIMEOUT_MS, Math.min(MAX_TIMEOUT_MS, requestedMs));
    }

    public static int clampOutputLimit(int requestedChars) {
        return Math.max(256, Math.min(MAX_OUTPUT_CHARS, requestedChars));
    }

    public String limitOutput(String value) {
        if (value == null) return "";
        if (value.length() <= maxOutputChars) return value;
        String marker = "\n[output truncated]";
        int keep = Math.max(0, maxOutputChars - marker.length());
        return value.substring(0, keep) + marker;
    }
}
