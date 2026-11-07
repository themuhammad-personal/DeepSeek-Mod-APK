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
- `SdsToolExecutor`: the only place a tool runs. It refuses an unregistered tool, refuses a network tool while the network policy forbids it, refuses to run without an explicit approval, bounds the output, and interrupts a tool that exceeds the timeout. The tools run in a pool and not in a single thread, so a tool that ignores the interrupt cannot block the next approved run, and the output of a tool is bounded again with the policy of the executor.
- `SdsWorkspaceTools`: `workspace.list`, `workspace.read` and `workspace.write`. They only touch the app-private workspace, they never run a command, they never use the network and every path goes through `SdsWorkspaceGuard`. A refused path or a mismatching argument becomes a failed result with a reason, not an exception.

### Feature modules (build-verified, not device-runtime-verified)

- `SdsSlashCommands` and the built-ins in `SdsRuntimeRegistry`: `/help`,
  `/memory`, `/projects` and `/files`. The expansion happens in the request
  hook, so the model receives a plain instruction. An unknown command leaves
  the prompt untouched.
- `SdsMemoryStore`: an app-private key/value store for the notes of the user.
  Bounded to 200 entries and 4 KiB per value, and it survives a restart.
- `SdsProjectRegistry`: named folders in the app-private workspace with a
  bounded number of projects, and it survives a restart.
- `SdsArtifactStore` and `SdsArtifactTools`: `artifact.create`,
  `artifact.list` and `artifact.read`. The create tool writes, so it always
  needs an approval, and the arguments use the same strict flat schema as the
  other tools.
- `SdsUiSpec` and `SdsUiTools`: the Generative UI. The assistant describes the
  interface with flat strings and the console renders it with plain Android
  views. No web view, no script and no remote content is involved.
- `SdsExportService`: the export through the system file picker and the system
  share sheet. The app asks for no storage permission and exposes no private
  file.
- `SdsSandboxConfig`, `SdsSandboxManager` and `SdsSandboxTool`: the Linux
  Studio. The runtime is PRoot with a pinned Ubuntu base root filesystem. The
  tool is only on the allow-list while the user turns it on, every command
  still needs an explicit approval, the environment of the app is not
  inherited and the output and the timeout are bounded. The runtime itself is
  not bundled in this build, so the studio refuses to run and says so.
- `SdsJson` and `SdsJsonWriter`: a strict and bounded JSON reader and its
  writer. The tool arguments use the flat reader, this one is for the
  protocols that need the nested form. It refuses trailing data, unquoted
  keys, duplicate keys and documents that break the depth or the size limit.
- `SdsHttpFetcher`: the bounded https fetcher. Only https, no redirect is
  followed and the body is reduced to plain text, so a remote page can never
  inject a view, a script or a command.
- `SdsMcpConfigStore`, `SdsMcpClient` and `SdsMcpTools`: the MCP module. The
  server list stays in the app-private directory, the token never leaves the
  device, the tools of a server join the allow-list under
  `mcp.<server>.<tool>` and they always need the network and an approval.
- `SdsResearchTools`: the retrieval of the research module. It fetches one
  address and returns its text; the synthesis stays with the model.
- `SdsAppContext` and `SdsRuntimeRegistry`: the Android glue that fills the
  runtime state with the app-private directories, and the holder that the
  plain-Java feature logic uses so it can be regression tested.

### The handback of a tool result (static-verified, build-verified, not device-runtime-verified)

- `SdsToolResultHandoff`: holds the result of an approved run and adds it to
  the content once, marked with `[SDS:TOOL_RESULT]`. It is off by default, it
  checks the marker before it adds anything and it only ever hands back the
  result of an approved run.
- `SdsAgentHooks.observeResponseAppend` is the new call site. It runs **before**
  the content update of the streaming APPEND method, so the value that the app
  writes into its own content holder is the value that the chat renders. The
  CI verification proves that the call site reads the register of the
  accumulated content and that its result is written back into that register
  before the update.

**Why.** The app renders the value it writes into its own content holder. The
reference proof of concept used the same seam to rewrite the answer. This
project uses it only for the result of an approved tool run, and only when the
user turns the switch on.

**Safety.** The switch is off by default, the marker makes the operation
idempotent, and the result of a run that was not approved is never handed
back. The device run still has to prove that the chat renders the rewritten
content.

### Observability (build-verified, not device-runtime-verified)

- `SdsObserverDiagnostics`: measures the response observer **without storing any assistant content**. It counts the callbacks, the distinct contents, the duplicated contents, the growing and shrinking contents, the first and last content length, and the threads the callbacks arrived on. The content digest is salted with a random value that is created once per process, so a digest cannot be used to look up a known text.
- The diagnostics are recorded even while proposal mode is off, so a device run can prove that the hook fires and that it receives the complete accumulated answer.

### Console (build-verified, not device-runtime-verified)

