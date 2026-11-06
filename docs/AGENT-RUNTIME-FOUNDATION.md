# Native Agent Runtime Foundation

## Implemented

- SdsExecutionPolicy: approval-first defaults, bounded timeouts/output, network disabled by default, and a per-turn tool-call cap.
- SdsWorkspaceGuard: canonical-path checks that reject absolute paths and traversal outside the app-private workspace. This is best-effort validation, not a kernel sandbox.
- SdsToolCall: immutable envelope for untrusted model proposals.
- SdsToolCallParser: strict, bounded parser for SDS tool-call envelopes containing one JSON object.
- SdsAgentHooks: opt-in request hook plus response observers connected by the experimental Morphe patch. Agent mode is disabled by default; complete-content proposals are held in a bounded in-memory queue.
- NativeAgentHooksPatch: default-off patch inserting a prompt hook into the inspected serializer and response observers into the inspected mutable streaming ResponseFragment SET/APPEND methods.
- SdsAgentConsoleActivity: a separate native launcher Activity to toggle proposal mode and review, copy or dismiss queued proposals. No command execution or approval-to-run action is present.
- Device-free Java regression tests for policy defaults, parser validation, queue caps, snapshot/dismiss, opt-in prompt handling and workspace path validation.

## Streaming trace

The supplied 2.7.4 static decode shows the response flow as:

SseMessageRoot → mutable response model → JSON delta SET/APPEND → mutable ResponseFragment.content.

The SET method decodes a replacement String and updates the content holder. The APPEND method reads the previous content, decodes the new chunk, concatenates them, and writes the complete accumulated String. The observer is inserted after these state-holder writes. Therefore the proposal parser receives complete content rather than only the newest delta.

See docs/DEEPSEEK-STREAM-TRACE.md for the obfuscated classes, methods and evidence chain.

## Explicitly not implemented

The stream path is statically traced but not yet runtime-verified on a physical device. Callback frequency, coroutine/thread context, lifecycle ordering and interaction with Compose rendering are still unknown. The Console is a separate launcher Activity, not yet an entry embedded in the main chat UI. The current branch does not execute shell commands, invoke MCP tools, send tool results back to DeepSeek, or expose a reliable Stop control. The proposal queue is in-memory and Agent mode is session-only.

Therefore, this is a native proposal-review milestone, not a complete multi-turn agent.

## Required next integration order

1. Obtain a stable copy of the exact inspected APK and complete Morphe patch-apply verification. The live latest-download URL currently no longer serves the pinned 2.7.4 sample.
2. Run the patched APK on a real device and prove the SET/APPEND hook fires with complete assistant content, without excessive duplicates or UI-thread stalls.
3. Trace the Compose navigation/message renderer and embed a Console entry into supported in-app navigation.
4. Add an approval UI that records Approve / Deny / Cancel decisions and shows a visible event log.
5. Implement only explicitly registered tools with per-tool schemas, timeout, cancellation, bounded output, workspace confinement and explicit network policy.
6. Feed results back only through a verified supported user-message flow; never spoof or rewrite login/session traffic.
7. Test stop, timeout, process death, rotation, denied permissions and malformed streams on a real device.

## Security caveat

Nothing in this extension silently executes a proposal. PRoot, if added later, runs under the app UID and is not a kernel-level security boundary. Any future executor must require explicit user approval by default.
