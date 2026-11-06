# Patch architecture

This repository is a Morphe Patcher project for the official Android DeepSeek application. It is not a replacement chat client and does not embed a browser version of DeepSeek.

## Layers

| Layer | Project area | Responsibility |
|---|---|---|
| Compatibility metadata | patches/.../DeepSeekCompatibility.kt | Exact package and app-version declaration |
| Fingerprints | patches/.../DeepSeekFingerprints.kt | Resolve version-specific obfuscated methods |
| Morphe patches | patches/src/main/kotlin | Minimal, reviewable APK changes |
| DEX extensions | extensions/sds-core | New Java/Kotlin runtime classes merged into the target APK |
| Feature modules | future extensions and patches | Native UI, agent controller, MCP, sandbox, memory, research and export |
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

## First milestone in this branch

- Gradle Morphe patch project.
- DeepSeek 2.7.4 compatibility declaration.
- Two static fingerprints for the prompt serializer and response content deserializer.
- No-behaviour-change compatibility gate.
- Optional launcher-label resource patch.
- Minimal DEX extension module.
- CI workflow to build and upload the MPP artifact.

No credentials, DeepSeek APK, decoded sources, release keystore or generated APK/MPP files belong in this repository.
