# Native Agent Runtime Foundation

## Implemented

### Proposal intake (static, build-verified)

- `SdsExecutionPolicy`: approval-first defaults, bounded timeouts/output, network disabled by default, and a per-turn tool-call cap.
- `SdsWorkspaceGuard`: canonical-path checks that reject absolute paths and traversal outside the app-private workspace. This is best-effort validation, not a kernel sandbox.
- `SdsToolCall`: immutable envelope for untrusted model proposals.
- `SdsToolCallParser`: strict, bounded parser for SDS tool-call envelopes containing one JSON object.
- `SdsAgentHooks`: opt-in request hook plus response observers connected by the experimental Morphe patch. Agent mode is disabled by default; complete-content proposals are held in a bounded in-memory queue.
- `NativeAgentHooksPatch`: default-off patch inserting a prompt hook into the inspected serializer and response observers into the inspected mutable streaming ResponseFragment SET/APPEND methods.

### Session and decisions (build-verified, not device-runtime-verified)

- `SdsAgentSession`: the state machine. It moves through `IDLE → COLLECTING → AWAITING_APPROVAL → RUNNING → COMPLETED`, with `DENIED`, `CANCELLED` and `FAILED` as the refusal and failure states. Illegal transitions are refused instead of silently applied.
- `SdsAgentEvent`: one entry of the activity timeline. The detail is short and free of assistant content.
- `SdsCancellation`: the flag the Stop control sets. A tool that runs for a long time has to check it, because the executor can only interrupt the thread.
- The session records the approve, deny and stop decisions in a bounded timeline of at most 64 entries.

### Tool execution (build-verified, not device-runtime-verified)

- `SdsToolRegistry`: the allow-list. A tool has to be registered explicitly before it can run. There is no dynamic lookup by class name, no reflection and no command execution, so a model cannot reach anything the app did not put into the registry.
- `SdsTool`: the tool contract. Every tool declares its name, its description, its exact argument schema, whether it only reads, and whether it needs the network.
- `SdsToolArguments`: a strict reader for **flat** JSON only. Nested objects and arrays are rejected, and `requireOnly` refuses every argument the tool does not declare.
- `SdsToolExecutor`: the only place a tool runs. It refuses an unregistered tool, refuses a network tool while the network policy forbids it, refuses to run without an explicit approval, bounds the output, and interrupts a tool that exceeds the timeout.
- `SdsWorkspaceTools`: `workspace.list`, `workspace.read` and `workspace.write`. They only touch the app-private workspace, they never run a command, they never use the network and every path goes through `SdsWorkspaceGuard`. A refused path or a mismatching argument becomes a failed result with a reason, not an exception.

### Observability (build-verified, not device-runtime-verified)

- `SdsObserverDiagnostics`: measures the response observer **without storing any assistant content**. It counts the callbacks, the distinct contents, the duplicated contents, the growing and shrinking contents, the first and last content length, and the threads the callbacks arrived on. The content digest is salted with a random value that is created once per process, so a digest cannot be used to look up a known text.
- The diagnostics are recorded even while proposal mode is off, so a device run can prove that the hook fires and that it receives the complete accumulated answer.

### Console (build-verified, not device-runtime-verified)

- `SdsAgentConsoleActivity`: a separate native launcher Activity that shows the session state, the approve, deny and stop actions, the activity timeline, the tool allow-list with the schema of every tool, the observer diagnostics and the proposal queue.

## Streaming trace

The supplied 2.7.4 static decode shows the response flow as:

SseMessageRoot → mutable response model → JSON delta SET/APPEND → mutable ResponseFragment.content.

The SET method decodes a replacement String and updates the content holder. The APPEND method reads the previous content, decodes the new chunk, concatenates them, and writes the complete accumulated String. The observer is inserted after these state-holder writes. Therefore the proposal parser receives complete content rather than only the newest delta.

See docs/DEEPSEEK-STREAM-TRACE.md for the obfuscated classes, methods and evidence chain.

## Safety analysis of the new runtime

| Property | How it is enforced |
| --- | --- |
| No silent execution | `SdsToolExecutor` returns `APPROVAL_REQUIRED` when the policy requires an approval and none was given |
| No arbitrary command | There is no shell tool and no reflection; only registered tools exist |
| No network by default | `SdsExecutionPolicy.SAFE_DEFAULT` has `networkAllowed = false` and the executor refuses a tool that needs the network |
| Bounded work | The timeout, the output limit and the per-turn tool-call cap are all clamped |
| Workspace confinement | Every path goes through `SdsWorkspaceGuard`; a refusal becomes a failed result |
| Untrusted model output | The proposal is parsed with a strict bounded parser and validated against the per-tool schema before anything runs |
| No sensitive logs | The diagnostics store lengths, timings and a salted digest; the timeline stores a tool name and a status |

## Rollback and disable behaviour

- The two experimental patches stay `default = false`, so a build without them is the untouched official app behaviour.
- Proposal mode is off by default and session-only, so the hooks never change a prompt until the user turns it on.
- The allow-list starts empty in the shipped runtime and is only populated with the workspace tools, so removing the registry population removes every executable tool without touching the UI.
- The registry refuses a duplicate name, so a tool cannot be replaced at runtime.

## Tests

`bash scripts/test-agent-core.sh` runs on every push in CI and covers: the safe defaults, the opt-in prompt, the queue and its deduplication, the malformed payload handling, the strict JSON grammar, the per-response call limit, the workspace confinement, the observer diagnostics, every session transition, the strict tool arguments, the allow-list, the approval gate, the unregistered tool refusal, the network refusal, the timeout and the cancellation, and the workspace tools staying inside the workspace.

The CI run of the runtime is recorded in the build workflow; the results are build-verified, not device-runtime-verified.

## Explicitly not implemented

The stream path is statically traced but not yet runtime-verified on a physical device. Callback frequency, coroutine/thread context, lifecycle ordering and interaction with Compose rendering are still unknown. The Console is a separate launcher Activity, not yet an entry embedded in the main chat UI. The current branch does not invoke MCP tools, send tool results back to DeepSeek, or expose a stop control for the official chat itself. The proposal queue is in-memory and Agent mode is session-only.

Therefore, this is a native agent runtime with an approval-gated executor for the private workspace, not a complete multi-turn agent.

## Required next integration order

1. ~~Obtain a stable copy of the exact inspected APK and complete Morphe patch-apply verification.~~ Done, see docs/P0-VERIFICATION.md.
2. Run the patched APK on a real device and prove the SET/APPEND hook fires with complete assistant content, without excessive duplicates or UI-thread stalls. The observer diagnostics are the instrument for this.
3. Trace the Compose navigation/message renderer and embed a Console entry into supported in-app navigation.
4. Feed tool results back only through a verified supported user-message flow; never spoof or rewrite login/session traffic.
5. Implement MCP with a native transport, an explicit consent step and a credential store.
6. Implement the remaining feature modules of docs/FEATURE-PORT-MAP.md, each with its own UI, lifecycle, permissions, persistence, tests and failure modes.
7. Test stop, timeout, process death, rotation, denied permissions and malformed streams on a real device.

## Security caveat

Nothing in this extension silently executes a proposal. PRoot, if added later, runs under the app UID and is not a kernel-level security boundary. Any future executor must require explicit user approval by default.
