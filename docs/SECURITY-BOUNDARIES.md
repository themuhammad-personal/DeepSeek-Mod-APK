# Security and compatibility boundaries

## Authentication and app integrity

- Preserve the official account sign-in and session flow.
- Do not bypass CAPTCHA, anti-fraud, anti-tamper, signature, root-detection, device-integrity or risk checks.
- Do not intercept TLS, install user certificates, extract session cookies/tokens or rewrite private authentication requests.
- Never log authorization headers, cookies, refresh tokens, personal chat content or MCP credentials.
- If the official app blocks a modified build or presents a risk/integrity failure, stop and report the exact non-sensitive error.

## Native agent and command execution

A model response is untrusted input. The future agent must not execute arbitrary shell commands automatically.

Required before enabling command execution:

1. Explicit user approval by default; separately configurable trusted policies only after implementation.
2. Per-command timeout, cancellation and bounded stdout/stderr.
3. Workspace confinement and safe path resolution; reject traversal and symlink escapes.
4. CPU, memory, disk and process limits where the Android environment supports them.
5. Network access disabled by default or controlled by an explicit allowlist.
6. Clear disclosure that PRoot is not a kernel security boundary and runs under the app UID.
7. No hidden background execution; show active task state and provide a reliable Stop action.

## Distribution

- Do not commit the official APK, extracted third-party code, signing keys or generated patched APKs.
- A re-signed patched APK will not use DeepSeek's original certificate. Android may refuse an in-place update over the official installation. Do not uninstall the user's official app or erase its data as an automated workaround.
- Check applicable app terms and third-party licenses before distributing patches or modified binaries.
