# Super DeepSeek → Native Android port map

The Super DeepSeek repository is the product and feature reference. Its web implementation is not copied into this APK project. Each capability must be re-designed around the official Android app's native lifecycle, Compose UI, storage model and permissions.

| Reference capability | Web implementation concept | Native port target | Status |
|---|---|---|---|
| AI Agent | sd-agent.js, prompt and tool loop | Kotlin AgentController + narrow request/response adapters | Planned; only static serializer/content seams identified |
| Linux Studio | browser terminal and file workspace | Android Activity/Compose UI + app-private sandbox runtime | Planned; security design required |
| MCP | sd-genui-mcp.js and browser fetch bridge | Kotlin MCP client, HTTP/Streamable HTTP transport, consent and credential store | Planned |
| Generative UI | sd-genui*.js, HTML/CSS renderer | Schema-validated Compose renderer and native artifact viewer | Planned |
| Memory | browser/local storage records | Encrypted or app-private persistent store with inspect/export/delete controls | Planned |
| Personas and prompts | system prompt and profile settings | Native prompt profile manager | Planned |
| Slash commands | web composer popup | Compose command palette and send-pipeline integration | Planned |
| Projects | browser project/file drawer | Native project workspace and Android document picker | Planned |
| Deep Research | multi-step web retrieval | Native research orchestrator with source provenance and cancellation | Planned |
| File tools | browser bridge and sandbox files | ContentResolver/document provider + sandbox workspace adapter | Planned |
| Artifacts | generated cards and preview panel | Native artifact registry, viewer, version history and sharing | Planned |
| Export / backup | browser export helpers | Android share sheet, SAF and backup/restore format | Planned |
| Activity timeline | sd-run.js run cards and steps | Native agent event log and stoppable task UI | Planned |
| Diagnostics | page-contract probes | Version-specific app contract checks and safe diagnostic export | Planned |

## Porting rules

- Recreate the interaction and capability, not the DOM or browser implementation.
- Do not copy minified web bundles, extension manifests, CSS selectors, browser APIs or WebView bridge code into the DEX extension.
- Reuse product terminology and documented behaviour as a design reference, but write Android-native implementations and tests.
- Prefer stable app APIs and narrowly scoped fingerprints over broad method replacement.
- Do not claim a feature is functional until target method, UI entry point, runtime behaviour and failure modes are verified.
