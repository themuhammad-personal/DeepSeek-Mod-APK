# Build and patch verification

Two separate CI checks are used:

1. `Build Morphe patch bundle` builds the `.mpp` and `.mpe` files.
2. `Verify Morphe patch against official DeepSeek APK` runs the device-free Java regression test, downloads the pinned official 2.7.4 APK, verifies its SHA-256, builds the same MPP, runs Morphe fingerprint validation and Android SDK verification, applies only the compatibility and experimental hook patches with Morphe Desktop 2.1.1, and verifies the resulting APK package/version/signature and merged DEX class/hook references.

The original and patched APKs are temporary CI files and are deleted at job completion. They are not uploaded as release assets. This is a static/build-time integration check only. It does not prove app installation, account login, CAPTCHA/risk behaviour, runtime callback frequency, Compose rendering or real-device execution.

Local agent-core regression test:

```bash
bash scripts/test-agent-core.sh
```

MPP build:

```bash
gradle --no-daemon buildAndroid
```
