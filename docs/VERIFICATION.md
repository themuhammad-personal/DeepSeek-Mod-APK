# Build and patch verification

Two independent CI workflows are used.

1. Build Morphe patch bundle runs the Java agent-core regression tests and builds the MPP/MPE bundle on pushes to the working branch.
2. Verify Morphe patch against official DeepSeek APK is manually dispatched with an HTTPS URL and SHA-256 for the exact inspected APK. It checks package/version, builds the MPP, validates fingerprints, applies the compatibility, experimental hook and optional native console patches, then verifies the resulting APK signature, launcher Activity in the manifest and merged extension DEX classes.

The live DeepSeek download URL is a moving latest-version endpoint. When it stops serving the inspected 2.7.4 sample, the SHA check intentionally fails before Morphe applies any patch. Supply a stable HTTPS source for the exact sample and its known SHA-256; do not change the pinned hash to match an uninspected version.

The original and patched APKs are temporary CI files and are deleted at job completion. They are not uploaded as release assets. This is static/build-time integration evidence only. It does not prove app installation, account login, CAPTCHA/risk behaviour, correct callback frequency, threading, Compose rendering or real-device execution.

## Current CI evidence before the streaming-hook update

- The prior Native Agent Console code passed the device-free Java tests.
- Morphe buildAndroid completed and uploaded an MPP artifact.
- The manual APK verification workflow stopped at the source APK SHA-256 check because the moving download endpoint returned a different binary. Morphe apply and patched APK checks were therefore skipped.

The streaming-hook update replaces the previous static response-fragment deserializer fingerprint with the mutable ResponseFragment SET/APPEND methods. A new successful MPP build is required before treating that update as build-verified.

## Local checks

    bash scripts/test-agent-core.sh
    gradle --no-daemon buildAndroid

## Manual device checks still required

- Obtain the exact inspected 2.7.4 APK and run the manual Morphe apply verification.
- Install a separately patched APK without uninstalling or erasing the user's official DeepSeek installation.
- Confirm the original DeepSeek MainActivity and login/session flow remain unchanged.
- Enable proposal mode and verify that the SET/APPEND observer receives full accumulated assistant content only as expected.
- Confirm proposals are not executed, Dismiss removes them, and the Console is usable across rotation.
- Trace and verify the Compose navigation/message renderer before moving the Console into the main chat UI.
