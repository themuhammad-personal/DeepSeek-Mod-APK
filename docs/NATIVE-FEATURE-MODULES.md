# Native feature modules — source mapping, rationale, safety, rollback, tests

Every feature that was ported from the reference product is listed here with the
place it comes from, the reason it is built the way it is, what can go wrong,
how it is switched off again and how it is tested.

The rule that applies to every row: **the feature is native Android code in the
DEX extension, plus a small Morphe patch where the official app has to be
reached.** No web view, no JavaScript, no injected HTML and no remote content
is used anywhere.

Status labels used in this document:

| Label | Meaning |
|---|---|
| `static-verified` | read from the decoded DEX of the pinned 2.7.4 target and confirmed in the source |
| `build-verified` | compiles and passes the regression test in CI |
| `patch-applied` | the Morphe patch applies to the rebuilt pinned target and the call site was proved in the patched DEX |
| `device-runtime-verified` | proven on a real device — **nothing in this project has reached this label yet** |
| `planned` | designed, not implemented |

---

## 1. AI Agent

| | |
|---|---|
| Reference source | `deepseek-mod-poc/SEAM-ANALYSIS.md` §1.1 (`jy1` prompt serializer) and §1.3 (the unconfirmed tool-turn path), `src-mod/com/deepseek/mod/ModAgent.java` |
| Native implementation | `SdsAgentHooks`, `SdsToolCallParser`, `SdsToolCall`, `SdsToolRegistry`, `SdsToolExecutor`, `SdsToolContext`, `SdsToolResult`, `SdsExecutionPolicy`, `SdsToolArguments`, `SdsAgentSession`, `SdsAgentEvent`, `SdsCancellation` |
| Patch | `Native Agent hook points (experimental)` — `NativeAgentHooksPatch.kt` |
| Status | `static-verified`, `build-verified`, `patch-applied` |

**Why.** The reference PoC rewrote the prompt and the response of the official
app with a `[SDS:RUN]` block and ran the command straight away. That is exactly
the behaviour this project must not have: model output is untrusted. The port
keeps the two seams but changes the semantics: the hook only *collects* a
proposal, and the proposal is executed only after the user pressed Approve.

**Handback of the result.** The result of an approved run can be added to the
conversation, marked with `[SDS:TOOL_RESULT]`, right before the app writes the
content that it renders (`SdsToolResultHandoff`, `SdsAgentHooks.observeResponseAppend`).
The switch is off by default, the marker makes it idempotent and only an
approved result is ever handed back. The flow is proved statically in the
patched DEX and it still needs the device run.

**Safety.** The tool allow-list is a closed set of registered objects, there is
no lookup by class name and no reflection. Every tool declares a strict flat
argument schema; a nested object, an array, a trailing byte or an unknown key
is refused before the tool runs. The execution runs on a daemon thread, honours
the cancellation flag, is stopped by the policy timeout and returns a bounded
output. A tool that needs the network is refused while the network ban is on.

**Rollback.** The patch is off by default (`--enable` decides). Turning it off
leaves the official app untouched: the hook calls are removed and the
extension classes are never reached.

**Tests.** `scripts/test-agent-core.sh` covers the parser, the queue and the
deduplication, the per-response limit, the strict grammar, the registry, the
approval gate, the approved run, the refusal of an unregistered tool, the
refusal of a network tool, the timeout, the cancellation, the workspace escape
and the handback of a result.

---

## 2. Tool review (approval, deny, stop)

| | |
|---|---|
| Reference source | The reference product reviews the tool calls of the agent; the PoC had no review step at all (`SEAM-ANALYSIS.md` §5 calls the tool turn unconfirmed) |
| Native implementation | `SdsAgentSession` (state machine `IDLE → COLLECTING → AWAITING_APPROVAL → RUNNING → COMPLETED / DENIED / CANCELLED / FAILED`), `SdsAgentConsoleActivity` (Approve, Deny, Stop) |
| Patch | `Super DeepSeek native Agent Console` — `NativeAgentConsolePatch.kt` (the Activity is declared with `exported=false` and without a launcher filter) |
| Status | `static-verified`, `build-verified`, `patch-applied` |

**Why.** The state machine exists so that no code path can reach a tool
without a matching approval record. `SdsToolExecutor` refuses to run a call
that the session did not move to `RUNNING` for.

**Safety.** Approve is bound to one call id, Deny drops the proposal and Stop
cancels the running call through the cancellation flag. The timeline keeps the
kind of every event, never the content of a message.

**Rollback.** The console patch is off by default; the console activity is
declared in the manifest only while the patch is enabled.

**Tests.** The session transitions, the approve and deny paths, the stop path
and the timeline are covered by the regression test.

