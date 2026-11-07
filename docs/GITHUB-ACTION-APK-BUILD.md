# Build an installable APK with GitHub Actions

The branch `apk-build/8bb57a23-deepseek-mod-apk` adds a separate workflow that builds a signed APK and uploads it as a short-lived GitHub Actions artifact. It does not modify `main` or the feature branch.

## One-time source setup

The workflow never stores an official DeepSeek APK or extracted third-party source in the repository. Configure these repository Actions secrets:

- `SDS_SOURCE_URL`: an HTTPS direct-download URL to your exact source file.
- `SDS_SOURCE_SHA256`: its exact 64-character SHA-256.
- `SDS_SOURCE_FORMAT`: `zip`, `apkm` or `apk`.

The supplied source must correspond to an inspected build. The patcher reads the APK version code and refuses unknown versions.

| Source format | What it contains | Intended inspected build |
|---|---|---|
| `zip` | APK extraction ZIP with AndroidManifest.xml, DEX files and resources | 2.7.4 / versionCode 293 |
| `apkm` | Split package containing `base.apk` | 2.7.4 / 294 or 2.7.5 / 296 |
| `apk` | A single inspected base APK | Any inspected profile: 293, 294 or 296 |

For the already supplied 293 extraction ZIP, the SHA-256 is:

```text
b0ec418ec9b8a273479acd8fed73acefbdbddd34162684c50e1737abb70d74de
```

The 294 APKM reviewed earlier had SHA-256:

```text
8c3f9665e81c4c7bb76547f70c4d5e52ccc31d981e66c8121ad9f218b646f1ae
```

The 296 source must use the SHA-256 of the exact owner-supplied APK/APKM. Never copy a hash from a different build.

In GitHub, open **Settings → Secrets and variables → Actions → New repository secret** and create the source secrets above. The source URL must be directly downloadable by the runner; a link that requires signing into a Google account will not work.

## Signing key: recommended before the first APK

To make future APKs install as updates, keep one signing key and reuse it. Generate a JKS key on a computer or Termux environment with Java installed:

```bash
keytool -genkeypair -v \
  -keystore deepseek-mod-release.jks \
  -storetype JKS \
  -alias deepseek-mod \
  -keyalg RSA -keysize 3072 -validity 10000
base64 -w 0 deepseek-mod-release.jks > deepseek-mod-release.base64.txt
```

Add these repository Actions secrets:

- `SDS_SIGNING_KEY_BASE64`: the one-line contents of `deepseek-mod-release.base64.txt`.
- `SDS_KEYSTORE_PASSWORD`: the keystore password chosen during key creation.
- `SDS_KEY_PASSWORD`: the key password.
- `SDS_KEY_ALIAS`: `deepseek-mod` (or the alias you chose).

The workflow decodes the key into the temporary runner workspace, uses it to sign the APK, and does not upload the key. Do not commit or publish the JKS or its Base64 text.

If these signing secrets are not configured, the workflow creates a one-run temporary key. The APK will install, but a later build will not update it; Android will require uninstalling the previous modified build, which can erase its app data.

## Start or repeat a build

A push to this dedicated branch starts the workflow. If the source secrets are missing, the run will finish with a notice and no APK; add the secrets and use **Actions → Build installable DeepSeek APK → Re-run all jobs**.

Workflow URL:

https://github.com/themuhammad-personal/DeepSeek-Mod-APK/actions/workflows/build-patched-apk.yml

The workflow runs the native regression tests, builds the MPP, downloads and hash-checks the source, checks the target fingerprints, applies all four patches, signs the output and verifies the patched DEX. It uploads:

- `deepseek-patched.apk`
- `deepseek-patched.apk.sha256`

Open the successful run and download the `deepseek-patched-apk` item under **Artifacts**. The artifact expires after one day.

## Important limitations

- This workflow builds a patched APK, not the original DeepSeek-signed package. It cannot replace the official app in-place because the signing certificates differ.
- An APK extracted from an APKM is the patched `base.apk`; optional configuration splits from the APKM are not bundled into a universal APK.
- Linux Studio still requires the separate, checksum-pinned PRoot runtime libraries. They are not included in this build.
- The APK has not been device-runtime-verified. Test it separately before relying on login, streaming, agent handoff, MCP or Linux Studio.
- This repository is public. GitHub says anyone with read access to a repository can download workflow artifacts; therefore the APK artifact is accessible to the public while retained. Retention is limited to one day. Do not use this artifact workflow for private binaries; use a private repository for that.
