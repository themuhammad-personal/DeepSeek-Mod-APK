# P0 verification report — Morphe patches against the pinned DeepSeek 2.7.4 target

**Status:** patch-applied and statically verified in CI.
**Not** device-runtime verified: no Android device was available in this environment, so login,
chat, streaming and the Agent Console were **not** exercised on hardware.

Everything below is labelled with the level of evidence that it actually has.

---

## 1. Target under test

| Property | Value | Evidence |
| --- | --- | --- |
| Package | `com.deepseek.chat` | `aapt dump badging` on the rebuilt target and on the patched APK |
| Version | `2.7.4` | `aapt dump badging` |
| versionCode | `293` | `aapt dump badging` |
| minSdk / targetSdk | 24 / 36 | apktool decode of the pinned extraction |
| ABI | `arm64-v8a` | `aapt dump badging` |
| Official APK SHA-256 | `a868bad448c87444f6c825b5ddbfa23756ad34178ab08503d107ff8e29b4f022` (22,198,932 bytes) | owner-supplied pin, recorded in `docs/TARGET-APK.md` |

### 1.1 The official APK is not redistributable and was not obtained

`https://download.deepseek.com/apk/deepseek.apk` is a moving *latest* endpoint. It no longer
serves 2.7.4, so the pinned SHA-256 correctly fails against it. The hash was **not** changed to
make a download pass, and the workflow never silently accepts a different file: the bundle
checksum is compared byte for byte and the run fails on a mismatch.

The owner's Google Drive copy of the pinned extraction
(`com.deepseek.chat_2.7.4_apkmirror.com.apkm` and the raw extraction bundle) was used as the
source. The pinned **APK binary itself was never obtained**, so the target used for the apply
is a *reconstruction*, not the signed original.

### 1.2 The APKM in the Drive is a different build — the fingerprints were not applied to it

| Property | Pinned original (293) | Drive APKM (`base.apk`) |
| --- | --- | --- |
| versionCode | 293 | **294** |
| minSdk | 24 | 29 |
| Application class | `com.deepseek.chat` | `com.pairip.application.Application` |
| DEX files | 3 | 4 |
| SHA-256 | `a868bad4…f022` | `8c3f9665…f1ae` (APKM), `2e898602…a709` (base.apk) |

The R8 mapping changed between the two builds (`Lv44;`, `Ljy1;`, `Ldea;`, `Lly1;` exist in both
but with different members), and all five fingerprint methods that exist in the pinned target are
**absent** in the APKM. Applying 2.7.4 fingerprints to 2.7.4/294 would have patched the wrong
code, so the apply is performed against the reconstructed 293 target only.

---

## 1.3 The newest official build and the build of the patched APK

The official download address serves DeepSeek 2.7.5 with the version code 295
now. The workflow **Build the patched DeepSeek APK** builds the patched app of
exactly that file and keeps it as an artifact of the run.

| Property | Value | Evidence |
| --- | --- | --- |
| Package | `com.deepseek.chat` | `aapt dump badging` in the run `42364324070` |
| Version / versionCode | `2.7.5` / `295` | `aapt dump badging` |
| minSdk / targetSdk | 24 / 36 | `aapt dump badging` |
| Official APK SHA-256 | `ddc52ef868b991fd33e634de834a3e2c022b0cd7ff9e854f4f141996aaeadb70` (22,210,268 bytes) | the run `42363137135` of the workflow **Check the newest official DeepSeek release** |
| Patched APK | the artifact `deepseek-patched-apk` of the run `42364324070` (21,329,027 bytes) | the upload step of that run |
| Fingerprint set | `DeepSeekFingerprints295.kt` | `scripts/verify_fingerprints.py` in the run |
| Patches applied | the four patches of this project | the patch result JSON of the run |
| The login is untouched | passed | `scripts/verify_login_untouched.py` in the run |
| The app stays native | passed | `scripts/verify_native_only.py` in the run |
| The only file that the patches add | `classes4.dex` | the report of the run |

The checksum of the patched APK is different in every run, because the Morphe
CLI signs every build with a new throwaway key. The checksum of the run that
is named above belongs to that run.

The names of the seams of that build were found by the twin search of
`scripts/find_build_twin.py`, which reproduces the fingerprint sets of the
builds with the version codes 293, 294 and 296 exactly.

---

## 2. What the CI run does

