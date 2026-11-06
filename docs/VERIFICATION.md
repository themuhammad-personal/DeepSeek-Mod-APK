# Build and patch verification

Two separate CI checks are used:

1. `Build Morphe patch bundle` builds the `.mpp` and `.mpe` files. This check is triggered by pushes to the working branch.
2. `Verify Morphe patch against official DeepSeek APK` is manually dispatched with an HTTPS APK URL and the expected SHA-256. It runs the device-free Java regression test, verifies the APK checksum and exact `com.deepseek.chat 2.7.4 (versionCode 293)` identity, builds the MPP, runs Morphe fingerprint validation and Android SDK verification, applies only the compatibility and experimental hook patches with Morphe Desktop 2.1.1, and verifies the resulting APK package/version/signature and merged DEX class/hook references.

The official download URL is a moving latest-version endpoint. When it stops serving the inspected 2.7.4 sample, the old SHA check intentionally fails before Morphe touches the APK. Supply a stable HTTPS location for the exact 2.7.4 APK and its SHA-256 to the manual workflow. Never replace the pinned target hash with the hash of an uninspected release.

The original and patched APKs are temporary CI files and are deleted at job completion. They are not uploaded as release assets. This is a static/build-time integration check only. It does not prove app installation, account login, CAPTCHA/risk behaviour, runtime callback frequency, Compose rendering or real-device execution.

## Results from this phase

- `scripts/test-agent-core.sh`: PASS (25 checks).
- Morphe `buildAndroid`: PASS; `.mpp` artifact uploaded by GitHub Actions.
- Official APK apply: **not yet verified**. The live download endpoint returned a binary with a different SHA-256 from the supplied 2.7.4 sample; the workflow stopped safely before applying the patch.

Local agent-core regression test:

```bash
bash scripts/test-agent-core.sh
```

MPP build:

```bash
gradle --no-daemon buildAndroid
```
