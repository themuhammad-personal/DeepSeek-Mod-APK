# Build and patch verification

Two independent CI workflows are used.

1. Build Morphe patch bundle runs the Java agent-core regression tests and builds the MPP/MPE bundle on pushes to the working branch.
2. Verify Morphe patch against official DeepSeek APK is manually dispatched with an HTTPS URL and SHA-256 for the exact inspected APK. It checks package/version, builds the MPP, validates the fingerprints, applies the compatibility, experimental hook and optional native console patches, then verifies the resulting APK signature, launcher Activity in the manifest and merged extension DEX classes.

The live DeepSeek download URL is a moving latest-version endpoint. When it stops serving the inspected 2.7.4 sample, the SHA check intentionally fails before Morphe applies any patch. Supply a stable HTTPS source for the exact sample and its known SHA-256; do not change the pinned hash to match an uninspected version.

The original and patched APKs are temporary CI files and are deleted at job completion. They are not uploaded as release assets. This is static/build-time integration evidence only. It does not prove app installation, account login, CAPTCHA/risk behaviour, correct response-hook frequency, Compose rendering or real-device execution.

## Current CI evidence

- Native core Java regression test passed in the latest MPP run.
- Morphe buildAndroid completed and uploaded the MPP artifact.
- The manual APK verification workflow stopped at the source APK SHA-256 check because the moving download endpoint returned a different binary. Morphe apply and patched APK checks were therefore skipped.
- After adding the separate Agent Console, the next manual APK verification must additionally confirm its exported launcher Activity in the final manifest and the Activity class in the merged DEX.

## Local checks

    bash scripts/test-agent-core.sh
    gradle --no-daemon buildAndroid

## Manual device checks still required

- Install a separately patched APK without uninstalling or erasing the user's official DeepSeek installation.
- Confirm launcher Activity starts and renders on the target Android version.
- Confirm the original DeepSeek MainActivity and login/session flow remain unchanged.
- Enable proposal mode, send a test chat, refresh the console, and check whether the inspected content-deserializer callback actually receives final assistant content.
- Check that proposals are not executed, that Dismiss removes them, and that the console survives rotation without losing its UI state.
