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

- Only 2.7.4 is declared as a supported target.
- Do not use catch-all compatibility for future versions.
- Re-inspect method descriptors and instruction context for each new DeepSeek release before adding an AppTarget.
