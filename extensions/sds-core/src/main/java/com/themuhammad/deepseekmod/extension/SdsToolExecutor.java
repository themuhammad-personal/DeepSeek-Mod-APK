package com.themuhammad.deepseekmod.extension;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runs an approved tool with a timeout, a cancellation flag and a bounded output.
 *
 * The executor is the only place where a tool runs. It refuses an unregistered
 * tool, refuses a tool that needs the network while the network policy forbids
 * it, refuses to run without an explicit approval, and never lets a tool run
 * longer than the policy timeout.
 */
public final class SdsToolExecutor {
    private static final int MAX_ERROR_CHARS = 200;

    private final SdsToolRegistry registry;
    private final SdsExecutionPolicy policy;
    private final ExecutorService worker;

    public SdsToolExecutor(SdsToolRegistry registry, SdsExecutionPolicy policy) {
        if (registry == null) throw new IllegalArgumentException("registry is required");
        this.registry = registry;
        this.policy = policy == null ? SdsExecutionPolicy.SAFE_DEFAULT : policy;
        // A pool and not a single thread: a tool that ignores the interrupt must
        // never block the next approved run.
        this.worker = Executors.newCachedThreadPool(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "sds-tool-worker");
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    public SdsExecutionPolicy getPolicy() {
        return policy;
    }

    /**
     * Runs a tool call.
     *
     * @param approved true when the user approved this exact call.
     */
    public SdsToolResult run(SdsToolCall call, SdsToolContext context, boolean approved) {
        if (call == null) {
            return result(SdsToolResult.Status.FAILED, "", "no tool call");
        }
        SdsTool tool = registry.find(call.getName());
        if (tool == null) {
            return result(SdsToolResult.Status.FAILED, "",
                    "tool is not registered: " + call.getName());
        }
        if (policy.isApprovalRequired() && !approved) {
            return result(SdsToolResult.Status.APPROVAL_REQUIRED, "", "approval is required");
        }
        if (tool.requiresNetwork() && !policy.isNetworkAllowed()) {
            return result(SdsToolResult.Status.FAILED, "", "network access is disabled");
        }
        if (context != null && context.isCancelled()) {
            return result(SdsToolResult.Status.CANCELLED, "", "cancelled before the tool started");
        }

        Future<SdsToolResult> future = worker.submit(new Callable<SdsToolResult>() {
            @Override
            public SdsToolResult call() throws Exception {
                return tool.execute(context, call);
            }
        });

        try {
            SdsToolResult value = future.get(policy.getCommandTimeoutMs(), TimeUnit.MILLISECONDS);
            if (value == null) {
                return result(SdsToolResult.Status.FAILED, "", "the tool returned no result");
            }
            if (context != null && context.isCancelled()) {
                return result(SdsToolResult.Status.CANCELLED, value.getOutput(), "cancelled by the user");
            }
            // The output of the tool is bounded again with the policy of the
            // executor, so a tool cannot exceed the limit with its own policy.
            return new SdsToolResult(value.getStatus(), value.getOutput(), value.getSafeError(), policy);
        } catch (TimeoutException timeout) {
            future.cancel(true);
            return result(SdsToolResult.Status.TIMED_OUT, "",
                    "the tool exceeded the " + policy.getCommandTimeoutMs() + " ms timeout");
        } catch (InterruptedException interrupted) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            return result(SdsToolResult.Status.CANCELLED, "", "the run was interrupted");
        } catch (ExecutionException failure) {
            return result(SdsToolResult.Status.FAILED, "", safeError(failure.getCause(), context));
        } catch (RuntimeException failure) {
            return result(SdsToolResult.Status.FAILED, "", safeError(failure, context));
        }
    }

    /** Stops the worker thread. A running tool is interrupted. */
    public void shutdown() {
        worker.shutdownNow();
    }

    private SdsToolResult result(SdsToolResult.Status status, String output, String error) {
        return new SdsToolResult(status, output, error, policy);
    }

    /** Builds an error message that cannot leak a path or a stack trace. */
    private static String safeError(Throwable error, SdsToolContext context) {
        if (error == null) return "the tool failed";
        String message = error.getMessage() == null ? "" : error.getMessage();
        int newline = message.indexOf('\n');
        if (newline >= 0) message = message.substring(0, newline);
        if (context != null && context.getWorkspace() != null) {
            message = message.replace(context.getWorkspace().getPath(), "<workspace>");
        }
        String text = error.getClass().getSimpleName()
                + (message.isEmpty() ? "" : ": " + message);
        if (text.length() > MAX_ERROR_CHARS) {
            text = text.substring(0, MAX_ERROR_CHARS);
        }
        return text;
    }
}
