# DeepSeek 2.7.5 / versionCode 296 — final single-APK build

## Source restriction

This branch patches only the standalone APK downloaded from the exact Google Drive file ID supplied by the owner:
`1r4__RT5nFRmkfxHuK4sLWgGeNXGKbS3o`.

Expected source SHA-256:
`fd2f7c5479f8e784d22724ea45ac7a3ae91904437591ae8337125194c11a3bfa`

Package: `com.deepseek.chat`; version: `2.7.5`; versionCode: `296`.

No other APK/APKS/archive is used as a fallback. If the Drive file is changed, not downloadable, not a standalone APK, or does not match version 296 fingerprints, the build stops.

## Why this fix branch exists

The first device report showed outgoing user messages with retry icons, but no assistant response. The Agent Console also showed an IDLE session and zero response-observer callbacks. This means the static CI proof was not enough to establish runtime send/stream behaviour.

The branch `fix/296-chat-send-fail-open` adds defensive behaviour:
- `beforePrompt` now fails open and returns the original prompt if optional Agent processing throws.
- response diagnostics and proposal parsing are isolated so an Agent error cannot interrupt the official response stream.
- the Console now refreshes diagnostics live and shows request-hook calls and fail-open count alongside response observer callbacks.

This reduces the risk that an experimental native Agent hook can block normal chat. It does not bypass server checks or establish why the first device build failed. A physical-device test is still required.

## Persistent signing identity: superdeepSeek

The workflow now requires a persistent owner-controlled signing key and no longer generates a temporary signing key. The alias is exactly `superdeepSeek`.

Before triggering the APK workflow, add these GitHub Actions repository secrets:
- `SDS_SIGNING_KEY_BASE64`
- `SDS_KEYSTORE_PASSWORD`
- `SDS_KEY_PASSWORD`
- `SDS_KEY_ALIAS` = `superdeepSeek`

Also add this repository variable:
- `SDS_296_SIGNING_READY` = `true`

The workflow job is skipped until that variable is set. This prevents a build from producing another APK with an accidental one-run key.

Keep the JKS and passwords in a secure backup. Never commit the keystore or secrets. Losing the key means future builds cannot update an app signed with it.

Workflow: `.github/workflows/build-deepseek-296-single-apk.yml`  
Trigger: manual `workflow_dispatch` or a push to this fix branch. The job is skipped until repository variable `SDS_296_SIGNING_READY=true`.

The workflow downloads the pinned Drive APK and produces two outputs, both signed with the exact same persistent certificate:
- `deepseek-baseline-2.7.5-296.apk`: the same Drive APK with no Morphe patches (A/B baseline).
- `deepseek-mod-2.7.5-296.apk`: the APK with all four Morphe patches, plus hook/login/native verification.

Install the baseline first and test a simple chat message. Then install the patched APK as an update (the signing certificate is the same, so there is no need to uninstall the baseline). If baseline chat works but patched chat fails, the patch path is responsible. If both fail, investigate the signature/server/network/device path instead of changing the Agent UI blindly.

## Important: previous install signature

The already-installed APK from run `42394660963` was signed with a one-run temporary key. It cannot be updated by this new `superdeepSeek` identity. Back up anything important, uninstall that temporary-signature build, then install the first APK signed with the permanent key. Subsequent builds using the same JKS will install as updates.

## Runtime diagnosis

Before testing Agent proposals, keep **Allow DeepSeek to propose native tools** turned off and send a new simple message. The chat should work with Agent disabled. If it still shows the retry icon, collect Android logcat for the failed send. The console's new diagnostics distinguish:
- request hook calls and fail-open count;
- response observer callbacks, which only increase after an answer stream reaches the hooked methods.

The Agent Console is a proposal/diagnostics screen, not a separate chat. Approve/Deny/Stop do not send a message. The proposal queue can only populate after a response contains a valid native tool proposal. Tool-result handoff remains disabled by default and has not been device-verified. Linux Studio runtime binaries are not bundled in this build.

## Install constraints

The official Play Store signature cannot be retained after patching. The modified APK uses the owner's `superdeepSeek` signer and cannot update the official installation in place. Back up local app data before uninstalling the official app. Login, chat streaming, and Agent behaviour must be tested on-device after installation.
