# Native Agent Console

## What it is

The Morphe patch named **Super DeepSeek native Agent Console** adds a second
launcher entry to the inspected DeepSeek 2.7.4 APK. It opens an Android-native
Activity implemented in the DEX extension. The original DeepSeek launcher
Activity and the sign-in flow are not replaced.

A second patch, **Super DeepSeek native Agent chat entry**, puts a small
floating entry into the chat screen itself, so the console can be reached
without leaving the conversation.

Enable these patches in Morphe:

1. DeepSeek 2.7.4 compatibility check
2. Native Agent hook points (experimental)
3. Super DeepSeek native Agent Console
4. Super DeepSeek native Agent chat entry (optional)

The three experimental patches are disabled by default.

## How the chat entry works

`com.deepseek.chat.MainActivity` keeps its original name in the official 2.7.4
build, so the hook does not depend on the obfuscation mapping. The patch
inserts one call in front of the last `return` of `MainActivity.onCreate`, so
the original code of the activity runs completely before the entry is added.

The entry is a plain Android view that is placed in the window of the activity.
It does not touch the Compose tree, the message renderer or the composer. It
only opens the console, and it removes itself again when anything goes wrong,
so the official chat can never be broken by it. The CI verification proves that
the call site sits directly in front of the return in the patched DEX; the
placement and the touch behaviour on a device are still unproven.

## Current controls

**Return the result of a tool to the chat**

- The switch is off by default. The flow is proved statically, not on a device.
- When it is on, the result of an approved run is added once to the
  conversation, marked with `[SDS:TOOL_RESULT]`, right before the app writes
  the content that it renders.
- The marker is checked first, so a repeated callback never duplicates the
  result, and only the result of an approved run is ever handed back.

**Tool proposal mode**

- Allow DeepSeek to propose native tools: enables the prompt guidance for the
  current app process. It resets after a process restart.

**Agent session**

- Approve: approves the pending proposal. Nothing runs before that; the
  approval only moves the session into `AWAITING_APPROVAL`.
- Deny: refuses the pending proposal and removes it from the queue.
- Stop: cancels a waiting or running tool and sets the cancellation flag. It
  never approves anything.

**Tool allow-list**

- The list shows every registered tool with its description and its exact
  argument schema, and marks the read-only ones. Only these tools can run.
- The list holds `workspace.list`, `workspace.read`, `workspace.write`,
  `artifact.create`, `artifact.list`, `artifact.read`, `ui.render` and
  `research.fetch`.
- `sandbox.run` only appears while the Linux Studio switch is on.
- The tools of an MCP server appear under `mcp.<server>.<tool>` while the
  server is switched on.

**Observer diagnostics**

- The counters show the callbacks, the distinct and the duplicated contents,
  the growing and shrinking contents, the last and the maximum content length
  and the threads the callbacks arrived on. No assistant content is stored.
- Reset diagnostics clears the counters.

**Proposals**

- Approve: approves that proposal and starts it.
- Copy: copies the selected proposal's JSON arguments.
- Dismiss: removes a proposal from the queue. It does not approve or execute
  it.
- Clear: clears the queue and resets the session.
- Refresh: reads the in-memory proposal queue without consuming entries.

**Activity timeline**

- The timeline lists the recorded decisions in reverse order: proposal
  received, approved, denied, stop requested, run started, run finished and
  run failed. It holds at most 64 entries and contains no assistant content.
- Export the activity to a file opens the system file picker and writes the
  kinds of the events, never the content of a message.

**Memories**

- Add a memory with the form `key = note`. The store is app-private and
  bounded to 200 entries of 4 KiB.
- The switch adds the memories to every prompt. It is off by default, and
  `/memory` adds them to a single prompt without the switch.
- Delete removes a memory from the device.

**Projects**

- Create a project by name. A project is a named folder in the app-private
  workspace, and the registry survives an app restart.
- Delete removes the project from the registry. The files inside it are not
  touched.

**Slash commands**

- The list shows the registered commands. A command is expanded natively
  before the prompt is serialised.
- `/help` lists the commands, `/memory` adds the stored memories, `/projects`
  lists the projects and `/files` lists the workspace.
- An unknown command leaves the prompt untouched.

**Artifacts**

- View shows the content of an artifact, Save opens the system file picker,
  Share opens the system share sheet and Delete removes the artifact.
- The store is app-private and bounded to 50 artifacts of 256 KiB.

**Generative UI**

- The surface shows the interface the assistant asked for last: a title, a
  subtitle, a body, a list, a code block and one action.
- The action copies its argument to the clipboard. Nothing is executed and
  nothing is loaded from the network.

**MCP servers**

- Add a server with its name, its https address and an optional token. The
  token stays in the app-private directory and is never written into a log, a
  timeline entry or a diagnostic.
- A server that was just added is switched off. Switch it on to let its tools
  join the allow-list, and switch it off to remove them again.
