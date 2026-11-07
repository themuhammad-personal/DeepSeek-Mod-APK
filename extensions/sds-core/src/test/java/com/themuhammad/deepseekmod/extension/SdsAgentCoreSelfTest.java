package com.themuhammad.deepseekmod.extension;

import java.io.File;
import java.nio.file.Files;

/** Plain-Java regression tests; no Android device or third-party test framework is needed. */
public final class SdsAgentCoreSelfTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        testSafeDefaults();
        testPromptIsOptIn();
        testToolCallQueueAndDeduplication();
        testMalformedPayloadsAreIgnored();
        testStrictJsonGrammar();
        testPerResponseCallLimit();
        testWorkspaceConfinement();
        testObserverDiagnostics();
        testAgentSessionTransitions();
        testStrictToolArguments();
        testToolRegistryAllowList();
        testExecutorRequiresApproval();
        testExecutorRunsApprovedTool();
        testExecutorRefusesUnregisteredTool();
        testExecutorRefusesNetworkTool();
        testExecutorTimeoutAndCancel();
        testWorkspaceToolsStayInsideTheWorkspace();
        System.out.println("SDS agent-core self-test: PASS (" + checks + " checks)");
    }

    private static void testSafeDefaults() {
        SdsExecutionPolicy p = SdsExecutionPolicy.SAFE_DEFAULT;
        check(p.isApprovalRequired(), "approval must be required by default");
        check(!p.isNetworkAllowed(), "network must be disabled by default");
        check(p.getMaxToolCallsPerTurn() <= 8, "tool-call cap must be bounded");
        check(SdsExecutionPolicy.clampTimeout(Long.MAX_VALUE) == SdsExecutionPolicy.MAX_TIMEOUT_MS,
                "timeout upper bound");
        check(SdsExecutionPolicy.clampOutputLimit(Integer.MAX_VALUE) == SdsExecutionPolicy.MAX_OUTPUT_CHARS,
                "output upper bound");
    }

    private static void testPromptIsOptIn() {
        SdsAgentHooks.clearPendingCalls();
        SdsAgentHooks.setEnabled(false);
        check("hello".equals(SdsAgentHooks.beforePrompt("hello")), "disabled prompt must remain unchanged");
        SdsAgentHooks.setEnabled(true);
        check(SdsAgentHooks.beforePrompt("hello").contains("SDS:TOOL_CALL"), "enabled prompt guidance missing");
        SdsAgentHooks.setEnabled(false);
        check("hello".equals(SdsAgentHooks.beforePrompt("hello")), "disabled prompt must be unchanged again");
    }

    private static void testToolCallQueueAndDeduplication() {
        SdsAgentHooks.clearPendingCalls();
        SdsAgentHooks.setEnabled(true);
        String payload = "Before <SDS:TOOL_CALL id=\"call-1\" name=\"workspace.list\">"
                + "{\"path\":\".\",\"depth\":2}"
                + "</SDS:TOOL_CALL> after";
        SdsAgentHooks.observeResponse(payload);
        SdsAgentHooks.observeResponse(payload);
        check(SdsAgentHooks.pendingCount() == 1, "duplicate call id must be ignored");
        java.util.List<SdsToolCall> snapshot = SdsAgentHooks.snapshotPendingToolCalls();
        check(snapshot.size() == 1, "snapshot should expose one pending proposal");
        SdsToolCall call = snapshot.get(0);
        check("call-1".equals(call.getId()), "wrong call id");
        check("workspace.list".equals(call.getName()), "wrong tool name");
        check(call.getArgumentsJson().contains("\"depth\":2"), "arguments changed");
        check(SdsAgentHooks.pendingCount() == 1,
                "snapshot must not consume the proposal");
        check(SdsAgentHooks.dismissPendingToolCall("call-1"),
                "dismiss should remove an existing proposal");
        check(SdsAgentHooks.pendingCount() == 0, "dismiss must remove the proposal");
        check(!SdsAgentHooks.dismissPendingToolCall("call-1"),
                "dismiss should report false for an absent proposal");
        SdsAgentHooks.setEnabled(false);
    }

    private static void testMalformedPayloadsAreIgnored() {
        SdsAgentHooks.clearPendingCalls();
        SdsAgentHooks.setEnabled(true);
        SdsAgentHooks.observeResponse("<SDS:TOOL_CALL id=\"bad\" name=\"workspace.list\">{broken}</SDS:TOOL_CALL>");
        SdsAgentHooks.observeResponse("<SDS:TOOL_CALL id=\"bad-name\" name=\"bad name\">{}</SDS:TOOL_CALL>");
        SdsAgentHooks.observeResponse("<SDS:TOOL_CALL id=\"no-close\" name=\"workspace.list\">{}");
        check(SdsAgentHooks.pendingCount() == 0, "malformed tool call must be ignored");
        check(SdsToolCallParser.parse("x".repeat(SdsToolCallParser.MAX_RESPONSE_CHARS + 1)).isEmpty(),
                "oversized response must be ignored");
        SdsAgentHooks.setEnabled(false);
    }

    private static void testStrictJsonGrammar() {
        String valid = "<SDS:TOOL_CALL id=\"valid-json\" name=\"workspace.write\">"
                + "{\"path\":\"dir/file\",\"items\":[1,true,null,{\"text\":\"line\\nnext\"}]}"
                + "</SDS:TOOL_CALL>";
        check(SdsToolCallParser.parse(valid).size() == 1, "nested JSON object/array should parse");
        String trailingComma = "<SDS:TOOL_CALL id=\"trailing\" name=\"workspace.write\">"
                + "{\"path\":\"x\",}"
                + "</SDS:TOOL_CALL>";
        check(SdsToolCallParser.parse(trailingComma).isEmpty(), "trailing comma must be rejected");
        String trailingText = "<SDS:TOOL_CALL id=\"trailing-text\" name=\"workspace.write\">"
                + "{} garbage"
                + "</SDS:TOOL_CALL>";
        check(SdsToolCallParser.parse(trailingText).isEmpty(), "trailing JSON text must be rejected");
        String deep = "{".repeat(27) + "} ".repeat(27);
        String tooDeep = "<SDS:TOOL_CALL id=\"deep\" name=\"workspace.write\">"
                + deep + "</SDS:TOOL_CALL>";
        check(SdsToolCallParser.parse(tooDeep).isEmpty(), "excessive JSON depth must be rejected");
    }

    private static void testPerResponseCallLimit() {
        SdsAgentHooks.clearPendingCalls();
        SdsAgentHooks.setEnabled(true);
        StringBuilder response = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            response.append("<SDS:TOOL_CALL id=\"c").append(i)
                    .append("\" name=\"workspace.read\">{\"path\":\"file")
                    .append(i).append("\"}</SDS:TOOL_CALL>");
        }
        SdsAgentHooks.observeResponse(response.toString());
        check(SdsAgentHooks.pendingCount() == SdsToolCallParser.MAX_CALLS_PER_RESPONSE,
                "per-response call cap must be enforced");
        SdsAgentHooks.setEnabled(false);
    }

    private static void testWorkspaceConfinement() throws Exception {
        File parent = Files.createTempDirectory("sds-agent-test").toFile();
        File workspace = new File(parent, "workspace");
        check(workspace.mkdirs(), "workspace create failed");
        File inside = SdsWorkspaceGuard.resolveWithin(workspace, "notes/one.txt");
        check(inside.getPath().startsWith(workspace.getCanonicalPath()), "valid path escaped workspace");
        boolean rejected = false;
        try { SdsWorkspaceGuard.resolveWithin(workspace, "../../outside.txt"); }
        catch (SecurityException expected) { rejected = true; }
        check(rejected, "path traversal must be rejected");
        rejected = false;
        try { SdsWorkspaceGuard.resolveWithin(workspace, "/absolute/path"); }
        catch (SecurityException expected) { rejected = true; }
        check(rejected, "absolute paths must be rejected");
        deleteTree(parent);
    }

    private static void deleteTree(File file) {
        if (!file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }

    private static void testObserverDiagnostics() {
        SdsObserverDiagnostics diagnostics = SdsObserverDiagnostics.get();
        diagnostics.reset();
        SdsAgentHooks.setEnabled(false);
        // The observer metrics are recorded even when proposal mode is off, so a
        // device test can prove that the hook fires.
        SdsAgentHooks.observeResponse("hello");
        check(diagnostics.getCallbacks() == 1, "the observer metric must count the callback");
        SdsAgentHooks.observeResponse("hello world");
        check(diagnostics.getGrowthEvents() == 1, "growing content must be counted");
        SdsAgentHooks.observeResponse("hello world");
        check(diagnostics.getDuplicateCallbacks() == 1, "a repeated content must be counted");
        check(diagnostics.getDistinctContents() == 2, "distinct content must be counted");
        check(diagnostics.getLastContentChars() == 11, "the last length must be reported");
        check(!diagnostics.summary().contains("hello"), "the diagnostics must not store content");
        check(diagnostics.getThreads().size() == 1, "the calling thread must be recorded");
    }

    private static void testAgentSessionTransitions() {
        SdsAgentSession session = new SdsAgentSession();
        check(session.getState() == SdsAgentSession.State.IDLE, "a new session must be idle");
        SdsToolCall call = new SdsToolCall("call-1", "workspace.list", "{\"path\":\".\"}");
        check(session.onProposal(call), "a proposal must be accepted");
        check(session.getState() == SdsAgentSession.State.COLLECTING, "the state must be collecting");
        check(!session.approve("other-id"), "an unknown call must not be approved");
        check(session.approve("call-1"), "the proposal must be approvable");
        check(session.getState() == SdsAgentSession.State.AWAITING_APPROVAL, "the state must await approval");
        check(session.onRunStarted(), "the run must start after the approval");
        check(session.getState() == SdsAgentSession.State.RUNNING, "the state must be running");
        check(!session.onRunStarted(), "the run must not start twice");
        check(session.onRunSucceeded(new SdsToolResult(SdsToolResult.Status.SUCCESS, "ok", "")),
                "the run must be able to succeed");
        check(session.getState() == SdsAgentSession.State.COMPLETED, "the state must be completed");
        check(!session.getEvents().isEmpty(), "the timeline must record the activity");
        check(session.requestStop() == false, "a finished session must not be stoppable");

        SdsAgentSession other = new SdsAgentSession();
        other.onProposal(call);
        check(other.deny("call-1"), "the proposal must be deniable");
        check(other.getState() == SdsAgentSession.State.DENIED, "the state must be denied");
        check(other.requestStop() == false, "a denied session must not be stoppable");

        SdsAgentSession stopped = new SdsAgentSession();
        stopped.onProposal(call);
        stopped.approve("call-1");
        check(stopped.requestStop(), "the stop control must work");
        check(stopped.getState() == SdsAgentSession.State.CANCELLED, "the state must be cancelled");
        check(stopped.getCancellation().isCancelled(), "the stop control must set the cancellation");
    }

    private static void testStrictToolArguments() {
        SdsToolArguments arguments = SdsToolArguments.parse("{\"path\":\"a.txt\",\"depth\":2,\"flag\":true}");
        check("a.txt".equals(arguments.getString("path", "")), "a string argument must be read");
        check(arguments.getInt("depth", 0) == 2, "an integer argument must be read");
        check(arguments.getBoolean("flag", false), "a boolean argument must be read");
        boolean rejected = false;
        try { arguments.requireOnly("path"); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "an unknown argument must be rejected");
        rejected = false;
        try { SdsToolArguments.parse("{\"a\":{\"b\":1}}"); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "nested objects must be rejected");
        rejected = false;
        try { SdsToolArguments.parse("{\"a\":[1,2]}"); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "nested arrays must be rejected");
        rejected = false;
        try { SdsToolArguments.parse("{\"a\":1} trailing"); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "trailing data must be rejected");
    }

    private static void testToolRegistryAllowList() {
        SdsToolRegistry registry = SdsWorkspaceTools.createRegistry();
        check(registry.contains(SdsWorkspaceTools.LIST), "the list tool must be registered");
        check(registry.contains(SdsWorkspaceTools.READ), "the read tool must be registered");
        check(registry.contains(SdsWorkspaceTools.WRITE), "the write tool must be registered");
        check(!registry.contains("shell.exec"), "an unknown tool must not resolve");
        check(registry.find("shell.exec") == null, "an unknown tool must not be found");
        boolean rejected = false;
        try {
            registry.register(new SdsWorkspaceTools.ListTool());
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, "a duplicate tool name must be rejected");
    }

    private static void testExecutorRequiresApproval() {
        SdsToolRegistry registry = SdsWorkspaceTools.createRegistry();
        SdsToolExecutor executor = new SdsToolExecutor(registry, SdsExecutionPolicy.SAFE_DEFAULT);
        File workspace = new File(System.getProperty("java.io.tmpdir"), "sds-approval-test");
        workspace.mkdirs();
        SdsToolContext context = new SdsToolContext(workspace, SdsExecutionPolicy.SAFE_DEFAULT, new SdsCancellation());
        SdsToolResult result = executor.run(
                new SdsToolCall("call-1", SdsWorkspaceTools.LIST, "{\"path\":\".\"}"), context, false);
        check(result.getStatus() == SdsToolResult.Status.APPROVAL_REQUIRED,
                "a tool must not run without an approval");
        executor.shutdown();
    }

    private static void testExecutorRunsApprovedTool() throws Exception {
        SdsToolRegistry registry = SdsWorkspaceTools.createRegistry();
        SdsToolExecutor executor = new SdsToolExecutor(registry, SdsExecutionPolicy.SAFE_DEFAULT);
        File parent = Files.createTempDirectory("sds-exec-test").toFile();
        File workspace = new File(parent, "workspace");
        check(workspace.mkdirs(), "workspace create failed");
        SdsToolContext context = new SdsToolContext(workspace, SdsExecutionPolicy.SAFE_DEFAULT, new SdsCancellation());
        SdsToolResult written = executor.run(new SdsToolCall("call-1", SdsWorkspaceTools.WRITE,
                "{\"path\":\"notes.txt\",\"content\":\"hello\"}"), context, true);
        check(written.getStatus() == SdsToolResult.Status.SUCCESS, "the approved write must succeed");
        SdsToolResult read = executor.run(new SdsToolCall("call-2", SdsWorkspaceTools.READ,
                "{\"path\":\"notes.txt\"}"), context, true);
        check("hello".equals(read.getOutput()), "the read must return the written content");
        SdsToolResult listed = executor.run(new SdsToolCall("call-3", SdsWorkspaceTools.LIST,
                "{\"path\":\".\"}"), context, true);
        check(listed.getOutput().contains("notes.txt"), "the listing must contain the file");
        executor.shutdown();
        deleteTree(parent);
    }

    private static void testExecutorRefusesUnregisteredTool() {
        SdsToolRegistry registry = SdsWorkspaceTools.createRegistry();
        SdsToolExecutor executor = new SdsToolExecutor(registry, SdsExecutionPolicy.SAFE_DEFAULT);
        File workspace = new File(System.getProperty("java.io.tmpdir"), "sds-unknown-test");
        workspace.mkdirs();
        SdsToolContext context = new SdsToolContext(workspace, SdsExecutionPolicy.SAFE_DEFAULT, new SdsCancellation());
        SdsToolResult result = executor.run(
                new SdsToolCall("call-1", "shell.exec", "{\"command\":\"rm -rf /\"}"), context, true);
        check(result.getStatus() == SdsToolResult.Status.FAILED,
                "an unregistered tool must be refused");
        check(result.getSafeError().contains("not registered"),
                "the refusal must name the reason");
        executor.shutdown();
    }

    private static void testExecutorRefusesNetworkTool() {
        SdsToolRegistry registry = new SdsToolRegistry();
        registry.register(new SdsTool() {
            @Override public String getName() { return "web.fetch"; }
            @Override public String getDescription() { return "test tool"; }
            @Override public String getSchema() { return "{}"; }
            @Override public boolean isReadOnly() { return true; }
            @Override public boolean requiresNetwork() { return true; }
            @Override
            public SdsToolResult execute(SdsToolContext context, SdsToolCall call) {
                return new SdsToolResult(SdsToolResult.Status.SUCCESS, "fetched", "");
            }
        });
        SdsToolExecutor executor = new SdsToolExecutor(registry, SdsExecutionPolicy.SAFE_DEFAULT);
        File workspace = new File(System.getProperty("java.io.tmpdir"), "sds-network-test");
        workspace.mkdirs();
        SdsToolContext context = new SdsToolContext(workspace, SdsExecutionPolicy.SAFE_DEFAULT, new SdsCancellation());
        SdsToolResult result = executor.run(
                new SdsToolCall("call-1", "web.fetch", "{}"), context, true);
        check(result.getStatus() == SdsToolResult.Status.FAILED,
                "a network tool must be refused while the network is disabled");
        check(result.getSafeError().contains("network"),
                "the refusal must name the network policy");
        executor.shutdown();
    }

    private static void testExecutorTimeoutAndCancel() throws Exception {
        SdsToolRegistry registry = new SdsToolRegistry();
        registry.register(new SdsTool() {
            @Override public String getName() { return "slow.tool"; }
            @Override public String getDescription() { return "test tool"; }
            @Override public String getSchema() { return "{}"; }
            @Override public boolean isReadOnly() { return true; }
            @Override public boolean requiresNetwork() { return false; }
            @Override
            public SdsToolResult execute(SdsToolContext context, SdsToolCall call) throws Exception {
                Thread.sleep(30_000L);
                return new SdsToolResult(SdsToolResult.Status.SUCCESS, "late", "");
            }
        });
        SdsExecutionPolicy shortPolicy = new SdsExecutionPolicy(true, 1_000L, 1_024, false, 1);
        SdsToolExecutor executor = new SdsToolExecutor(registry, shortPolicy);
        File workspace = new File(System.getProperty("java.io.tmpdir"), "sds-timeout-test");
        workspace.mkdirs();
        SdsToolContext context = new SdsToolContext(workspace, shortPolicy, new SdsCancellation());
        long started = System.currentTimeMillis();
        SdsToolResult result = executor.run(new SdsToolCall("call-1", "slow.tool", "{}"), context, true);
        long elapsed = System.currentTimeMillis() - started;
        check(result.getStatus() == SdsToolResult.Status.TIMED_OUT, "a slow tool must time out");
        check(elapsed < 20_000L, "the timeout must interrupt the tool");

        SdsCancellation cancellation = new SdsCancellation();
        cancellation.cancel();
        SdsToolContext cancelled = new SdsToolContext(workspace, shortPolicy, cancellation);
        SdsToolResult cancelledResult = executor.run(
                new SdsToolCall("call-2", "slow.tool", "{}"), cancelled, true);
        check(cancelledResult.getStatus() == SdsToolResult.Status.CANCELLED,
                "a cancelled run must not start the tool");
        executor.shutdown();
    }

    private static void testWorkspaceToolsStayInsideTheWorkspace() throws Exception {
        File parent = Files.createTempDirectory("sds-workspace-tool-test").toFile();
        File workspace = new File(parent, "workspace");
        check(workspace.mkdirs(), "workspace create failed");
        SdsToolContext context = new SdsToolContext(workspace, SdsExecutionPolicy.SAFE_DEFAULT, new SdsCancellation());
        SdsToolRegistry registry = SdsWorkspaceTools.createRegistry();
        SdsToolResult escaped = registry.find(SdsWorkspaceTools.READ).execute(context,
                new SdsToolCall("call-1", SdsWorkspaceTools.READ, "{\"path\":\"../secret.txt\"}"));
        check(escaped.getStatus() == SdsToolResult.Status.FAILED,
                "a path outside the workspace must be refused");
        SdsToolResult absolute = registry.find(SdsWorkspaceTools.READ).execute(context,
                new SdsToolCall("call-2", SdsWorkspaceTools.READ, "{\"path\":\"/etc/hosts\"}"));
        check(absolute.getStatus() == SdsToolResult.Status.FAILED,
                "an absolute path must be refused");
        SdsToolResult unknownArgument = registry.find(SdsWorkspaceTools.READ).execute(context,
                new SdsToolCall("call-3", SdsWorkspaceTools.READ, "{\"path\":\"a\",\"command\":\"ls\"}"));
        check(unknownArgument.getStatus() == SdsToolResult.Status.FAILED,
                "an argument outside the schema must be refused");
        deleteTree(parent);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
