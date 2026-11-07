# Patch architecture

This repository is a Morphe Patcher project for the official Android DeepSeek application. It is not a replacement chat client and does not embed a browser version of DeepSeek.

## Layers

| Layer | Project area | Responsibility |
|---|---|---|
| Compatibility metadata | patches/.../DeepSeekCompatibility.kt | Exact package and app-version declaration |
| Fingerprints | patches/.../DeepSeekFingerprints.kt | Resolve version-specific obfuscated methods |
| Instruction checks | patches/.../DeepSeekInstructionChecks.kt | Prove the shape of the instructions a hook relies on |
| Morphe patches | patches/src/main/kotlin | Minimal, reviewable APK changes |
| DEX extensions | extensions/sds-core | New Java runtime classes merged into the target APK |
| Feature modules | extensions/sds-core/.../extension | Agent runtime, file tools, artifacts, memories, projects, slash commands, Generative UI, export, MCP, research and the Linux Studio |
| Analysis records | docs/ | Static evidence, porting decisions and safety rules |

## Runtime direction

The target app uses a native Compose chat interface. The WebView-oriented Super DeepSeek JavaScript/CSS engine cannot be installed as-is: it depends on browser DOM, extension APIs, injected page scripts and WebView lifecycle assumptions that the native chat screen does not provide.

The implementation therefore follows this direction:

1. Morphe fingerprints locate narrow methods in the official APK.
2. Patches insert calls into small, version-checked hook points.
3. Precompiled extension DEX provides new native classes.
4. Native Android APIs provide UI, lifecycle, storage, file selection and background work.
5. A schema-driven renderer displays only allow-listed native components.
6. Every feature remains independently testable and can be disabled if its hook is unreliable.

## The patch graph

```
compatibilityGatePatch          (default on)  validates the three seams before anything is touched
  └── nativeAgentHooksPatch     (default off) the prompt, the SET and the APPEND hooks
        └── nativeAgentConsolePatch (default off) the console Activity and the start of the runtime
              └── nativeAgentChatEntryPatch (default off) the entry in the chat screen
```

A patch is only applied when the patches it depends on are applied, so the
runtime never runs without the gate that proves the shape of the instructions.

## What is in this branch

- The Gradle Morphe patch project and the DeepSeek 2.7.4 compatibility declaration.
- Four static fingerprints: the prompt serializer, the streaming SET method, the streaming APPEND method and the launcher Activity.
- The compatibility gate, which resolves the fingerprints and proves the shape of the instructions before any hook is inserted.
- Three runtime patches: the hooks, the console and the chat entry.
- The DEX extension with the agent runtime and every feature module.
- The plain-Java regression test of the runtime and the verification of the patched APK.
- Two CI workflows: the build of the patch bundle and the verification against the pinned target.

No credentials, DeepSeek APK, decoded sources, release keystore or generated APK/MPP files belong in this repository.
