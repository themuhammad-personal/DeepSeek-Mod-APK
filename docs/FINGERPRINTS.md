# Fingerprints — one set per inspected build

## Why every build needs its own fingerprints

The official app is obfuscated again for every build. Two builds of the same
app version therefore share no class name, and a fingerprint of one build does
not resolve in another build at all. The fingerprints of this project are
therefore never reused: every inspected build has its own set, and the set is
selected by the **version code of the APK that is being patched**.

| Build | Version code | Fingerprint set | Profile |
|---|---|---|---|
| Official download of DeepSeek 2.7.4 | 293 | `patches/.../DeepSeekFingerprints293.kt` | `deepSeekTargetProfile293` |
| Mirrored build of DeepSeek 2.7.4 | 294 | `patches/.../DeepSeekFingerprints294.kt` | `deepSeekTargetProfile294` |

The patch reads the version code from the APK itself
(`app.morphe.patcher.patch.packageMetadata.versionCode`) and looks the profile
up in `deepSeekTargetProfiles`. A build that was not inspected has no profile,
so the compatibility gate stops with a readable message and nothing is patched.

## The seams of both builds

The three seams have the same shape in both builds, only the names differ.
The 294 names were read out of the dex files of that build and were checked
with `scripts/verify_fingerprints.py`.

| Seam | Build 293 | Build 294 |
|---|---|---|
| Request prompt serializer | `Ljy1;->e(Lw94;Ljava/lang/Object;)V` | `La02;->b(Lwb4;Ljava/lang/Object;)V` |
| Request model that holds the prompt | `Lly1;` | `Lc02;` |
| Prompt field | `Lly1;->c : Ljava/lang/String;` | `Lc02;->c : Ljava/lang/String;` |
| Writer of the string fields | `Ly53;->x(Lsr9; I Ljava/lang/String;)V` | `Lv73;->x(Lqv9; I Ljava/lang/String;)V` |
| Serializer descriptor | `Lsr9;` | `Lqv9;` |
| Streaming SET of the answer content | `Lv44;->l(Ljava/lang/String; Lc26; Lgv1;)V` | `Lv64;->l(Ljava/lang/String; Ls46; Lxw1;)V` |
| Streaming APPEND of the answer content | `Lv44;->m(Lc26; Ljava/lang/String;)V` | `Lv64;->m(Ls46; Ljava/lang/String;)V` |
| Content holder of the answer | `Ldea;` | `Leia;` |
| Content update (SET) | `Ldea;->j(Ljava/lang/Object;)V` | `Leia;->j(Ljava/lang/Object;)V` |
| Content update (APPEND) | `Ldea;->m(Ljava/lang/Object; Ljava/lang/Object;)Z` | `Leia;->m(Ljava/lang/Object; Ljava/lang/Object;)Z` |
| Launcher Activity | `Lcom/deepseek/chat/MainActivity;->onCreate(Landroid/os/Bundle;)V` | the same, the name is not obfuscated |

The APPEND path of both builds builds the complete answer with a
`StringBuilder`, moves the result into a register and passes that register as
the second argument of the content update. The instruction level checks of
`DeepSeekInstructionChecks.kt` read that register out of the bytecode instead
of assuming a position, so the observer always sees the value that the app
wrote.

## The static proof

`scripts/verify_fingerprints.py` reads the fingerprint definitions out of the
Kotlin sources, so the script can never drift away from the patch bundle, and
checks them against the dex files of a build:

```
python3 scripts/verify_fingerprints.py <apk|dex-dir> [--version-code 293|294]
```

For every fingerprint the script proves that the class exists, that the method
exists with the exact descriptor and that every filter of the fingerprint
matches. It also proves the instruction shape that the patch relies on: the
prompt field read is followed by the serializer call and both use the same
register, the SET update passes exactly one argument, and the APPEND update
passes two arguments of which the second one is the register that the
`StringBuilder` produced.

The script also proves the other direction: the fingerprint set of a build must
**not** resolve in another build. A run against the build with the version code
294 reports the fingerprints of the build with the version code 293 as absent,
and the other way round. That is the mechanical proof that a fingerprint of one
build can never be used for another build.

The verification workflow runs the same script against the rebuilt pinned
target, so the set of the build with the version code 293 is checked on every
run that has a pinned source.

`scripts/verify_patched_hooks.py` reads the version code of the patched APK and
uses the names of that build, so the hook verification of a patched APK is
correct for the build that was patched.

## The apply proof of the build with the version code 294

The workflow `verify-294-build-patches.yml` takes the base APK out of the
owner's own copy of the APKM, checks the fingerprint set of that build, applies
the four patches and verifies the hook call sites in the patched DEX.

| Item | Value |
|---|---|
| run | `42358670995` |
| source APKM SHA-256 | `8c3f9665e81c4c7bb76547f70c4d5e52ccc31d981e66c8121ad9f218b646f1ae` |
| base APK SHA-256 | `2e898602374ed75f53d8e4c162fa625f5f4e1793b16fbd567f6dd02f6277a709` |
| patched APK SHA-256 | `496c20b8c5cad4e2280eef41ab2b1e74f51753bc13cb6c43a6d1009034e935be` |
| patched APK size | 23,191,872 bytes |
| hooks | `beforePrompt` reads `v8`, the SET observer reads `v3`, the APPEND observer reads `v4` |

Every step of that run succeeded, Morphe reported `success=True` and the
result JSON lists the four applied patches.

## What this does not prove

- The build with the version code 294 is **patch-applied**, but it was **not
  run on a device**. Nothing of this proves that the app starts, that the login
  works or that the observer sees the stream on that build.
- The target of the version code 294 therefore stays **experimental** in
  `DeepSeekCompatibility.kt`, while the target of the version code 293 stays
  the stable one.
- Nothing of this changes the official login, the session, the anti-fraud
  checks or the signature of the app.
