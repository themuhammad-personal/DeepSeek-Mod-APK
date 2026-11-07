# Final build — DeepSeek 2.7.5 / versionCode 296

Base branch: \`arena/8bb57a23-deepseek-mod-apk\`  
Base commit: \`f1d2da85bc3ae77ce657dc8771b82a69ed674528\`  
Final branch: \`final/2.7.5-296-split-apk\`

This branch starts from the newest arena commit and keeps its latest feature work, fixes and 296 inspection. It adds a workflow for the exact Play Store split package supplied in this conversation.

## Source archive verified

Uploaded file: \`DeepSeek_2.7.5.apks\`

- Archive SHA-256: \`02e6dafdb37fb2be754d4dfd428d2cb9767a610f4de1fa30bce21f11dc14cd8c\`
- Members: \`base.apk\`, \`split_config.arm64_v8a.apk\`, \`split_config.xhdpi.apk\`
- Base SHA-256: \`6befd98c06155b2b4583f57cd76d7b721e342fed77025a94b653e03e18b5c8a8\`
- ARM64 split SHA-256: \`4f9bbc031c799450cee4100687514e4b1dae8d082816f823840fdd2cd3344051\`
- xhdpi split SHA-256: \`3b7ce2d70fe06334bd533c04ef6d1f97a1a0d741a17eef230985a241c9b5cb7a\`

The base hash matches the versionCode 296 base APK recorded in the arena branch. This is the exact target: package \`com.deepseek.chat\`, version \`2.7.5\`, versionCode \`296\`. It requires Android API 32 or newer.

The separately rebuilt APK you mentioned is not needed as an input: the original archive contains all three matching Play Store splits, and its base matches the recorded 296 source.

## Why the output is an APKS set

Returning only the patched \`base.apk\` would omit the ARM64 native-library and xhdpi configuration splits. The new workflow therefore:
1. verifies the outer archive hash and all three inner APK hashes;
2. checks package/version metadata for each APK;
3. verifies the 296 fingerprints against base.apk;
4. builds the Morphe bundle and applies all four native patches to base.apk;
5. runs hook-site, login-untouched and native-only static checks;
6. re-signs the patched base and both configuration splits with one certificate;
7. repackages the three APKs into a new \`.apks\` file and verifies the signer matches across the set.

The original Play signing certificate cannot be preserved after modifying the app. All APKs in the output set receive the same owner-controlled signature.

## Run the GitHub Actions workflow

Workflow:
https://github.com/themuhammad-personal/DeepSeek-Mod-APK/actions/workflows/build-deepseek-296-split.yml

Select branch \`final/2.7.5-296-split-apk\`, then **Run workflow**.

The \`source_sha256\` field is prefilled with the exact archive hash above. The \`source_url\` field needs a direct HTTPS download link to the same \`.apks\` file. The uploaded chat attachment itself is not directly accessible to GitHub-hosted runners. Put the same file at an owner-controlled HTTPS location that permits direct unauthenticated download, then paste that direct-download URL into the workflow input.

Do not change the hash unless the source file changes. A changed base or split is rejected by the inner hash checks.

## Persistent signing key

To install future builds as updates without uninstalling the current modified app, configure these Actions secrets **before the first installation**:
- \`SDS_SIGNING_KEY_BASE64\`
- \`SDS_KEYSTORE_PASSWORD\`
- \`SDS_KEY_PASSWORD\`
- \`SDS_KEY_ALIAS\`

Generate a JKS key on a trusted computer with Java:
\`\`\`bash
keytool -genkeypair -v -keystore deepseek-mod-release.jks \\
  -storetype JKS -alias deepseek-mod \\
  -keyalg RSA -keysize 3072 -validity 10000
base64 -w 0 deepseek-mod-release.jks > deepseek-mod-release.base64.txt
\`\`\`

Keep the key and passwords private; never commit them. If these secrets are absent, the workflow creates a temporary one-run key. That build installs, but a later different key cannot update it and Android may require uninstalling the previous modified app, which can remove local app data.

## Download and install

A successful run uploads artifact \`deepseek-mod-2.7.5-296-apks\`. Download it from the Actions run's **Artifacts** section and extract:
- \`deepseek-mod-2.7.5-296.apks\`
- \`deepseek-mod-2.7.5-296.apks.sha256\`

Install the split set with SAI (Split APKs Installer), or with Android platform tools:
\`\`\`bash
adb install-multiple -r base.apk split_config.arm64_v8a.apk split_config.xhdpi.apk
\`\`\`

Because the APKs are re-signed with an owner key, they cannot update the official Play Store app in place. Back up important data before removing the official app. The static checks are not a substitute for first-launch, sign-in, chat-streaming and Agent device tests.

The repository is public, so the workflow artifact is downloadable by repository readers while retained (seven days). No original APK or signing key is committed to the repository.
