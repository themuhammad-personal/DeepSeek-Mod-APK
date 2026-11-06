# DeepSeek Mod APK

A custom Morphe Patcher project for building native Android feature patches for the official DeepSeek application.

The user's `Super-Deepseek-` project is used as the **feature and UX reference**, not as a bundle of web code to inject into the native app. Browser-only modules are redesigned as Android-native DEX extensions and narrow Morphe patches.

> Independent project. Not affiliated with or endorsed by DeepSeek or Morphe. The official DeepSeek APK, decoded sources, credentials and signing keys are not committed.

## Current target

| Property | Inspected sample |
|---|---|
| Package | `com.deepseek.chat` |
| Version | `2.7.4` (`versionCode 293`) |
| Supported target | `2.7.4` only |
| APK SHA-256 | `a868bad448c87444f6c825b5ddbfa23756ad34178ab08503d107ff8e29b4f022` |

See [target APK evidence](docs/TARGET-APK.md) before supporting another release.

## Implemented in this phase

- Morphe Patcher project and `.mpp` bundle CI.
- Structural fingerprints for the exact prompt-field serializer call and response content deserializer result.
- Compatibility gate and optional launcher label change.
- DEX extension with approval-first policy defaults, bounded tool-call model, strict proposal parser and workspace path guard.
- A default-off experimental patch that wires the two verified method seams to native request/response hooks.
- Device-free Java tests for the parser, queue, safety policy and path guard.
- Device-free Java regression tests for strict JSON parsing, opt-in hooks, queue limits, execution policy and workspace path validation.
- A second CI workflow that downloads the pinned official sample, uses Morphe `--validate` plus Android SDK verification, applies the actual `.mpp` with Morphe Desktop, then verifies package/version, signature and merged extension DEX. The APK is temporary and is not uploaded or committed.

## Important current limitation

The experimental response hook observes the deserialized message content. It is **not** a verified SSE streaming callback or Compose renderer hook. The current branch does not execute shell commands, call MCP, display a native approval UI or feed tool results back to the model. The native proposal queue is in-memory. Therefore, this is a verified patch/integration foundation, **not yet a complete ChatGPT/Claude-like agent**.

See [Native Agent Runtime Foundation](docs/AGENT-RUNTIME-FOUNDATION.md) and [Feature Port Map](docs/FEATURE-PORT-MAP.md).

## Build

Requirements: JDK 21, Gradle 9.8.2 and access to the Morphe GitHub Packages registry.

Set `gpr.user` and `gpr.key` in Gradle user properties, or use the GitHub Actions `GITHUB_TOKEN` in CI. Then run:

```bash
bash scripts/test-agent-core.sh
gradle --no-daemon buildAndroid
```

The `.mpp` bundle is produced in `patches/build/libs/`. Select the compatibility patch first. The experimental native agent hook patch is **disabled by default** and should only be selected on the exact inspected target while testing.

## Real patch verification

GitHub Actions workflow `Verify Morphe patch against official DeepSeek APK` downloads the official 2.7.4 sample, checks its pinned SHA-256, applies the MPP using a pinned Morphe Desktop CLI, and verifies the resulting APK. Temporary APKs are deleted at the end of the job; they are not release assets.

## Security and identity

- Preserve official account sign-in and session behaviour.
- Do not bypass CAPTCHA, anti-fraud, anti-tamper, signature, root-detection, device-integrity or risk checks.
- Do not intercept TLS, install user certificates, extract session tokens or rewrite authentication requests.
- Model output is untrusted input. Tool proposals are never executed by the current extension.
- A re-signed APK will not use DeepSeek's original certificate and may not update the official installation in place. Keep the original installation and its data intact until separately verified.
- Check applicable app terms and third-party licenses before distributing any patch or modified binary.

## Project map

- [Architecture](docs/ARCHITECTURE.md)
- [Target APK evidence](docs/TARGET-APK.md)
- [Feature port map](docs/FEATURE-PORT-MAP.md)
- [Agent runtime foundation](docs/AGENT-RUNTIME-FOUNDATION.md)
- [Security boundaries](docs/SECURITY-BOUNDARIES.md)
