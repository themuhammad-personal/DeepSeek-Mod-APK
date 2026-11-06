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

## Implemented in this phase

- Morphe Patcher project with successful MPP bundle CI.
- Structural fingerprints for the inspected prompt serializer field and response content deserializer.
- Compatibility gate and optional launcher label patch.
- DEX extension with approval-first policy defaults, strict bounded tool proposal parser, and app-private workspace path guard.
- Default-off experimental hooks that add tool-format guidance to the inspected prompt serializer and collect parsed proposal envelopes from deserialized assistant content.
- A separate, default-off Native Agent Console Activity. When selected with the hook patch, it adds a second launcher entry for toggling session-only proposal mode, refreshing the proposal queue, copying arguments, dismissing proposals, or clearing the queue.
- Device-free Java regression tests for parsing, queue bounds, proposal snapshot/dismiss, execution policy, and path confinement.
- A manual verification workflow that applies selected patches to an exact hash-pinned APK and checks package, version, signing validity, manifest Activity, and merged extension DEX.

## Current Native Agent Console scope

The console is deliberately a proposal-review surface, not a command executor. No shell process, MCP call, network request, file write or automatic tool approval is implemented. The Dismiss action only removes the selected proposal. Agent/proposal mode is off by default and is session-only; it resets when the app process restarts. The separate launcher entry is a temporary integration point until the native Compose chat navigation and response-rendering lifecycle are verified.

The response hook observes a deserialized content field. It is not a verified incremental SSE callback or Compose renderer hook, and it does not provide reliable message-to-tool-result handoff. This branch is a patch and native review-console foundation, not a complete ChatGPT/Claude-like agent.

See docs/AGENT-RUNTIME-FOUNDATION.md, docs/NATIVE-AGENT-CONSOLE.md and docs/FEATURE-PORT-MAP.md.

## Build

Requirements: JDK 21, Gradle 9.8.2 and access to the Morphe GitHub Packages registry.

    bash scripts/test-agent-core.sh
    gradle --no-daemon buildAndroid

The MPP bundle is produced in patches/build/libs/. For a test build select these patches in order:

1. DeepSeek 2.7.4 compatibility check
2. Native Agent hook points (experimental)
3. Super DeepSeek native Agent Console

The hook and console patches are disabled by default and only target the exact inspected APK version.

## APK apply verification status

The sample APK analysis is for DeepSeek 2.7.4. The official download URL is a moving latest-version endpoint; a recent workflow downloaded a different binary and stopped at the SHA-256 check before applying any patch. This is intentional: never apply old R8 fingerprints to an uninspected version.

Dispatch the Verify Morphe patch against official DeepSeek APK workflow from branch work/morphe-foundation and provide a stable HTTPS URL plus SHA-256 for the exact inspected 2.7.4 sample. The workflow deletes temporary APKs at job completion and does not publish them.

## Security and identity

- Preserve official account sign-in and session behaviour.
- Do not bypass CAPTCHA, anti-fraud, anti-tamper, signature, root-detection, device-integrity or risk checks.
- Do not intercept TLS, install user certificates, extract session tokens or rewrite authentication requests.
- Model output is untrusted. Tool proposals are never executed by this branch.
- A re-signed APK will not use DeepSeek's original certificate and may not update the official installation in place. Keep the official installation and its data intact until a separate build is verified.
- PRoot, if introduced later, runs under the app UID and is not a kernel-level security boundary.
- Check applicable app terms and third-party licenses before distributing patches or modified binaries.

## Project map

- Architecture
- Target APK evidence
- Feature port map
- Agent runtime foundation
- Native Agent Console
- Security boundaries
- Build and verification