`.github/workflows/verify-pinned-apk-patches.yml`, run `42336409045`, commit `e524487`:

1. resolve the hash-pinned source bundle;
2. download it over HTTPS and compare its SHA-256 with the pinned value;
3. rebuild a content-equivalent APK from the extracted payload with
   `scripts/rebuild-pinned-apk.py`, which re-checks the SHA-256 of every payload file;
4. `zipalign` the rebuild and check the identity with `aapt`;
5. build the Morphe patch bundle with Gradle;
6. run the approval-first native core tests (`scripts/test-agent-core.sh`);
7. apply the three patches with the pinned Morphe Desktop CLI 2.1.1;
8. check the Morphe result JSON;
9. check package, version, signature, alignment, manifest and DEX structure of the patched APK;
10. check the hook call sites in the patched DEX with `scripts/verify_patched_hooks.py`;
11. delete the temporary third-party and patched APKs.

All nineteen steps of that run are **green**.

### 2.0 Artifact and hash evidence of that run

| Item | Value |
| --- | --- |
| patch bundle artifact | `deepseek-morphe-patches`, 709,307 bytes, `sha256:dc16e4bbe3acef0cb77cc189561562323e6dea911421ec2626a97e56dc426785` (run `42336409036`) |
| source bundle SHA-256 | `b0ec418ec9b8a273479acd8fed73acefbdbddd34162684c50e1737abb70d74de` |
| rebuilt target SHA-256 | `b4bd2251637879b3c192ad37ff2905910c7c97da192c4bc56fd782d93b01b930` |
| patched APK SHA-256 | `50aecf0b093e81797a2fe3370cd084afbdccc232ae46438bfd4be4ffab0077a3` |
| patched APK size | 31,175,885 bytes |

The rebuilt target differs from the locally rebuilt file only because CI additionally runs
`zipalign` on it. The patched APK is **not** published anywhere: it is deleted in the last step
of the run.

### 2.1 The apply

```
applied=DeepSeek 2.7.4 compatibility check,
        Native Agent hook points (experimental),
        Super DeepSeek native Agent Console
success=True reason=None
```

`--exclusive` is used, so exactly these three patches run. `--validate` is on, so Morphe
validates the result. The patch result JSON is asserted to name all three patches, to contain
no failed patch and to report `success: true`.

### 2.2 Static checks on the patched APK

| Check | Tool | Result |
| --- | --- | --- |
| package / version / versionCode | `aapt dump badging` | `com.deepseek.chat`, `2.7.4`, `293` |
| native ABI | `aapt dump badging` | `arm64-v8a` |
| signature | `apksigner verify --verbose` | valid (Morphe signs with its own generated key) |
| alignment | `zipalign -c -p 4` | aligned |
| DEX structure | `dexdump -f` on every `classes*.dex` | every file disassembles |
| manifest | `aapt dump xmltree` | `com.themuhammad.deepseekmod.extension.SdsAgentConsoleActivity` declared, `com.deepseek.chat.MainActivity` still declared |
| hook call sites | `scripts/verify_patched_hooks.py` | all checks pass |
| no new undefined types | `scripts/verify_patched_hooks.py` | no new undefined type reference |

### 2.3 Hook call sites — register level evidence

The verifier does not look for "a call exists"; it proves that each hook reads **the register
that the original code just wrote**:

* **Prompt hook** — `Ljy1;->e(Lw94;Ljava/lang/Object;)V` reads the prompt field
  `Lly1;->c:Ljava/lang/String;` into a register and passes exactly that register to
  `SdsAgentHooks.beforePrompt(String)`, and the returned value is moved back into the same
  register, so the guarded prompt is the one that is sent.
  *Verified in the patched DEX: `beforePrompt receives the prompt register v8`.*
* **Streaming SET hook** — `Lv44;->l(Ljava/lang/String;Lc26;Lgv1;)V` passes the register that
  carries the replacement content into `Ldea;->j(Object)` to `SdsAgentHooks.observeResponse(String)`.
  *Verified in the patched DEX: `observeResponse receives the SET content register v3`.*
* **Streaming APPEND hook** — `Lv44;->m(Lc26;Ljava/lang/String;)V` passes the register that
  carries the **complete accumulated** content into `Ldea;->m(Object,Object)` to
  `SdsAgentHooks.observeResponse(String)`, and that register is proven to be the one produced by
  `StringBuilder.toString()`, so the observer sees the whole accumulated answer and not a chunk.
  *Verified in the patched DEX: `observeResponse receives the accumulated content register v4`.*

