package com.themuhammad.deepseekmod.extension;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

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
        testMemoryStore();
        testProjectRegistry();
        testSlashCommands();
        testPromptExpansion();
        testArtifactStore();
        testArtifactTools();
        testGenerativeUi();
        testLinuxStudio();
        testStrictJsonReader();
        testMcpConfiguration();
        testNetworkToolsStayRefused();
        testToolResultHandoff();
        testStopDoesNotPoisonTheNextTurn();
        testDiagnosticsStayBounded();
        testStoresWriteAtomically();
        testMcpHandshake();
        testRootfsInstallation();
        testApproveProposalIsAtomic();
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

    private static void writeFile(File file, String content) throws Exception {
        java.io.FileWriter writer = new java.io.FileWriter(file);
        try {
            writer.write(content);
        } finally {
            writer.close();
        }
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void testStopDoesNotPoisonTheNextTurn() {
        SdsAgentSession session = new SdsAgentSession();
        check(session.onProposal(new SdsToolCall("call-1", "workspace.list", "{}")),
                "the proposal must be accepted");
        check(session.approve("call-1"), "the proposal must be approved");
        check(session.onRunStarted(), "the run must start");
        check(session.requestStop(), "the stop must be accepted");
        check(session.getCancellation().isCancelled(), "the cancellation must be set");
        check(session.getState() == SdsAgentSession.State.CANCELLED, "the state must be cancelled");

        // A new proposal is a new turn, so the old cancellation must not stop it.
        check(session.onProposal(new SdsToolCall("call-2", "workspace.list", "{}")),
                "the next proposal must be accepted");
        check(!session.getCancellation().isCancelled(),
                "the cancellation of the previous turn must not stop the next one");
    }

    private static void testDiagnosticsStayBounded() {
        SdsObserverDiagnostics diagnostics = new SdsObserverDiagnostics();
        for (int index = 0; index < 800; index++) {
            diagnostics.onResponse("a growing answer " + index);
        }
        check(diagnostics.getCallbacks() == 800L, "every callback must be counted");
        check(diagnostics.getMaxContentChars() > 0, "the maximum length must be recorded");
        check(diagnostics.getEvents().size() <= SdsObserverDiagnostics.MAX_EVENTS,
                "the event ring must stay bounded");
        check(diagnostics.getThreads().size() <= SdsObserverDiagnostics.MAX_THREADS,
                "the thread list must stay bounded");
    }

    private static void testToolResultHandoff() {
        SdsToolResultHandoff.setEnabled(false);
        SdsToolResultHandoff.clearPendingResult();
        SdsToolResultHandoff.setPendingResult("the output of the tool");

        // While the handback is off, the content is never touched.
        String untouched = SdsToolResultHandoff.applyTo("the answer of the model");
        check("the answer of the model".equals(untouched),
                "the content must stay untouched while the handback is off");
        check(SdsToolResultHandoff.hasPendingResult(),
                "the result must still wait while the handback is off");

        SdsToolResultHandoff.setEnabled(true);
        String first = SdsToolResultHandoff.applyTo("the answer of the model");
        check(first.contains("the answer of the model"), "the content must be kept");
        check(first.contains("the output of the tool"), "the result must be added");
        check(first.contains(SdsToolResultHandoff.MARKER), "the result must be marked");
        check(!SdsToolResultHandoff.hasPendingResult(), "the result must not wait twice");

        // A second callback must not add the result again.
        String second = SdsToolResultHandoff.applyTo(first);
        check(second.equals(first), "the result must not be added twice");

        SdsToolResultHandoff.setPendingResult("another output");
        String third = SdsToolResultHandoff.applyTo(first);
        check(third.equals(first), "a marked content must not be changed again");

        SdsToolResultHandoff.setEnabled(false);
        SdsToolResultHandoff.clearPendingResult();
    }

    private static void testStrictJsonReader() {
        Map<String, Object> object = SdsJson.parseObject(
                "{\"name\":\"value\",\"count\":3,\"flag\":true,\"nothing\":null}");
        check("value".equals(SdsJson.optString(object, "name")), "a string must be read");
        check(SdsJson.asLong(object.get("count")) == 3L, "a number must be read");
        check(Boolean.TRUE.equals(object.get("flag")), "a boolean must be read");
        check(object.get("nothing") == null, "a null must be read");
        check(SdsJson.optString(object, "missing") == null, "a missing key must be null");

        List<Object> array = SdsJson.parseArray("[1, \"two\", {\"three\": true}]");
        check(array.size() == 3, "the array must have three entries");
        check(SdsJson.asObject(array.get(2)) != null, "a nested object must be read");

        check("line\nbreak".equals(SdsJson.optString(
                SdsJson.parseObject("{\"text\":\"line\\nbreak\"}"), "text")),
                "an escape must be decoded");
        check("\\u0041".equals(SdsJson.optString(
                SdsJson.parseObject("{\"text\":\"\\\\u0041\"}"), "text")),
                "a backslash must be kept");

        String[] broken = {
                "{\"a\":1,}",
                "{\"a\":1}{\"b\":2}",
                "{a:1}",
                "[1,2",
                "\"unclosed",
                "{\"a\":1,\"a\":2}",
                "nope",
        };
        for (String document : broken) {
            boolean refused = false;
            try {
                SdsJson.parse(document);
            } catch (IllegalArgumentException expected) {
                refused = true;
            }
            check(refused, "a broken document must be refused: " + document);
        }

        // The writer and the reader have to agree.
        Map<String, Object> source = new java.util.LinkedHashMap<>();
        source.put("name", "value");
        source.put("count", 3L);
        source.put("flag", Boolean.TRUE);
        source.put("list", java.util.Arrays.asList("a", "b"));
        Map<String, Object> round = SdsJson.parseObject(SdsJsonWriter.write(source));
        check("value".equals(SdsJson.optString(round, "name")), "the round trip must keep the string");
        check(SdsJson.asLong(round.get("count")) == 3L, "the round trip must keep the number");
        check(SdsJson.optArray(round, "list").size() == 2, "the round trip must keep the list");

        // A number without a JSON form must not break the document.
        Map<String, Object> notANumber = new java.util.LinkedHashMap<>();
        notANumber.put("value", Double.NaN);
        Map<String, Object> repaired = SdsJson.parseObject(SdsJsonWriter.write(notANumber));
        check(repaired.get("value") == null, "a number without a JSON form must become null");
    }

    private static void testMcpConfiguration() throws Exception {
        File parent = Files.createTempDirectory("sds-mcp-test").toFile();
        File file = new File(parent, "mcp.txt");
        SdsMcpConfigStore store = new SdsMcpConfigStore(file);
        check(store.size() == 0, "a new list must be empty");
        SdsMcpConfigStore.Server server = store.add("local", "https://127.0.0.1:9000/mcp", "secret");
        check("local".equals(server.getName()), "the name must be kept");
        check("mcp.local.".equals(server.toolPrefix()), "the tool prefix must be derived");
        check(!server.isEnabled(), "a new server must be switched off");
        check(store.enabledServers().isEmpty(), "a switched off server must not be used");

        boolean refused = false;
        try { store.add("plain", "http://example.org/mcp", ""); } catch (IllegalArgumentException expected) {
            refused = true;
        }
        check(refused, "a server without https must be refused");
        refused = false;
        try { store.add("local", "https://example.org/mcp", ""); } catch (IllegalStateException expected) {
            refused = true;
        }
        check(refused, "a duplicate server must be refused");

        check(store.setEnabled("local", true), "the server must be switchable");
        check(store.enabledServers().size() == 1, "the switched on server must be listed");

        SdsMcpConfigStore reopened = new SdsMcpConfigStore(file);
        check(reopened.size() == 1, "the list must survive a restart");
        check(reopened.find("local") != null, "the server must be found after a restart");
        check(reopened.find("local").getToken().equals("secret"), "the token must survive a restart");

        // The tools of a server join the allow-list under a prefixed name.
        SdsMcpTools.rememberTool("local", "search");
        SdsToolRegistry registry = SdsMcpTools.createRegistry(reopened);
        check(registry.contains("mcp.local.search"), "the tool of the server must be registered");
        SdsTool tool = registry.find("mcp.local.search");
        check(tool.requiresNetwork(), "the tool of a server must need the network");
        check(!tool.isReadOnly(), "the tool of a server must not be read-only");

        check(reopened.remove("local"), "the server must be removable");
        check(reopened.size() == 0, "the server must be gone");
        deleteTree(parent);
    }

    private static void testNetworkToolsStayRefused() throws Exception {
        File parent = Files.createTempDirectory("sds-network-test").toFile();
        File workspace = new File(parent, "workspace");
        check(workspace.mkdirs(), "workspace create failed");
        SdsToolRegistry registry = SdsResearchTools.createRegistry();
        check(registry.contains(SdsResearchTools.FETCH), "the fetch tool must be registered");
        SdsTool tool = registry.find(SdsResearchTools.FETCH);
        check(tool.requiresNetwork(), "the fetch tool must need the network");
        check(!tool.isReadOnly(), "the fetch tool must not be read-only");

        SdsExecutionPolicy safe = SdsExecutionPolicy.SAFE_DEFAULT;
        check(!safe.isNetworkAllowed(), "the network must be off by default");
        SdsToolExecutor executor = new SdsToolExecutor(registry, safe);
        try {
            SdsToolResult refused = executor.run(
                    new SdsToolCall("call-1", SdsResearchTools.FETCH, "{\"url\":\"https://example.org/\"}"),
                    new SdsToolContext(workspace, safe, new SdsCancellation()), true);
            check(refused.getStatus() == SdsToolResult.Status.FAILED,
                    "a network tool must be refused while the network is off");
        } finally {
            executor.shutdown();
        }

        // The plain text extraction must not keep any markup.
        String text = SdsHttpFetcher.toPlainText(
                "<html><head><script>alert(1)</script><style>a{}</style></head>"
                        + "<body><p>Hello &amp; welcome</p><br><script>bad()</script></body></html>", 500);
        check(!text.contains("<"), "the markup must be removed");
        check(!text.contains("alert(1)"), "the script must be removed");
        check(!text.contains("bad()"), "the second script must be removed");
        check(text.contains("Hello & welcome"), "the entities must be decoded");
        deleteTree(parent);
    }

    private static void testLinuxStudio() throws Exception {
        File parent = Files.createTempDirectory("sds-studio-test").toFile();
        File nativeLibraryDirectory = new File(parent, "lib");
        check(nativeLibraryDirectory.mkdirs(), "native library directory create failed");
        File filesDirectory = new File(parent, "files");
        check(filesDirectory.mkdirs(), "files directory create failed");

        SdsSandboxManager manager = new SdsSandboxManager(nativeLibraryDirectory, filesDirectory);
        check(!manager.isRuntimeAvailable(), "a runtime that is not bundled must not be available");
        check(!manager.isRootfsReady(), "a root filesystem that is not there must not be ready");
        check(!manager.isReady(), "the studio must not be ready without a runtime");
        check(manager.describe().contains("not bundled"), "the state must be described");
        check(SdsSandboxConfig.ROOTFS_SHA256.length() == 64, "the root filesystem must be pinned");

        // The runtime needs every one of its files, not only the launcher.
        File launcher = new File(nativeLibraryDirectory, SdsSandboxConfig.LAUNCHER_NAME);
        writeFile(launcher, "#!/bin/sh\n");
        launcher.setExecutable(true);
        SdsSandboxManager withLauncher = new SdsSandboxManager(nativeLibraryDirectory, filesDirectory);
        check(!withLauncher.isRuntimeAvailable(),
                "a runtime with only the launcher must not be used");
        check(withLauncher.findLauncher() != null, "the launcher must be found");
        for (String name : SdsSandboxConfig.RUNTIME_FILES) {
            writeFile(new File(nativeLibraryDirectory, name), "not the real runtime");
        }
        SdsSandboxManager complete = new SdsSandboxManager(nativeLibraryDirectory, filesDirectory);
        check(complete.isRuntimeAvailable(), "the whole runtime must be found");
        check(!complete.isRuntimeIntact(),
                "a runtime whose checksum does not match must not be used");
        check(!complete.isReady(), "a runtime that is not intact must not be ready");
        check(complete.describe().contains("checksum"),
                "the state must name the checksum that does not match");

        File rootfs = withLauncher.getRootfsDirectory();
        check(rootfs.mkdirs(), "rootfs directory create failed");
        writeFile(new File(rootfs, SdsSandboxConfig.ROOTFS_MARKER),
                SdsSandboxConfig.ROOTFS_SHA256 + "\n");
        SdsSandboxManager ready = new SdsSandboxManager(nativeLibraryDirectory, filesDirectory);
        check(ready.isRootfsReady(), "the verified root filesystem must be ready");
        check(!ready.isReady(),
                "the studio must not be ready while the runtime is not intact");

        // A root filesystem with a wrong checksum must not be accepted.
        writeFile(new File(rootfs, SdsSandboxConfig.ROOTFS_MARKER), "0".repeat(64) + "\n");
        SdsSandboxManager wrong = new SdsSandboxManager(nativeLibraryDirectory, filesDirectory);
        check(!wrong.isRootfsReady(), "a wrong checksum must be refused");

        // The tool refuses to run without the runtime.
        SdsRuntimeRegistry.setSandboxManager(wrong);
        SdsToolRegistry registry = SdsSandboxTool.createRegistry();
        check(registry.contains(SdsSandboxTool.RUN), "the sandbox tool must be registered");
        SdsTool tool = registry.find(SdsSandboxTool.RUN);
        check(!tool.isReadOnly(), "the sandbox tool must not be read-only");
        check(!tool.requiresNetwork(), "the sandbox tool must not need the network");
        File workspace = new File(parent, "workspace");
        check(workspace.mkdirs(), "workspace create failed");
        SdsToolContext context = new SdsToolContext(workspace, SdsExecutionPolicy.SAFE_DEFAULT,
                new SdsCancellation());
        SdsToolResult refused = tool.execute(context,
                new SdsToolCall("call-1", SdsSandboxTool.RUN, "{\"command\":\"uname -a\"}"));
        check(refused.getStatus() == SdsToolResult.Status.FAILED, "a missing runtime must be refused");

        SdsToolResult badArguments = tool.execute(context,
                new SdsToolCall("call-2", SdsSandboxTool.RUN, "{\"command\":\"uname -a\",\"x\":1}"));
        check(badArguments.getStatus() == SdsToolResult.Status.FAILED,
                "an unknown argument must be refused");
        SdsRuntimeRegistry.setSandboxManager(null);
        deleteTree(parent);
    }

    private static void testApproveProposalIsAtomic() {
        SdsAgentSession session = new SdsAgentSession();
        SdsToolCall first = new SdsToolCall("call-1", "workspace.list", "{}");
        SdsToolCall second = new SdsToolCall("call-2", "workspace.read", "{\"path\":\"a.txt\"}");

        check(session.approveProposal(first), "the first proposal must be approved");
        check(session.getPendingCallId().equals("call-1"),
                "the approved proposal must be the one of the session");
        check(session.getState() == SdsAgentSession.State.AWAITING_APPROVAL,
                "the session must wait for the run");

        // A second proposal must not replace the approved one while it waits.
        check(!session.approveProposal(second),
                "a proposal must not be approved while another one waits for its run");

        // After the run the session may take the next proposal.
        session.onRunStarted();
        session.onRunFailed(new SdsToolResult(SdsToolResult.Status.FAILED, "", "no"));
        check(session.approveProposal(second), "the next proposal must be approved after the run");
        check(session.getPendingCallId().equals("call-2"),
                "the approved proposal must be the one the user chose");
    }

    private static void testRootfsInstallation() throws Exception {
        File parent = Files.createTempDirectory("sds-rootfs-test").toFile();
        File filesDirectory = new File(parent, "files");
        check(filesDirectory.mkdirs(), "files directory create failed");
        SdsSandboxManager manager = new SdsSandboxManager(new File(parent, "lib"), filesDirectory);

        // The checksum of a well known value.
        check("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
                        .equals(SdsHash.sha256("abc".getBytes("UTF-8"))),
                "the checksum of a file has to be the known one");

        // An archive that is not the pinned one is refused and nothing is unpacked.
        File archive = manager.getRootfsArchive();
        writeFile(archive, "this is not the pinned archive");
        check(!manager.installRootfs(archive), "an archive with a wrong checksum must be refused");
        check(!manager.getRootfsDirectory().exists(),
                "a refused archive must not create the root filesystem");

        // A download over a plain address is refused before a connection is opened.
        boolean refused = false;
        try {
            SdsHttpFetcher.download("http://example.org/file", new File(parent, "file"),
                    1024L, 1000);
        } catch (java.io.IOException expected) {
            refused = true;
        }
        check(refused, "a download over http must be refused");

        refused = false;
        try {
            SdsHttpFetcher.download("https://example.org/file", null, 1024L, 1000);
        } catch (IllegalArgumentException | java.io.IOException expected) {
            refused = true;
        }
        check(refused, "a download without a target must be refused");

        deleteTree(parent);
    }

    private static void testGenerativeUi() throws Exception {
        File parent = Files.createTempDirectory("sds-ui-test").toFile();
        File workspace = new File(parent, "workspace");
        check(workspace.mkdirs(), "workspace create failed");
        SdsRuntimeRegistry.setWorkspace(workspace);
        SdsRuntimeRegistry.setLastUiSpec(null);

        SdsToolRegistry registry = SdsUiTools.createRegistry();
        check(registry.contains(SdsUiTools.RENDER), "the render tool must be registered");
        SdsTool render = registry.find(SdsUiTools.RENDER);
        check(render.isReadOnly(), "the render tool must be read-only");
        check(!render.requiresNetwork(), "the render tool must not need the network");

        SdsToolContext context = new SdsToolContext(workspace, SdsExecutionPolicy.SAFE_DEFAULT,
                new SdsCancellation());
        SdsToolResult rendered = render.execute(context, new SdsToolCall("call-1", SdsUiTools.RENDER,
                "{\"title\":\"Plan\",\"subtitle\":\"three steps\",\"items\":\"one\\ntwo\\nthree\","
                        + "\"code\":\"println(1)\",\"language\":\"kotlin\","
                        + "\"action\":\"Copy\",\"argument\":\"plan.txt\"}"));
        check(rendered.getStatus() == SdsToolResult.Status.SUCCESS, "the interface must be rendered");
        check(rendered.getOutput().contains("Plan"), "the result must mention the interface");

        SdsUiSpec spec = SdsRuntimeRegistry.getLastUiSpec();
        check(spec != null, "the interface must be stored");
        check("Plan".equals(spec.getTitle()), "the title must be kept");
        check(3 == spec.getItems().size(), "the items must be split by the line break");
        check(spec.hasAction(), "the action must be kept");
        check("plan.txt".equals(spec.getArgument()), "the argument must be kept");
        check("kotlin".equals(spec.getLanguage()), "the language must be kept");

        SdsToolResult empty = render.execute(context,
                new SdsToolCall("call-2", SdsUiTools.RENDER, "{}"));
        check(empty.getStatus() == SdsToolResult.Status.FAILED, "an empty interface must be refused");

        SdsToolResult refused = render.execute(context,
                new SdsToolCall("call-3", SdsUiTools.RENDER, "{\"html\":\"<script>\"}"));
        check(refused.getStatus() == SdsToolResult.Status.FAILED, "an unknown argument must be refused");

        // The bounds of the specification.
        StringBuilder longItems = new StringBuilder();
        for (int index = 0; index < 50; index++) longItems.append("item\n");
        SdsUiSpec bounded = SdsUiSpec.fromArguments("t", "s", "b", longItems.toString(), "", "text", "", "");
        check(bounded.getItems().size() <= SdsUiSpec.MAX_ITEMS, "the number of items must be bounded");
        check(bounded.getTitle().length() <= SdsUiSpec.MAX_TITLE_LENGTH, "the title must be bounded");
        deleteTree(parent);
    }

    private static void testArtifactStore() throws Exception {
        File parent = Files.createTempDirectory("sds-artifact-test").toFile();
        File file = new File(parent, "artifacts.txt");
        SdsArtifactStore store = new SdsArtifactStore(file);
        check(store.size() == 0, "a new artifact store must be empty");
        SdsArtifactStore.Artifact artifact = store.create("My snippet", "kotlin", "fun main() {}");
        check(store.size() == 1, "the artifact must be stored");
        check(artifact.getId().startsWith("a-"), "the artifact must have an id");
        check("kotlin".equals(artifact.getLanguage()), "the artifact language must be kept");
        check("fun main() {}".equals(artifact.getContent()), "the artifact content must be kept");
        check(artifact.getExportName().equals("My-snippet.kotlin"), "the export name must be safe");

        boolean rejected = false;
        try { store.create("", "text", "x"); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "an artifact without a title must be rejected");
        rejected = false;
        try { store.create("title", "text/plain", "x"); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "an artifact language with a slash must be rejected");

        SdsArtifactStore reopened = new SdsArtifactStore(file);
        check(reopened.size() == 1, "the artifact must survive a restart");
        check(reopened.find(artifact.getId()) != null, "the artifact must be found after a restart");
        check(reopened.delete(artifact.getId()), "the artifact must be removable");
        check(reopened.size() == 0, "the artifact must be gone");
        deleteTree(parent);
    }

    private static void testArtifactTools() throws Exception {
        File parent = Files.createTempDirectory("sds-artifact-tool-test").toFile();
        File workspace = new File(parent, "workspace");
        check(workspace.mkdirs(), "workspace create failed");
        SdsRuntimeRegistry.setWorkspace(workspace);
        SdsRuntimeRegistry.setArtifactStore(new SdsArtifactStore(new File(parent, "artifacts.txt")));

        SdsToolRegistry registry = SdsArtifactTools.createRegistry();
        check(registry.contains(SdsArtifactTools.CREATE), "the create tool must be registered");
        check(registry.contains(SdsArtifactTools.LIST), "the list tool must be registered");
        check(registry.contains(SdsArtifactTools.READ), "the read tool must be registered");

        SdsToolContext context = new SdsToolContext(workspace, SdsExecutionPolicy.SAFE_DEFAULT,
                new SdsCancellation());
        SdsTool create = registry.find(SdsArtifactTools.CREATE);
        check(!create.isReadOnly(), "the create tool must not be read-only");
        check(!create.requiresNetwork(), "the create tool must not need the network");

        SdsToolResult created = create.execute(context,
                new SdsToolCall("call-1", SdsArtifactTools.CREATE,
                        "{\"title\":\"Report\",\"language\":\"markdown\",\"content\":\"# Report\"}"));
        check(created.getStatus() == SdsToolResult.Status.SUCCESS, "the artifact must be created");
        check(created.getOutput().contains("Report"), "the result must mention the artifact");

        SdsToolResult refused = create.execute(context,
                new SdsToolCall("call-2", SdsArtifactTools.CREATE,
                        "{\"title\":\"Report\",\"content\":\"x\",\"unknown\":1}"));
        check(refused.getStatus() == SdsToolResult.Status.FAILED, "an unknown argument must be refused");

        SdsTool list = registry.find(SdsArtifactTools.LIST);
        SdsToolResult listed = list.execute(context, new SdsToolCall("call-3", SdsArtifactTools.LIST, "{}"));
        check(listed.getStatus() == SdsToolResult.Status.SUCCESS, "the artifacts must be listed");
        check(listed.getOutput().contains("Report"), "the list must contain the artifact");

        SdsTool read = registry.find(SdsArtifactTools.READ);
        SdsToolResult missing = read.execute(context,
                new SdsToolCall("call-4", SdsArtifactTools.READ, "{\"id\":\"a-missing\"}"));
        check(missing.getStatus() == SdsToolResult.Status.FAILED, "an unknown artifact must fail");
        deleteTree(parent);
    }

    private static void testMemoryStore() throws Exception {
        File parent = Files.createTempDirectory("sds-memory-test").toFile();
        File file = new File(parent, "memory.txt");
        SdsMemoryStore store = new SdsMemoryStore(file);
        check(store.size() == 0, "a new memory store must be empty");
        store.put("name", "the user's name");
        store.put("plan", "build the native agent");
        check(store.size() == 2, "two memories must be stored");
        check("the user's name".equals(store.get("name")), "the memory must be readable");
        check(store.get("missing") == null, "an unknown memory must be null");
        check(store.remove("plan"), "a memory must be removable");
        check(store.size() == 1, "the memory must be removed");

        // The store has to survive a restart of the app.
        SdsMemoryStore reopened = new SdsMemoryStore(file);
        check(reopened.size() == 1, "the memory must survive a restart");
        check("the user's name".equals(reopened.get("name")), "the memory content must survive a restart");

        boolean rejected = false;
        try { store.put("bad key", "value"); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "a memory key with an unsupported character must be rejected");
        rejected = false;
        try { store.put("", "value"); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "an empty memory key must be rejected");
        rejected = false;
        try {
            StringBuilder oversized = new StringBuilder();
            for (int index = 0; index < SdsMemoryStore.MAX_VALUE_CHARS + 1; index++) oversized.append('x');
            store.put("big", oversized.toString());
        } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "an oversized memory value must be rejected");
        reopened.clear();
        check(reopened.size() == 0, "the memory store must be clearable");
        deleteTree(parent);
    }

    private static void testProjectRegistry() throws Exception {
        File parent = Files.createTempDirectory("sds-project-test").toFile();
        File file = new File(parent, "projects.txt");
        SdsProjectRegistry registry = new SdsProjectRegistry(file);
        check(registry.size() == 0, "a new project registry must be empty");
        SdsProjectRegistry.Project project = registry.create("My Project");
        check("My Project".equals(project.getName()), "the project name must be kept");
        check("project-my-project".equals(project.getFolder()), "the project folder must be derived");
        check(registry.size() == 1, "the project must be stored");
        boolean rejected = false;
        try { registry.create("My Project"); } catch (IllegalStateException expected) { rejected = true; }
        check(rejected, "a duplicate project must be rejected");
        rejected = false;
        try { registry.create("bad/name"); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "a project name with an unsupported character must be rejected");

        SdsProjectRegistry reopened = new SdsProjectRegistry(file);
        check(reopened.size() == 1, "the project must survive a restart");
        check(reopened.remove("My Project"), "the project must be removable");
        check(reopened.size() == 0, "the project must be gone");
        deleteTree(parent);
    }

    private static void testSlashCommands() {
        SdsRuntimeRegistry.ensureDefaults();
        check(SdsRuntimeRegistry.commandNames().contains("/help"), "the help command must exist");
        check(SdsRuntimeRegistry.commandNames().contains("/memory"), "the memory command must exist");
        check(SdsRuntimeRegistry.commandNames().contains("/projects"), "the projects command must exist");
        check(SdsSlashCommands.isCommand("/help"), "a slash command must be detected");
        check(!SdsSlashCommands.isCommand("hello"), "a plain text must not be a command");
        String expanded = SdsSlashCommands.expand("/help");
        check(expanded.contains("/help"), "the help command must list the commands");
        check("/unknown-command".equals(SdsSlashCommands.expand("/unknown-command")),
                "an unknown command must leave the prompt untouched");
        check("plain text".equals(SdsSlashCommands.expand("plain text")),
                "a plain prompt must stay untouched");
    }

    private static void testStoresWriteAtomically() throws Exception {
        File parent = Files.createTempDirectory("sds-atomic-test").toFile();

        File memoryFile = new File(parent, "memory.txt");
        SdsMemoryStore memory = new SdsMemoryStore(memoryFile);
        memory.put("language", "Bengali");
        check(memoryFile.isFile(), "the memory file must exist");
        check(!new File(parent, "memory.txt.sds-tmp").exists(),
                "the temporary file of a write must not stay behind");
        SdsMemoryStore reopenedMemory = new SdsMemoryStore(memoryFile);
        check("Bengali".equals(reopenedMemory.get("language")),
                "the memory must survive a restart");

        File artifactFile = new File(parent, "artifacts.txt");
        SdsArtifactStore artifacts = new SdsArtifactStore(artifactFile);
        String first = artifacts.create("one", "text", "a").getId();
        String second = artifacts.create("two", "text", "b").getId();
        String third = artifacts.create("three", "text", "c").getId();
        check(!first.equals(second) && !second.equals(third) && !first.equals(third),
                "two artifacts must not share an identifier");
        check(!new File(parent, "artifacts.txt.sds-tmp").exists(),
                "the temporary file of a write must not stay behind");
        SdsArtifactStore reopenedArtifacts = new SdsArtifactStore(artifactFile);
        check(reopenedArtifacts.size() == 3, "the artifacts must survive a restart");

        File projectFile = new File(parent, "projects.txt");
        SdsProjectRegistry projects = new SdsProjectRegistry(projectFile);
        projects.create("Report");
        check(!new File(parent, "projects.txt.sds-tmp").exists(),
                "the temporary file of a write must not stay behind");
        check(new SdsProjectRegistry(projectFile).size() == 1,
                "the projects must survive a restart");

        deleteTree(parent);
    }

    private static void testMcpHandshake() throws Exception {
        Map<String, Object> parameters = SdsMcpClient.buildInitializeParameters();
        check(SdsMcpClient.PREFERRED_PROTOCOL_VERSION.equals(parameters.get("protocolVersion")),
                "the handshake has to carry the version of the client");
        check(parameters.get("capabilities") instanceof Map, "the handshake needs the capabilities");
        Map<String, Object> clientInfo = SdsJson.asObject(parameters.get("clientInfo"));
        check(clientInfo != null && clientInfo.get("name") != null,
                "the handshake needs the name of the client");

        SdsMcpConfigStore.Server server = new SdsMcpConfigStore.Server(
                "local", "https://mcp.example.org/mcp", "token", false);
        SdsMcpClient client = new SdsMcpClient(server, null);
        check(!client.isInitialized(), "the client must not be initialized before the handshake");
        check(SdsMcpClient.PREFERRED_PROTOCOL_VERSION.equals(client.getProtocolVersion()),
                "the client speaks its preferred version before the handshake");

        Map<String, Object> answer = new java.util.LinkedHashMap<>();
        answer.put("protocolVersion", "2024-11-05");
        client.applyInitializeResult(answer);
        check("2024-11-05".equals(client.getProtocolVersion()),
                "the negotiated version of the server has to be used");

        boolean refused = false;
        try {
            client.applyInitializeResult(new java.util.LinkedHashMap<String, Object>());
        } catch (java.io.IOException expected) {
            refused = true;
        }
        check(refused, "a server without a version has to be refused");

        refused = false;
        Map<String, Object> broken = new java.util.LinkedHashMap<>();
        broken.put("protocolVersion", "not-a-version");
        try {
            client.applyInitializeResult(broken);
        } catch (java.io.IOException expected) {
            refused = true;
        }
        check(refused, "a server with an unknown version has to be refused");

        refused = false;
        Map<String, Object> tooOld = new java.util.LinkedHashMap<>();
        tooOld.put("protocolVersion", "2023-01-01");
        try {
            client.applyInitializeResult(tooOld);
        } catch (java.io.IOException expected) {
            refused = true;
        }
        check(refused, "a server with a version that is too old has to be refused");
    }

    private static void testPromptExpansion() throws Exception {
        File parent = Files.createTempDirectory("sds-prompt-test").toFile();
        File workspace = new File(parent, "workspace");
        check(workspace.mkdirs(), "workspace create failed");
        SdsRuntimeRegistry.setWorkspace(workspace);
        SdsRuntimeRegistry.setMemoryStore(new SdsMemoryStore(new File(parent, "memory.txt")));
        SdsRuntimeRegistry.setProjectRegistry(new SdsProjectRegistry(new File(parent, "projects.txt")));
        SdsRuntimeRegistry.ensureDefaults();
        SdsRuntimeRegistry.setIncludeMemoryInPrompt(false);

        SdsAgentHooks.clearPendingCalls();
        SdsAgentHooks.setEnabled(true);
        String plain = SdsAgentHooks.beforePrompt("hello");
        check(plain.startsWith("hello"), "a plain prompt must stay at the start");
        check(plain.contains("SDS:TOOL_CALL"), "the tool guidance must be added");

        String command = SdsAgentHooks.beforePrompt("/help");
        check(command.contains("available slash commands"),
                "a slash command must be expanded natively");

        SdsRuntimeRegistry.getMemoryStore().put("language", "Bengali");
        SdsRuntimeRegistry.setIncludeMemoryInPrompt(true);
        String withMemory = SdsAgentHooks.beforePrompt("hello");
        check(withMemory.contains("Stored memories:"), "the memories must be added when they are enabled");
        check(withMemory.contains("Bengali"), "the memory content must be added");
        SdsRuntimeRegistry.setIncludeMemoryInPrompt(false);
        SdsAgentHooks.setEnabled(false);
        deleteTree(parent);
    }
}
