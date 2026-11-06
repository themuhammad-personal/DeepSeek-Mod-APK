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
        SdsToolCall call = SdsAgentHooks.pollPendingToolCall();
        check(call != null, "pending call missing");
        check("call-1".equals(call.getId()), "wrong call id");
        check("workspace.list".equals(call.getName()), "wrong tool name");
        check(call.getArgumentsJson().contains("\"depth\":2"), "arguments changed");
        check(SdsAgentHooks.pendingCount() == 0, "poll must remove proposal");

        SdsAgentHooks.clearPendingCalls();
        SdsAgentHooks.observeResponse(payload);
        check(SdsAgentHooks.snapshotPendingToolCalls().size() == 1,
                "snapshot should expose pending proposals without consuming them");
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

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
