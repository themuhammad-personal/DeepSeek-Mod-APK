package com.themuhammad.deepseekmod.extension;

/**
 * Cooperative cancellation flag shared between the agent session and a running tool.
 *
 * The flag is set by the Stop control. A tool that runs for a long time has to
 * check it while it works, because the executor can only interrupt the thread.
 */
public final class SdsCancellation {
    private volatile boolean cancelled;

    public void cancel() {
        cancelled = true;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void reset() {
        cancelled = false;
    }
}