- Ask the servers for their tools lists the tools of the enabled servers. The
  request needs the network.
- The network switch allows the agent to use the network. It is off by
  default, and while it is off the executor refuses every tool that needs the
  network.

**Deep Research**

- The address field fetches one address. Only https is allowed and the fetch
  needs the network switch and an approval that shows the exact address.
- The answer is reduced to plain text, so a page can never inject a view, a
  script or a command. The synthesis is written by the model in the chat, the
  app never invents an answer.

**Linux Studio**

- The card states whether the PRoot runtime is bundled and whether the root
  filesystem is ready.
- The switch offers the sandbox command to the agent. While it is off, the
  tool is not on the allow-list at all.
- The command field runs one command, but only after a dialog that shows the
  exact command and asks for the approval.
- PRoot is a userspace path translator and **not** a kernel sandbox. The card
  says so next to the switch.

## Response source

The response observer is attached to the mutable SSE ResponseFragment content
SET and APPEND methods in the inspected 2.7.4 APK. SET passes the replacement
content. APPEND first combines the old content with the new chunk and passes
the complete accumulated string. A bounded parser detects the complete SDS
tool-call envelopes within that accumulated text and deduplicates them by call
id.

This is a static instruction-level finding. The CI verification proves that
the patched DEX calls the observer with the register that carries the complete
accumulated content, but it does not prove the runtime callback frequency, the
final-message semantics, the threading, the lifecycle or the alignment with the
Compose renderer. The observer diagnostics are the instrument for the device run
that has to close this gap.

## What a tool can do

Only the tools on the allow-list exist, and each one declares exactly what it
touches:

| Tool | Touches | Approval |
|---|---|---|
| `workspace.list` | the app-private workspace | read-only |
| `workspace.read` | the app-private workspace, 16 KiB | read-only |
| `workspace.write` | the app-private workspace, 64 KiB | always |
| `artifact.list` | the app-private artifact store | read-only |
| `artifact.read` | the app-private artifact store | read-only |
| `artifact.create` | the app-private artifact store | always |
| `ui.render` | the console surface only | read-only |
| `research.fetch` | one https address | always, and the network |
| `mcp.<server>.<tool>` | one MCP server the user entered | always, and the network |
| `sandbox.run` | the PRoot runtime, only while the switch is on | always |

There is no arbitrary command execution outside the Linux Studio and no access
outside the workspace. The two network tools exist, but they need the network
switch that is off by default, and every single call needs its own approval. Every path is resolved with a
canonical-path check, and an argument that the tool does not declare is
refused.

A tool that is not on the allow-list cannot run at all: the executor refuses it
before any code of the tool is reached.

## Hard limitations

- The console is not a chat replacement and does not change the official chat
  screen.
- The tool result can be returned to the chat, but the flow is only proved
  statically. The switch is off by default and the device run has to prove
  that the chat renders the rewritten content and that nothing else changes.
- The proposal queue is in-memory and the proposal mode is session-only.
- The PRoot runtime is not bundled in this build, so the Linux Studio refuses
  to run and says so.
- The chat entry is proven statically only. Its placement and its touch
  behaviour on a device are unproven.

## Failure modes

| Situation | Behaviour |
|---|---|
| The proposal names an unknown tool | The executor refuses it with `tool is not registered` |
| The proposal was not approved | The executor refuses it with `approval is required` |
| The tool needs the network | The executor refuses it while the network policy forbids it |
| The path leaves the workspace | The tool returns a failed result with `the path was refused` |
| The arguments do not match the schema | The tool returns a failed result with `the arguments did not match the schema` |
| The tool exceeds the timeout | The executor interrupts it and reports `TIMED_OUT` |
| The user pressed Stop | The cancellation flag is set and the run reports `CANCELLED` |
| The tool threw | The executor reports the exception class and a sanitised message, without a stack trace and without the workspace path |
| The PRoot runtime is missing | The Linux Studio refuses the run and reports that it is not bundled |
| The root filesystem checksum does not match | The root filesystem is not accepted and the studio is not ready |
| The system file picker cannot be opened | The export shows a message and nothing is written |
| An unknown slash command is typed | The prompt is sent unchanged, nothing is dropped |
| The handback is on and the content already carries the marker | The content is left alone, the result is never added twice |
| The network is off | The executor refuses every tool that needs the network |
| The MCP server is not reachable | The tool returns a failed result and the token never appears in the message |
| The address answers with an error | The fetch reports the status and no text is shown |

## Tests

    bash scripts/test-agent-core.sh
    gradle --no-daemon buildAndroid

The regression test covers the session, the allow-list, the strict arguments,
the workspace confinement, the memory store, the project registry, the slash
commands, the prompt expansion, the artifact store and tools, the Generative
UI specification, the state of the Linux Studio, the strict JSON reader and
its writer, the MCP server list and the refusal of a network tool while the
network is off.
