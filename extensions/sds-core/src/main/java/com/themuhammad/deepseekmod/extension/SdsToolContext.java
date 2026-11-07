package com.themuhammad.deepseekmod.extension;

import java.io.File;

/**
 * The environment a tool runs in.
 *
 * The workspace is the only place a file tool may touch. The policy carries the
 * timeout and the output limit, and the cancellation flag is set by the Stop
 * control.
 */
public final class SdsToolContext {
    private final File workspace;
    private final SdsExecutionPolicy policy;
    private final SdsCancellation cancellation;

    public SdsToolContext(File workspace, SdsExecutionPolicy policy, SdsCancellation cancellation) {
        if (workspace == null) throw new IllegalArgumentException("workspace is required");
        this.workspace = workspace;
        this.policy = policy == null ? SdsExecutionPolicy.SAFE_DEFAULT : policy;
        this.cancellation = cancellation == null ? new SdsCancellation() : cancellation;
    }

    public File getWorkspace() {
        return workspace;
    }

    public SdsExecutionPolicy getPolicy() {
        return policy;
    }

    public SdsCancellation getCancellation() {
        return cancellation;
    }

    public boolean isCancelled() {
        return cancellation.isCancelled();
    }
}