### 2.4 Why the SDK verifier is not enabled for the apply

The Android SDK verifier that Morphe can run while it writes the DEX files **aborts the
rebuild**, because the official DeepSeek APK references optional third-party SDK types that it
does not bundle:

```
[TYPE_MISSING]      Lcom/mob/commons/MOBLINK;, Lcom/mob/commons/MOBPUSH;, Lcom/mob/commons/SECVERIFY;,
                    Lcom/mob/commons/SMSSDK;, Lcom/mob/commons/SHARESDK;
[INVOKE_MISSING_CLASS] Lcom/tencent/smtt/sdk/WebView;->evaluateJavascript
[TYPE_MISSING]      Lcom/bytedance/retrofit2/mime/TypedString;, Lcom/bytedance/ttnet/utils/RetrofitUtils;
[MISSING_IFACE]     Lcom/tencent/smtt/sdk/ValueCallback;, Lcom/bytedance/retrofit2/mime/TypedOutput;
```

These references exist in the untouched official APK (`cn/fly/commons/w`, `cn/fly/commons/h`,
`Lvxb;`, `Lglc;`, `Ltxb;` reference them, and no class file defines them), so the finding is a
property of the official build and not of the patch. The verifier is therefore **not** enabled
for the patch run, and the same property is instead checked explicitly: the set of undefined
type references of the patched APK is compared with the set of the unpatched target, and the
patch must not add a single one. The patched APK has 130 undefined type references, exactly the
pre-existing set of the target.

---

## 3. Evidence levels used in this repository

| Label | Meaning |
| --- | --- |
| **static** | read from the DEX/manifest/binary, not executed |
| **build-verified** | the code compiles in CI |
| **patch-applied** | the patch ran against the target and the result was inspected |
| **device-runtime-verified** | executed on a real device — **nothing in this report claims this** |
| **planned** | designed, not implemented |

---

## 4. What is verified and what is not

**Verified**

* the pinned target is reconstructed from a hash-pinned source and its payload files are
  re-verified against the pinned hashes (`scripts/rebuild-pinned-apk.py`, 5/5 payload hashes);
* the three Morphe patches apply to that target and Morphe reports success;
* the patched APK keeps the official package name, version, version code and ABI;
* the patched APK is signed and aligned, and every DEX file disassembles;
* the original launcher activity is still declared, and the new Agent Console activity is added, declared as not exported and without a launcher filter;
* the three hooks read the intended registers, proven against the patched DEX;
* the patch adds no undefined type reference;
* the native core test suite (`scripts/test-agent-core.sh`) passes in the same run.

**Not verified**

* installation and runtime on a device: no device was available;
* login, session and the official authentication flow: untouched by the patches, but also not
  exercised;
* the SSE observer at runtime: callback frequency, duplicated events, thread and lifecycle
  behaviour and UI stalls are still **unmeasured**;
* the rebuilt APK is content-equivalent to the official APK but is **not byte identical** and is
  **not signed with DeepSeek's certificate**; the signature is a fresh Morphe key.

---

## 5. Reproducing the run

```bash
gh run view 42358670810 --repo themuhammad-personal/DeepSeek-Mod-APK
```

The newest full verification run against the pinned target is `42358670810`. It
proves the four patches that exist today:

```
applied=DeepSeek 2.7.4 compatibility check, Native Agent hook points (experimental),
Super DeepSeek native Agent Console, Super DeepSeek native Agent chat entry
success=True reason=None
```

with the hook call sites

```
OK      beforePrompt receives the prompt register v8
OK      observeResponse receives the SET content register v3
OK      observeResponse receives the accumulated content register v4
OK      observeResponseAppend reads the content register v4
OK      the handback is written back into v4 before the update
OK      the patch introduced no new undefined type reference
```

and the artefacts of that run:

| Item | Value |
|---|---|
| rebuilt target SHA-256 | `b4bd2251637879b3c192ad37ff2905910c7c97da192c4bc56fd782d93b01b930` |
| patched APK SHA-256 | `c9fdf1ad7f1d43ea1164e99fa7cc90c2915494687a3645bf0f1fb7f2ac1dfce8` |
| patched APK size | 31,278,285 bytes |
| source bundle SHA-256 | `b0ec418ec9b8a273479acd8fed73acefbdbddd34162684c50e1737abb70d74de` |