---

## 3. File tools

| | |
|---|---|
| Reference source | `deepseek-mod-poc/SEAM-ANALYSIS.md` §1.1 (the `jy1` serializer) for the seam; the file surface itself is new in this project |
| Native implementation | `SdsWorkspaceTools` (`workspace.list`, `workspace.read`, `workspace.write`), `SdsWorkspaceGuard` |
| Status | `static-verified`, `build-verified` |

**Why.** The reference product ran shell commands. A shell is the widest
possible door, so this project starts with three file tools that can only
touch the app-private workspace.

**Safety.** Every path is resolved through the guard, a path that escapes the
workspace is refused and reported as a failed result, the read is limited to
16 KiB and the write to 64 KiB, there is no shell and no network, and the
write tool is not read-only so it always needs an approval.

**Rollback.** The tools are only reachable through the allow-list; removing
them from the registry disables them without touching the app.

**Tests.** The escape attempt, the missing argument, the schema mismatch and
the read and write round trip are covered.

---

## 4. Slash commands

| | |
|---|---|
| Reference source | The feature list of the reference product (slash commands); the expansion point is the `jy1` seam of `SEAM-ANALYSIS.md` §1.1 |
| Native implementation | `SdsSlashCommands`, the built-ins in `SdsRuntimeRegistry` (`/help`, `/memory`, `/projects`, `/files`), expanded in `SdsAgentHooks.beforePrompt` |
| Status | `static-verified`, `build-verified`, `patch-applied` (the prompt hook is proved in the patched DEX) |

**Why.** A command is expanded natively before the prompt is serialised, so
the model receives a plain instruction and the app keeps the behaviour of the
official request.

**Safety.** The built-in commands only read. An unknown command leaves the
prompt untouched, so the app never silently drops what the user typed. The
command registry is bounded and a duplicate name is refused.

**Rollback.** The expansion happens inside the agent hook, so turning the hook
patch off removes the behaviour completely.

**Tests.** The detection, the expansion of `/help`, the untouched unknown
command and the untouched plain prompt are covered.

---

## 5. Memory

| | |
|---|---|
| Reference source | The feature list of the reference product (memory); the storage is new in this project |
| Native implementation | `SdsMemoryStore`, the memory card and the switch in `SdsAgentConsoleActivity` |
| Status | `static-verified`, `build-verified` |

**Why.** A memory is a short note the user keeps for the assistant. The file is written through a temporary file that then takes its place, so a crash cannot leave a half written file behind. It is
stored in the app-private directory, so it is removed with the app and it is
not reachable by another app.

**Safety.** The memories are only added to a prompt when the user asks for
`/memory` or turns the switch on. The store is bounded to 200 entries and
4 KiB per value, the key characters are restricted and the values are stored
Base64 encoded so the format cannot be confused.

**Rollback.** The switch in the console switches the injection off; the data
stays on the device until the app data is cleared.

**Tests.** The store, the read back after a restart, the removal, the refused
key, the refused value and the bounded prompt block are covered.

---

## 6. Projects

| | |
|---|---|
| Reference source | The feature list of the reference product (projects); the registry is new in this project |
| Native implementation | `SdsProjectRegistry`, the project card in `SdsAgentConsoleActivity` |
| Status | `static-verified`, `build-verified` |

**Why.** A project is a named folder inside the app-private workspace. The
registry keeps the name, the folder and the creation time, so the chat, the
file tools and the console talk about the same project without a document
provider and without any access outside the workspace.

**Safety.** The name characters are restricted, the number of projects is
bounded and a folder is only ever derived from the name, never taken from the
model.

**Rollback.** The registry is empty by default and only the console writes to
it.

**Tests.** The creation, the duplicate, the refused character, the read back
after a restart and the removal are covered.

---

## 7. Generative UI

| | |
|---|---|
| Reference source | The feature list of the reference product (generative UI); the surface is new in this project |
| Native implementation | `SdsUiSpec`, `SdsUiTools` (`ui.render`), the renderer in `SdsAgentConsoleActivity` |
| Status | `static-verified`, `build-verified` |

**Why.** The reference product rendered the interface of the model in a web
view. A web view would break the rule of this project, so the interface is
described with flat strings and rendered with plain Android views.

**Safety.** The assistant can only choose from a fixed set of fields, every
field is bounded, the renderer never loads a resource and never executes a
script, and the only action copies a bounded string to the clipboard.

**Rollback.** The tool is only reachable through the allow-list and the
surface only shows the last requested interface.

**Tests.** The rendering, the item split, the refused empty interface, the
refused unknown argument and the bounds are covered.

---

## 8. Artifacts

