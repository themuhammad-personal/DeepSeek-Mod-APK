# Native Agent Runtime Foundation

## Implemented in this branch

- `SdsExecutionPolicy`: approval-first defaults, bounded timeouts, bounded output, network disabled by default, and a per-turn tool-call cap.
- `SdsWorkspaceGuard`: canonical-path checks that reject absolute paths and traversal outside the app-private workspace. This is best-effort path validation, not a kernel sandbox.
- `SdsToolCall`: immutable envelope for an untrusted model proposal.
- `SdsToolCallParser`: strict, bounded parser for `<SDS:TOOL_CALL ...>{JSON object}</SDS:TOOL_CALL>` envelopes. It rejects malformed JSON, oversized payloads, invalid identifiers and excessive call counts.
- `SdsAgentHooks`: opt-in request/response adapters connected by the experimental Morphe patch. Agent mode is disabled by default. When enabled, requests may receive tool-format guidance and response proposals are placed in a bounded in-memory queue.
- `Native Agent hook points (experimental)`: a manually selected, default-off Morphe patch that inserts the two narrow static calls into the verified 2.7.4 prompt serializer and response-content deserializer.
- `scripts/test-agent-core.sh`: device-free Java regression tests for policy defaults, parser validation, queue limits, opt-in prompt handling and path confinement.

## Explicitly not implemented

The response hook is a content-deserializer observation point, **not** a verified incremental SSE callback or Compose renderer hook. The current branch does not execute shell commands, invoke MCP tools, display an approval screen, or send tool results back to DeepSeek. The proposal queue is in-memory and is not yet connected to a native UI. Do not describe this as a complete multi-turn agent.

## Required next integration order

1. Verify the exact stream callback and Compose message-rendering lifecycle for each supported app version.
2. Add a native approval UI that displays the exact tool name and arguments and records Approve / Deny / Cancel decisions.
3. Implement a registry of explicitly supported tools. Do not map arbitrary model-provided strings to `ProcessBuilder` or network calls.
4. Add per-command timeout, cancellation, output limits, workspace confinement and explicit network policy.
5. Feed tool results back only through a verified supported request flow; never spoof or rewrite login/session traffic.
6. Test stop, timeout, process death, rotation, denied permissions and malformed streams on a real device.

## Security caveat

Nothing in this extension silently executes a proposal. PRoot, if added later, runs under the app UID and is not a kernel-level security boundary. Any future executor must require explicit user approval by default.