The earlier P0 run `42336409045` proved the first three patches before the
chat entry and the handback existed.

The run `42358670810` repeated the whole chain with the **current** patch set
(the console that is not exported, the runtime that starts with the chat
screen, the chat entry, the handback, the fingerprint set of the build and the
stricter verification). Every step of the workflow succeeded: the pinned source
was fetched and checked, the target was rebuilt, the four patches were applied,
the patched APK passed the package, version, signature, alignment and manifest
checks and the hook call sites were proved in the patched DEX.

### The second inspected build

The build with the version code 294 has its own fingerprint set, because it is
obfuscated on its own. The workflow `verify-294-build-patches.yml` took the
base APK out of the copy of the owner and repeated the same chain against it:

```bash
gh run view 42358670995 --repo themuhammad-personal/DeepSeek-Mod-APK
```

| Item | Value |
|---|---|
| source APKM SHA-256 | `8c3f9665e81c4c7bb76547f70c4d5e52ccc31d981e66c8121ad9f218b646f1ae` |
| base APK SHA-256 | `2e898602374ed75f53d8e4c162fa625f5f4e1793b16fbd567f6dd02f6277a709` |
| patched APK SHA-256 | `496c20b8c5cad4e2280eef41ab2b1e74f51753bc13cb6c43a6d1009034e935be` |
| patched APK size | 23,191,872 bytes |

Every step of that run succeeded as well, the four patches were applied and the
hook call sites were proved in the patched DEX with the names of that build.

### The third inspected build

DeepSeek 2.7.5, the version code 296, is the newest release that was
inspected. It is a split package, it needs Android 12L and it is obfuscated on
its own, so it has its own fingerprint set. The same chain was repeated
against it:

```bash
gh run view 42360116129 --repo themuhammad-personal/DeepSeek-Mod-APK
```

| Item | Value |
|---|---|
| source package SHA-256 | `2362b0d6d98fd7288cc25fb889f4f3cacae7d2c42c5f5799c7aa50026598ae60` |
| base APK SHA-256 | `6befd98c06155b2b4583f57cd76d7b721e342fed77025a94b653e03e18b5c8a8` |
| patched APK SHA-256 | `6214d7bd3d292f41675f044b1862b386f9a5bad3c40cd91aefb9fbfe9cb33f13` |
| patched APK size | 23,167,296 bytes |

Every step of that run succeeded too. The hooks land on the same registers of
that build: the prompt in `v8`, the replaced content in `v3` and the appended
content in `v4`.

The runs need the hash-pinned source location, which is read from
`.github/pinned-source.env` and from `.github/pinned-294-source.env`.
That file points at the owner's own Drive copy and is **not part of the project**: it is removed
after the verification, and the Drive link share is revoked. Without it the workflow resolves
`has_source=false`, skips every verification step and says so in the run summary — it never
reports a pass it did not perform.

---

## 6. Known limits and the next blocker

1. **No device runtime.** The next blocker is a physical Android device (arm64, API 24+). The
   checks that need it: install the patched APK, log in with the official flow, send a chat
   message, and record the observer events (frequency, full accumulated content, duplicates,
   thread, lifecycle, UI stalls) without logging anything sensitive.
2. **The builds 294 and 296 are not device proven.** Both have their own fingerprint set and
   the patches were applied to their base APK in the runs `42358670995` and `42360116129`, so
   they are *patch-applied*. They were not started on a device, so their targets stay
   experimental. The fingerprint sets do not mix: the set of one build does not resolve in
   another build, and the harness proves that for every pair.
3. **The console entry point** is now also a plain floating view inside the official chat
   screen, inserted by the patch `Super DeepSeek native Agent chat entry`. The call site is
   proved in the patched DEX, but the placement and the touch behaviour on a device are not.
4. **The handback of a tool result** is proved statically in the patched DEX and it is switched
   off by default. The device run has to show that the chat renders the rewritten content and
   that the result is never duplicated.
5. **The PRoot runtime of the Linux Studio** is not bundled, so the studio refuses to run. The
   console can now download and install the pinned root filesystem behind an explicit approval
   and with a checksum against the official list, but the binaries of PRoot still have to be
   added by the owner.
6. **The console is not exported.** The Agent Console Activity is declared with
   `android:exported="false"` and without a launcher filter, so only the app itself can open it.
   The verification fails when that changes.
