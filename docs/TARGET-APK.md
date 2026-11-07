# Target APK — static inspection record

## Inspected sample

| Field | Value |
|---|---|
| Application ID | com.deepseek.chat |
| Version name | 2.7.4 |
| Version code | 293 |
| Minimum SDK | 24 |
| Target SDK | 36 |
| DEX files | classes.dex, classes2.dex, classes3.dex |
| Native ABI in supplied sample | arm64-v8a |
| APK SHA-256 | a868bad448c87444f6c825b5ddbfa23756ad34178ab08503d107ff8e29b4f022 |
| APK size | 22,198,932 bytes |

The APK and decoded third-party sources are not committed. Keep local inputs in an ignored folder and verify their hashes before every analysis/build.

## Second inspected sample

| Field | Value |
|---|---|
| Application ID | com.deepseek.chat |
| Version name | 2.7.4 |
| Version code | 294 |
| Minimum SDK | 29 |
| Target SDK | 36 |
| DEX files | classes.dex, classes2.dex, classes3.dex, classes4.dex |
| Distribution | mirrored split package, signed with the Play App Signing key |
| Source | the owner's own copy of `com.deepseek.chat_2.7.4_apkmirror.com.apkm` |

This is the same app version, but a different build: it is obfuscated on its
own, so every class name of the build with the version code 293 changed. It
therefore has its own fingerprint set, which is described in
[FINGERPRINTS.md](FINGERPRINTS.md). The patches were not applied to it and it
was not run on a device, so its target is declared as experimental.

## Statically identified method seams

### Request prompt serializer

- Class descriptor: Ljy1;
- Method: e(Lw94;Ljava/lang/Object;)V
- Request model: Lly1; (ChatFullCompletionRequest)
- Prompt field: c:Ljava/lang/String;
- Serialization path: Ly53;->x(Lsr9;ILjava/lang/String;)V
- The prompt is loaded into v8 immediately before the string serializer call.

### Response content deserializer

- Class descriptor: Lefa;
- Method: d(Lrn3;)Ljava/lang/Object;
- Content read: Lx53;->m(Lsr9;I)Ljava/lang/String;
- Result is assigned to the response-fragment model after move-result-object v9.

## Evidence level and limits

These are static findings from the supplied apktool decode and PoC seam notes. The Morphe fingerprints are intended to prove the same methods can be resolved against the exact 2.7.4 sample.

They do not prove that the network stream, incremental SSE handling, Compose message renderer, or a real model-to-tool-result turn has been integrated. That work requires separate source tracing and on-device tests.

## Version policy

- Only 2.7.4 is declared as a supported target, with the two inspected builds
  293 (stable) and 294 (experimental).
- Do not use catch-all compatibility for future versions.
- Re-inspect method descriptors and instruction context for each new DeepSeek release before adding an AppTarget.
- Every build needs its own fingerprint set, and the set is selected by the
  version code of the APK that is patched. See [FINGERPRINTS.md](FINGERPRINTS.md).
