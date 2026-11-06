# Native Agent Runtime Foundation

## Implemented

- SdsExecutionPolicy: approval-first defaults, bounded timeouts/output, network disabled by default, and a per-turn tool-call cap.
- SdsWorkspaceGuard: canonical-path checks that reject absolute paths and traversal outside the app-private workspace. This is best-effort validation, not a kernel sandbox.
- SdsToolCall: immutable envelope for untrusted model proposals.
- SdsToolCallParser: strict, bounded parser for SDS tool-call envelopes containing one JSON object.
- SdsAgentHooks: opt-in request/response adapters connected by the experimental Morphe patch. Agent mode is disabled by default; response proposals are held in a bounded in-memory queue.
- NativeAgentHooksPatch: default-off patch inserting two narrow calls into the inspected DeepSeek 2.7.4 prompt serializer and response-content deserializer.
- SdsAgentConsoleActivity: a separate native launcher Activity to toggle proposal mode and review, copy or dismiss queued proposals. No command execution or approval-to-run action is present.
- Device-free Java regression tests for policy defaults, parser validation, queue caps, snapshot/dismiss, opt-in prompt handling and workspace path validation.

## Explicitly not implemented

The response hook observes a deserialized content field. It is not a verified incremental SSE callback or Compose renderer hook. The console is a separate launcher Activity, not yet an entry embedded in the main chat UI. The current branch does not execute shell commands, invoke MCP tools, send tool results back to DeepSeek, or expose a reliable Stop control. The proposal queue is in-memory and the Agent mode switch is session-only.

Therefore, this is a native proposal-review milestone, not a complete multi-turn agent.

## Required next integration order

1. Obtain a stable copy of the exact inspected APK and complete Morphe patch-apply verification (the live latest-download URL no longer serves the pinned APK).
2. Trace the actual response stream callback and Compose message-rendering lifecycle for the exact app version.
3. Replace the deserializer observer with a verified callback only after proving it does not execute on partial/chunked values or duplicate events.
4. Move the proposal console into a supported in-app navigation entry after the real Compose surface is mapped.
5. Add a native approval UI that records Approve / Deny / Cancel decisions with a visible event log.
6. Implement only registered tools with per-tool schemas, timeout, cancellation, bounded output, workspace confinement and explicit network policy.
7. Feed results back only through a verified supported user-message flow; never spoof or rewrite login/session traffic.
8. Test stop, timeout, process death, rotation, denied permissions and malformed streams on a real device.

## Security caveat

Nothing in this extension silently executes a proposal. PRoot, if added later, runs under the app UID and is not a kernel-level security boundary. Any future executor must require explicit user approval by default.
