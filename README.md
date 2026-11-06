# DeepSeek Mod APK

A custom Morphe Patcher project for building native Android feature patches for the official DeepSeek application.

The user's Super-Deepseek- repository is the feature and UX reference, not source code to copy into the APK. Browser-only modules are redesigned as Android-native DEX extensions and narrow Morphe patches.

> Independent project. Not affiliated with or endorsed by DeepSeek or Morphe. The official DeepSeek APK, decoded sources, credentials and signing keys are not committed.

## Inspected target

| Property | Inspected sample |
|---|---|
| Package | com.deepseek.chat |
| Version | 2.7.4 (versionCode 293) |
| Supported target | 2.7.4 only |
| APK SHA-256 | a868bad448c87444f6c825b5ddbfa23756ad34178ab08503d107ff8e29b4f022 |

See docs/TARGET-APK.md before supporting another release.

## Implemented

- Morphe Patcher project with successful MPP bundle CI.
- Version-scoped fingerprints for the exact prompt serializer and mutable streaming ResponseFragment SET/APPEND methods.
- Compatibility gate and optional launcher label patch.
- DEX extension with approval-first policy defaults, strict bounded tool proposal parser, and app-private workspace path guard.
- Default-off experimental hooks: add tool-format guidance to the prompt and observe the complete accumulated content after SSE ResponseFragment SET/APPEND mutations.
- A separate, default-off Native Agent Console Activity to enable session-only proposal mode, refresh the proposal queue, copy arguments, dismiss proposals, or clear the queue.
- Device-free Java regression tests for parsing, queue bounds, proposal snapshot/dismiss, execution policy, and path confinement.
- A manual verification workflow that applies selected patches to an exact hash-pinned APK and checks package, version, signing validity, manifest Activity, and merged extension DEX.

## Agent scope and limitations

The response observer now targets the native SSE JSON-delta mutation path: SseMessageRoot → mutable response model → SET/APPEND → ResponseFragment.content. In the inspected 2.7.4 Smali, APPEND passes the full accumulated content string after combining the previous content and incoming chunk. This is a better static hook than observing the separate static response-fragment deserializer.

This is still static evidence, not a device-verified runtime callback. The hooks are disabled by default. The Console is a separate launcher Activity, not yet embedded in the main chat UI. No shell command, MCP request, network tool, file write, automatic approval, or tool-result handoff is implemented. The response callback frequency and Compose renderer lifecycle still require real-device verification. This is not yet a complete multi-turn AI agent.

See docs/DEEPSEEK-STREAM-TRACE.md, docs/AGENT-RUNTIME-FOUNDATION.md, docs/NATIVE-AGENT-CONSOLE.md and docs/FEATURE-PORT-MAP.md.

## Build

Requirements: JDK 21, Gradle 9.8.2 and access to the Morphe GitHub Packages registry.

    bash scripts/test-agent-core.sh
    gradle --no-daemon buildAndroid

The MPP bundle is produced in patches/build/libs/. For a test build select these patches in order:

1. DeepSeek 2.7.4 compatibility check
2. Native Agent hook points (experimental)
3. Super DeepSeek native Agent Console

The hook and console patches are disabled by default and only target the exact inspected APK version.

## APK apply verification

The exact source APK must match the inspected 2.7.4 SHA-256. The current official download URL is a moving latest-version endpoint and has returned a different binary, so the verification workflow safely stops before applying old fingerprints. Supply a stable HTTPS URL and SHA-256 for the exact inspected APK; never change the hash to match an uninspected release.

See the manual Verify Morphe patch against official DeepSeek APK workflow. It deletes temporary APKs after checking and does not publish them.

## Security and identity

- Preserve official account sign-in and session behaviour.
- Do not bypass CAPTCHA, anti-fraud, anti-tamper, signature, root-detection, device-integrity or risk checks.
- Do not intercept TLS, install user certificates, extract session tokens or rewrite authentication requests.
- Model output is untrusted. Tool proposals are never executed by this branch.
- A re-signed APK will not use DeepSeek's original certificate and may not update the official installation in place. Keep the official installation and its data intact until a separate build is verified.
- PRoot, if introduced later, runs under the app UID and is not a kernel-level security boundary.
- Check applicable app terms and third-party licenses before distributing patches or modified binaries.

## Project map

- docs/ARCHITECTURE.md
- docs/TARGET-APK.md
- docs/DEEPSEEK-STREAM-TRACE.md
- docs/FEATURE-PORT-MAP.md
- docs/AGENT-RUNTIME-FOUNDATION.md
- docs/NATIVE-AGENT-CONSOLE.md
- docs/SECURITY-BOUNDARIES.md
- docs/VERIFICATION.md