| | |
|---|---|
| Reference source | The feature list of the reference product (artifacts); the store is new in this project |
| Native implementation | `SdsArtifactStore`, `SdsArtifactTools` (`artifact.create`, `artifact.list`, `artifact.read`), the artifact card in `SdsAgentConsoleActivity` |
| Status | `static-verified`, `build-verified` |

**Why.** An artifact is a piece of content the assistant produced. It is
stored in the app-private directory with a title, a language tag and a bounded
content.

**Safety.** The create tool writes and therefore always needs an approval. The
arguments use the same strict flat schema as the other tools. The number of
artifacts and the size of a content are bounded, and the export name is
reduced to safe characters.

**Rollback.** The tools are only reachable through the allow-list, the store
is empty by default.

**Tests.** The creation, the refused title, the refused language, the read
back after a restart, the removal, the listing and the refused unknown
argument are covered.

---

## 9. Export

| | |
|---|---|
| Reference source | The feature list of the reference product (export); the flow is new in this project |
| Native implementation | `SdsExportService`, the activity result handling in `SdsAgentConsoleActivity` |
| Status | `static-verified`, `build-verified` |

**Why.** Both export flows are handled by the system: saving opens the system
file picker and the system writes the file, sharing opens the system share
sheet with the content as text.

**Safety.** The app asks for no storage permission and exposes no private file
through a provider. Nothing is sent anywhere without a tap on the system
dialog. The activity export writes only the kind of the events and never the
content of a message.

**Rollback.** The export is only reachable from the console cards.

**Tests.** The export service itself is Android only and is therefore covered
by the build; the bounded activity export is checked by the console build.

---

## 10. Diagnostics

| | |
|---|---|
| Reference source | `deepseek-mod-poc/VERIFY.md` and `TEST-PLAN.md` (the static verification idea) |
| Native implementation | `SdsObserverDiagnostics`, the diagnostics card in `SdsAgentConsoleActivity` |
| Status | `static-verified`, `build-verified`, `patch-applied` |

**Why.** The device test of the observer needs to know whether the hook fires,
how often, with which length and whether the content grows. The diagnostics
answer that without keeping a single message.

**Safety.** Only counters and salted digests are kept, never the content. The
ring holds 64 events. The digests are salted per process, so they cannot be
compared across runs.

**Rollback.** The diagnostics record even when the proposal mode is off, so
they can be used to prove the hook without enabling a feature.

**Tests.** The callback count, the duplicate count, the growth and shrink
detection, the thread names and the length bounds are covered.

---

## 11. Linux Studio

| | |
|---|---|
| Reference source | `deepseek-mod-poc/src-mod/com/deepseek/mod/ModSandbox.java`, `ModRootfs.java`, `ModConfig.java`, `assets/mod-sandbox/rootfs.json`, `SANDBOX-NOTICE.md` |
| Native implementation | `SdsSandboxConfig`, `SdsSandboxManager`, `SdsSandboxTool` (`sandbox.run`), the Linux Studio card in `SdsAgentConsoleActivity` |
| Status | `static-verified`, `build-verified`; the runtime itself is `planned` for this build |

**Why.** The reference PoC ran a model generated command straight away through
PRoot. That is not acceptable here, so the port keeps the runtime and the
pinned root filesystem but adds the missing gate: the tool is only on the
allow-list when the user turns it on, and every single command still needs an
explicit approval that shows the exact command.

**Safety.** PRoot is a userspace path translator and **not** a kernel sandbox,
so the console says so next to the switch. The environment of the app is not
inherited, so a session token can never reach a command. The root filesystem
is pinned by its SHA-256 and the marker file only counts when the checksum
matches. The output is bounded, the timeout is enforced and the cancellation
flag stops the process.

**The installation of the root filesystem.** The console has a button that shows
the exact address and the checksum of the pinned archive, waits for the
approval of the user, downloads it over https, checks the checksum against the
official list of the release, unpacks it with the tar of the system and only
then puts the directory in place. An interrupted download leaves no truncated
file behind, a refused archive leaves no directory behind and the archive
itself is removed when the checksum does not match. The address is
`https://cdimage.ubuntu.com/ubuntu-base/releases/noble/release/ubuntu-base-24.04.5-base-arm64.tar.gz`
and the checksum is the one of the official `SHA256SUMS` of that directory.

**Rollback.** The switch in the console removes the tool from the allow-list
and the feature disappears without touching the app.

**Tests.** The missing runtime, the wrong checksum, the refused unknown
argument and the refused run are covered, and so are the checksum of a file,
the refusal of an archive that is not the pinned one and the refusal of a
download over a plain address. The runtime is not bundled in this build, so
the studio reports that it is not ready and refuses to run instead of
pretending to work.