- `SdsAgentConsoleActivity`: a native Activity that is not exported and that is opened from the entry in the chat screen; it shows the session state, the approve, deny and stop actions, the activity timeline, the tool allow-list with the schema of every tool, the observer diagnostics, the proposal queue, the memories, the projects, the slash commands, the artifacts, the Generative UI surface and the Linux Studio.
- `SdsChatEntry`: the entry that the chat entry patch puts into the chat screen. It is a plain Android view on the window of the activity, it opens the console and it removes itself again when anything goes wrong.

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
| Only an approved result is handed back | `SdsToolResultHandoff` only holds the result of a run the session approved, and it adds it once |
| No sensitive logs | The diagnostics store lengths, timings and a salted digest; the timeline stores a tool name and a status |
| No silent shell | The only tool that runs a command is `sandbox.run`, it is only on the allow-list while the user turns it on, and every single command needs its own approval |
| No token in a command | The environment of the app is not inherited by the sandbox, so only five fixed variables reach a command |
| No web content | The Generative UI is rendered with plain Android views and the export uses the system dialogs |
| No silent network | The network switch is off by default, the executor refuses every tool that needs the network, and the two network tools need an approval for every single call |
| No foreign advertisement | The advertisement bar of the reference product is not ported on purpose |

## Rollback and disable behaviour

- The two experimental patches stay `default = false`, so a build without them is the untouched official app behaviour.
- Proposal mode is off by default, so the hooks never change a prompt until the user turns it on. The switches of the user are stored in the app-private preferences, so they survive a restart of the app; the proposal queue itself stays in the memory of the process.
- The allow-list is built by the console from the registered tools only. The workspace, the artifact and the Generative UI tools are always there, the sandbox command only while its switch is on, and the tools of an MCP server only while that server is on, so removing the population removes every executable tool without touching the UI.
- The registry refuses a duplicate name, so a tool cannot be replaced at runtime.
- The chat entry is a separate patch, so the console can be reached from the
  launcher alone.
- The Linux Studio is switched off by a switch in the console, and the switch
  removes the tool from the allow-list instead of only hiding it.
- The network is switched off by a switch in the console and the switch
  rebuilds the executor with the matching policy, so the effect is immediate.
- The handback of a tool result is switched off by a switch in the console, so
  the conversation keeps the content of the model until the device run proves
  the flow.
- An MCP server that the user just entered is switched off, and switching it
  off removes its tools from the allow-list.

## Tests

`bash scripts/test-agent-core.sh` runs on every push in CI and covers: the safe defaults, the opt-in prompt, the queue and its deduplication, the malformed payload handling, the strict JSON grammar, the per-response call limit, the workspace confinement, the observer diagnostics, every session transition, the strict tool arguments, the allow-list, the approval gate, the unregistered tool refusal, the network refusal, the timeout and the cancellation, the workspace tools staying inside the workspace, the memory store, the project registry, the slash commands, the prompt expansion, the artifact store and its tools, the Generative UI specification, the state of the Linux Studio, the strict JSON
reader and its writer, the MCP server list and the refusal of a network tool
while the network is off.

The CI run of the runtime is recorded in the build workflow; the results are build-verified, not device-runtime-verified.

## Explicitly not implemented

The stream path is statically traced but not yet runtime-verified on a physical device. Callback frequency, coroutine/thread context, lifecycle ordering and interaction with Compose rendering are still unknown. The console entry in the chat screen is proved statically only, its placement and its touch behaviour on a device are unproven. The current branch does not invoke MCP tools, does not run a research without the network switch and an approval, does not send tool results back to DeepSeek, does not expose a stop control for the official chat itself, and does not bundle the PRoot runtime of the Linux Studio. The proposal queue is in-memory and the switches of the console are stored in the app-private preferences, which are not checked on a device yet.

Therefore, this is a native agent runtime with an approval-gated executor for the private workspace, not a complete multi-turn agent.

## Required next integration order

1. ~~Obtain a stable copy of the exact inspected APK and complete Morphe patch-apply verification.~~ Done, see docs/P0-VERIFICATION.md.
2. Run the patched APK on a real device and prove the SET/APPEND hook fires with complete assistant content, without excessive duplicates or UI-thread stalls. The observer diagnostics are the instrument for this.
3. ~~Trace the Compose navigation/message renderer and embed a Console entry into supported in-app navigation.~~ The entry is a plain view on the window of `MainActivity`, proven statically; the device check of the placement is still open.
4. Feed tool results back only through a verified supported user-message flow; never spoof or rewrite login/session traffic.
5. Implement MCP with a native transport, an explicit consent step and a credential store.
6. ~~Implement the remaining feature modules of docs/FEATURE-PORT-MAP.md, each with its own UI, lifecycle, permissions, persistence, tests and failure modes.~~ The slash commands, the memories, the projects, the artifacts, the export, the Generative UI, the Linux Studio, the MCP client and the research retrieval are done. The advertisement bar of the reference product is not ported on purpose, see docs/NATIVE-FEATURE-MODULES.md.
7. Test stop, timeout, process death, rotation, denied permissions and malformed streams on a real device.

## Security caveat

Nothing in this extension silently executes a proposal. PRoot, if added later, runs under the app UID and is not a kernel-level security boundary. Any future executor must require explicit user approval by default.
