# DeepSeek Mod APK

A custom Morphe Patcher project for building native Android feature patches for the official DeepSeek application.

The user's Super-Deepseek- repository is the feature and UX reference, not source code to copy into the APK. Browser-only modules are redesigned as Android-native DEX extensions and narrow Morphe patches. **No JavaScript, CSS or WebView content is injected into the app.**

> Independent project. Not affiliated with or endorsed by DeepSeek or Morphe. The official DeepSeek APK, decoded sources, credentials and signing keys are not committed.

## Inspected target

| Property | Inspected sample |
|---|---|
| Package | com.deepseek.chat |
| Version | 2.7.4 (versionCode 293) |
| Supported target | 2.7.4 only |
| APK SHA-256 | a868bad448c87444f6c825b5ddbfa23756ad34178ab08503d107ff8e29b4f022 |

See docs/TARGET-APK.md before supporting another release.

## What is implemented

**Native runtime (DEX extension `extensions/sds-core`)**

- The agent runtime: a session state machine with proposals, approval, deny, cancel and a stop control, an activity timeline and bounded diagnostics.
- An allow-listed tool executor with a strict schema per tool, an explicit approval, a timeout, cancellation, bounded output and the workspace as the only place a file tool may touch.
- The built-in file tools: list, read and write inside the app-private workspace, with canonical path confinement.
- The memories, the projects, the slash commands and the Generative UI surface.
- The artifacts and the export through the system file picker and the system share sheet.
- The MCP client: JSON-RPC 2.0 over https, no redirect, a bounded answer, the tools of a server as native tools.
- The research retrieval: one https address at a time, shown to the user before it is fetched.
- The Linux Studio: a code-only module that refuses to run until the PRoot runtime is bundled. PRoot runs under the app UID and is **not** a kernel sandbox.

**Morphe patches (`patches/`)** — a compatibility gate that proves the shape of the three seams before anything is touched, and three default-off runtime patches: the hooks, the console and the entry in the chat screen.

**Verification** — a plain-Java regression test of the runtime, a build of the patch bundle and a verification that applies the patches to a hash-pinned target and inspects the patched DEX.

## What is not proven

The runtime is **statically traced, build-verified and patch-applied**. It is **not device-runtime-verified**: the callback frequency of the observer, the behaviour of the Compose renderer, the login flow and the touch behaviour of the chat entry still need a real device with the official 2.7.4 and an account. The Linux Studio cannot run until the PRoot binaries and the root filesystem are bundled, and the exact 2.7.4 binary is not obtainable from the public endpoint any more, so the apply verification waits for the owner-supplied pinned source.

## Build

Requirements: JDK 21, Gradle 9.8.2 and access to the Morphe GitHub Packages registry.

    bash scripts/test-agent-core.sh
    gradle --no-daemon buildAndroid

The MPP bundle is produced in patches/build/libs/. For a test build select these patches in order:

1. DeepSeek 2.7.4 compatibility check
2. Native Agent hook points (experimental)
3. Super DeepSeek native Agent Console
4. Super DeepSeek native Agent chat entry

The three runtime patches are disabled by default and only target the exact inspected APK version.

## Build the patched APK on your own machine

The CI builds the patched APK, checks it and deletes it, so it never publishes
a binary. To get a file you can install, run the local builder:

    bash scripts/build-patched-apk.sh --mpp <bundle.mpp>
    bash scripts/build-patched-apk.sh --source-file <extraction.zip>   # a file you already have

It needs a JDK 21, python3, curl, unzip and the Android SDK with the build
tools 36.0.0. Without `--mpp` it builds the bundle with gradle, which needs
access to the Morphe package registry; the bundle of the last CI run can also
be downloaded from the run page of the build workflow.

The result is `build-local/deepseek-patched.apk`, together with its SHA-256
and the proof of the hook call sites. The signing key is generated on your
machine and stays there.

## APK apply verification

The exact source APK must match the inspected 2.7.4 SHA-256. The current official download URL is a moving latest-version endpoint and has returned a different binary, so the verification workflow safely stops before applying old fingerprints. Supply a stable HTTPS URL and SHA-256 for the exact inspected APK; never change the hash to match an uninspected release.

The verification workflow deletes the temporary APKs after checking them and does not publish them.

## Security and identity

- Preserve official account sign-in and session behaviour.
- Do not bypass CAPTCHA, anti-fraud, anti-tamper, signature, root-detection, device-integrity or risk checks.
- Do not intercept TLS, install user certificates, extract session tokens or rewrite authentication requests.
- Model output is untrusted. No tool runs without an explicit approval of the user.
- The Agent Console is not exported and has no launcher entry, so no other app can open it.
- The network is off by default, only https is used and a redirect is never followed.
- A re-signed APK will not use DeepSeek's original certificate and may not update the official installation in place. Keep the official installation and its data intact until a separate build is verified.
- PRoot runs under the app UID and is not a kernel-level security boundary.
- Check applicable app terms and third-party licenses before distributing patches or modified binaries.

## Project map

- docs/ARCHITECTURE.md
- docs/TARGET-APK.md
- docs/DEEPSEEK-STREAM-TRACE.md
- docs/FEATURE-PORT-MAP.md
- docs/AGENT-RUNTIME-FOUNDATION.md
- docs/NATIVE-AGENT-CONSOLE.md
- docs/NATIVE-FEATURE-MODULES.md
- docs/SECURITY-BOUNDARIES.md
- docs/VERIFICATION.md
- docs/P0-VERIFICATION.md
- docs/FINGERPRINTS.md
- docs/DEVICE-TEST-CHECKLIST.md
- docs/FINAL-REVIEW.md