---

## 12. MCP

| | |
|---|---|
| Reference source | The feature list of the reference product (MCP); the reference PoC had no MCP at all |
| Native implementation | `SdsMcpConfigStore`, `SdsMcpClient`, `SdsMcpTools`, the MCP card in `SdsAgentConsoleActivity`, the strict JSON layer `SdsJson` and `SdsJsonWriter` |
| Status | `static-verified`, `build-verified` |

**Why.** The reference product used a browser fetch bridge. A bridge that the
page can call is exactly the kind of thing this project refuses, so the client
is native: it speaks JSON-RPC 2.0 over HTTPS to a server the user entered by
hand.

**Safety.** The network switch in the console is off by default and the
executor refuses every tool that needs the network while it is off. Every
call of a server tool needs its own approval and the tools are not read-only.
Only https is used, a redirect is never followed so the token of a server
cannot reach another host, the answer is bounded and the JSON is read with the
strict reader.

**The handshake.** The client follows the revisions of the specification that
use a session: it sends `initialize` with the version it prefers, takes the
version the server answers with, refuses a version it does not know, confirms
the session with `notifications/initialized` and sends the negotiated version
in the `MCP-Protocol-Version` header of every later call. The session the
server hands out is sent back with every call. A server that only speaks the
stateless revision of 2026-07-28 refuses the handshake and is reported as a
server that could not be reached.
The address has to use https, the answer is bounded and it is parsed with the
strict reader, so a broken or hostile answer is refused instead of being used.
The token of a server stays in the app-private directory and is never written
into a log, a timeline entry or a diagnostic. A server that the user just
entered is switched off until the user turns it on, and turning it off removes
its tools from the allow-list.

**Rollback.** Deleting the server removes its tools from the allow-list and
its token from the device.

**Tests.** The strict reader and its writer, the refused documents, the
server list, the refused plain http address, the duplicate server, the read
back after a restart and the prefixed name of a server tool are covered. The
transport itself needs a server, so it is build-verified only.

---

## 13. Deep Research

| | |
|---|---|
| Reference source | The feature list of the reference product (Deep Research); the retrieval is new in this project |
| Native implementation | `SdsHttpFetcher`, `SdsResearchTools` (`research.fetch`), the Deep Research card in `SdsAgentConsoleActivity` |
| Status | `static-verified`, `build-verified` |

**Why.** The reference product retrieved with a browser bridge and wrote the
answer itself. The port splits the two: the app retrieves the text and the
model writes the synthesis, so the app never invents an answer.

**Safety.** The same two gates as MCP: the network switch and the explicit
approval for every fetch, and the executor refuses the tool while the network
is off. Only https is allowed, a redirect is never followed, so a server
cannot move the request to another host, and the answer is bounded and reduced
to plain text, so a page can never inject a view, a script or a command.

**Rollback.** The tool is only reachable through the allow-list and the
network switch removes its effect.

**Tests.** The refusal of a network tool while the network is off and the
plain text extraction, which proves that the markup, the scripts and the
styles are removed, are covered.

---

## 14. Advanced settings and the advertisement bar

**Advanced settings** are implemented as the switches of the console: the tool
proposal mode, the network switch, the switch that adds the memories to every
prompt, the switch of the Linux Studio and the switch that returns the result of
a tool to the chat. Each of them changes one thing and each of them is off by
default. The value of every switch is stored in the app-private preferences of
the app, so the user does not have to turn them on again after a restart. The
runtime restores them when the chat screen starts.

**The advertisement bar of the reference product is deliberately not ported.**
It is a foreign advertisement surface inside the chat of the official app: it
would inject content that the user of the official app never asked for, it
would need a network call and a tracking identifier on every screen, and it
conflicts with the rule that no foreign content may enter the native surface.
This is the one capability of the reference list that is not ported on
purpose, and it is written down here so that the decision is visible.

---

## What is still missing

| Item | Blocker |
|---|---|
| Device runtime proof of the observer | a real device with the official 2.7.4 and a login |
| The tool result has to reach the chat | the official tool turn is unconfirmed in the reference analysis, so the flow is not proven |
| The chat entry has to be checked on a device | the placement and the touch behaviour of the floating entry are not proven |
| The PRoot runtime | the binaries of PRoot are not bundled, so the studio refuses to run even when the root filesystem is installed |
| The root filesystem | it can be downloaded and installed now, but the first download needs about 29 MB and the explicit approval of the user |
| MCP and Deep Research | a network transport that is switched off by default; the MCP handshake follows the revisions of the specification that use a session, so a server that only speaks the stateless revision of 2026-07-28 is refused |
