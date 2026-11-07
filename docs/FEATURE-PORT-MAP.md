# Super DeepSeek → Native Android port map

The Super DeepSeek repository is the product and UX reference. Its web implementation is not copied into the native APK. Each feature is redesigned around Android lifecycle, app-private storage, permissions and DeepSeek's native Compose chat surface.

| Reference capability | Web implementation concept | Native port target | Current state |
|---|---|---|---|
| AI Agent | sd-agent.js, prompt and tool loop | DEX request hook + SSE ResponseFragment observer + AgentController | Prompt and streaming SET/APPEND hooks implemented and patch-verified; session state machine with approve, deny and stop; approval-gated executor with seven allow-listed tools; no result handoff yet |
| Tool review | tool cards and ask/approve UI | Android Activity proposal console | Implemented as a separate launcher Activity and reachable from a native entry in the chat screen; review, approve, deny, stop, copy and dismiss; only allow-listed tools can run |
| Linux Studio | browser terminal and file workspace | Native Activity + app-private sandbox runtime | Approval-gated PRoot tool implemented with a pinned Ubuntu root filesystem and a bounded output; the runtime is not bundled in this build, so the studio refuses to run; the entry point and the download of the root filesystem are still open |
| MCP | sd-genui-mcp.js and browser fetch bridge | Native MCP client with transport, consent and credential store | Planned; needs a JSON-RPC transport that is off by default |
| Generative UI | sd-genui modules, HTML/CSS renderer | Schema-validated native view renderer | Implemented as plain Android views rendered from a bounded flat specification; no web view and no script |
| Memory | browser/local storage records | App-private persistent store with inspect, delete and export controls | Implemented as an app-private key/value store with a bounded size; it is only added to a prompt when the user asks for /memory or turns the switch on |
| Personas and prompts | system prompt and profile settings | Native prompt profile manager | Planned |
| Slash commands | web composer popup | Native command registry expanded in the request hook | Implemented as a native command registry with four read-only built-ins; an unknown command leaves the prompt untouched |
| Projects | browser project/file drawer | Native project workspace and Android document picker | Implemented as a registry of named folders in the app-private workspace; the document picker is still planned |
| Deep Research | multi-step web retrieval | Native research orchestrator with source provenance and cancellation | Planned; it needs a network transport that is off by default |
| File tools | browser bridge and sandbox files | ContentResolver/document provider + sandbox workspace adapter | Workspace list, read and write implemented behind the allow-list with canonical-path confinement; document provider still planned |
| Artifacts | generated cards and preview panel | Native artifact registry, viewer and sharing | Implemented as an app-private artifact store with a bounded content, a viewer card and the export actions; the version history is still planned |
| Export / backup | browser export helpers | Android share sheet, SAF and backup/restore format | Implemented for the artifacts and for the activity timeline through the system file picker and the system share sheet, without any storage permission; the full backup format is still planned |
| Activity timeline | sd-run.js run cards and steps | Native agent event log and stoppable task UI | Native timeline of approve, deny, stop and run events implemented and shown in the console, and exportable to a file; the official chat itself is not stoppable yet |
| Diagnostics | page-contract probes | Version-specific app contract checks and safe diagnostic export | Observer metrics implemented without storing content, and the activity export writes to a file the user chooses; the app contract checks are still planned |

## Streaming response trace

The inspected 2.7.4 stream path has been statically traced:

SseMessageRoot → mutable response model → JSON delta SET/APPEND → ResponseFragment.content.

The experimental patch observes SET/APPEND after their state-holder writes, so the parser receives the replacement or full accumulated content string. This supersedes the earlier static ResponseFragment deserializer hook. Runtime callback frequency, coroutine/thread context, Compose rendering and device behaviour remain unverified.

See docs/DEEPSEEK-STREAM-TRACE.md.

## Per-feature documentation

The source mapping, the patch rationale, the safety analysis, the rollback and
the tests of every ported feature are written down in
docs/NATIVE-FEATURE-MODULES.md. The console surface is described in
docs/NATIVE-AGENT-CONSOLE.md and the runtime in
docs/AGENT-RUNTIME-FOUNDATION.md.

## Porting rules

- Recreate the interaction and capability, not the DOM or browser implementation.
- Do not copy minified web bundles, extension manifests, CSS selectors, browser APIs or WebView bridge code into the DEX extension.
- Reuse product terminology and documented behaviour as a design reference, but write Android-native implementations and tests.
- Prefer stable app APIs and narrowly scoped fingerprints over broad method replacement.
- Do not claim a feature is functional until target method, UI entry point, runtime behaviour and failure modes are verified.
- Keep experimental patches disabled by default and scope each to the exact APK fingerprint set.
