# Final build — exact owner-provided single APK

Final branch: `final/2.7.5-296-split-apk`  
Source: the Google Drive file ID explicitly supplied by the owner: `1r4__RT5nFRmkfxHuK4sLWgGeNXGKbS3o`.

## Input restriction

The build workflow downloads **only that single Drive APK**. It does not use the earlier chat-uploaded APKS archive, any alternate APK, an APKMirror package, an extracted APK from another source, or any source APK committed to the repository.

The Drive link is converted to a direct download URL within GitHub Actions. The runner validates that the response is an APK (ZIP with AndroidManifest.xml), then checks:
- package: `com.deepseek.chat`
- version name: `2.7.5`
- version code: `296`
- no split attribute (the workflow accepts one standalone APK only).

If Drive does not permit anonymous download, if the URL returns an HTML confirmation/error page, or if the linked APK is a split rather than a standalone APK, the job stops. It does not fall back to a different APK.

After downloading, the build verifies the version-296 fingerprint set against this exact APK. A fingerprint mismatch stops patching, which prevents accidental application of another build's offsets or method names.

## Build process

1. Download the exact file ID supplied by the owner.
2. Verify APK container, package, version and standalone status.
3. Verify the build-296 fingerprints.
4. Build the Morphe patch bundle from this repository branch.
5. Apply the four existing native patches to that one APK.
6. Run hook-site, login-untouched, native-only and DEX verification.
7. Re-sign the patched APK with an owner-controlled key, or create a temporary key if secrets are absent.
8. Upload only the patched APK and its SHA-256 checksum as the Actions artifact.

The original Play signing certificate cannot be kept after modifying the APK. The patched APK therefore cannot update the official Play Store installation in place.

## Persistent signing key

For repeatable updates, set these GitHub Actions secrets before installing the first modified build:
- `SDS_SIGNING_KEY_BASE64`
- `SDS_KEYSTORE_PASSWORD`
- `SDS_KEY_PASSWORD`
- `SDS_KEY_ALIAS`

Generate a JKS on a trusted computer:
```bash
keytool -genkeypair -v -keystore deepseek-mod-release.jks \
  -storetype JKS -alias deepseek-mod \
  -keyalg RSA -keysize 3072 -validity 10000
base64 -w 0 deepseek-mod-release.jks > deepseek-mod-release.base64.txt
```

Never commit the keystore or its passwords. Without these secrets the workflow uses a temporary one-run key; another build will have a different signature and cannot update the installed copy.

## Output and installation

A successful run publishes artifact `deepseek-mod-2.7.5-296-apk`, containing:
- `deepseek-mod-2.7.5-296.apk`
- `deepseek-mod-2.7.5-296.apk.sha256`

Install the APK normally. Back up app data before uninstalling the official Play Store version, because the signing certificate changes.

Static checks do not prove device runtime behaviour. First launch, sign-in, chat streaming and Agent interactions still need physical-device testing.
