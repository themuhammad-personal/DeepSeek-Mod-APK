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
| Official download of DeepSeek 2.7.5 | 295 | `patches/.../DeepSeekFingerprints295.kt` | `deepSeekTargetProfile295` |
| Mirrored build of DeepSeek 2.7.5 | 296 | `patches/.../DeepSeekFingerprints296.kt` | `deepSeekTargetProfile296` |

The patch reads the version code from the APK itself
(`app.morphe.patcher.patch.packageMetadata.versionCode`) and looks the profile
up in `deepSeekTargetProfiles`. A build that was not inspected has no profile,
so the compatibility gate stops with a readable message and nothing is patched.

## The seams of the inspected builds

The three seams have the same shape in every build, only the names differ.
The names of the build with the version code 293 and of the build with the
version code 294 were read out of the dex files of that build and were checked
with `scripts/verify_fingerprints.py`. The names of the builds with the
version codes 295 and 296 were found by the twin search that is described in
the next section.

| Seam | Build 293 | Build 294 | Build 295 | Build 296 |
|---|---|---|---|---|
| Request prompt serializer | `Ljy1;->e(Lw94;Ljava/lang/Object;)V` | `La02;->b(Lwb4;Ljava/lang/Object;)V` | `Loy1;->e(Laa4;Ljava/lang/Object;)V` | `Lf02;->b(Lac4;Ljava/lang/Object;)V` |
| Request model that holds the prompt | `Lly1;` | `Lc02;` | `Lqy1;` | `Lh02;` |
| Prompt field | `Lly1;->c : Ljava/lang/String;` | `Lc02;->c : Ljava/lang/String;` | `Lqy1;->c : Ljava/lang/String;` | `Lh02;->c : Ljava/lang/String;` |
| Writer of the string fields | `Ly53;->x(Lsr9; I Ljava/lang/String;)V` | `Lv73;->x(Lqv9; I Ljava/lang/String;)V` | `Ld63;->x(Lyr9; I Ljava/lang/String;)V` | `La83;->x(Lvv9; I Ljava/lang/String;)V` |
| Serializer descriptor | `Lsr9;` | `Lqv9;` | `Lyr9;` | `Lvv9;` |
| Streaming SET of the answer content | `Lv44;->l(Ljava/lang/String; Lc26; Lgv1;)V` | `Lv64;->l(Ljava/lang/String; Ls46; Lxw1;)V` | `Ly44;->l(Ljava/lang/String; Lh26; Liv1;)V` | `Ly64;->l(Ljava/lang/String; Lx46; Lzw1;)V` |
| Streaming APPEND of the answer content | `Lv44;->m(Lc26; Ljava/lang/String;)V` | `Lv64;->m(Ls46; Ljava/lang/String;)V` | `Ly44;->m(Lh26; Ljava/lang/String;)V` | `Ly64;->m(Lx46; Ljava/lang/String;)V` |
| Content holder of the answer | `Ldea;` | `Lea;` | `Llea;` | `Llia;` |
| Content update (SET) | `Ldea;->j(Ljava/lang/Object;)V` | `Lea;->j(Ljava/lang/Object;)V` | `Llea;->j(Ljava/lang/Object;)V` | `Llia;->j(Ljava/lang/Object;)V` |
| Content update (APPEND) | `Ldea;->m(Ljava/lang/Object; Ljava/lang/Object;)Z` | `Lea;->m(Ljava/lang/Object; Ljava/lang/Object;)Z` | `Llea;->m(Ljava/lang/Object; Ljava/lang/Object;)Z` | `Llia;->m(Ljava/lang/Object; Ljava/lang/Object;)Z` |
| Launcher Activity | `Lcom/deepseek/chat/MainActivity;->onCreate(Landroid/os/Bundle;)V` | the same | the same | the same |

The APPEND path of the builds builds the complete answer with a
`StringBuilder`, moves the result into a register and passes that register as
the second argument of the content update. The instruction level checks of
`DeepSeekInstructionChecks.kt` read that register out of the bytecode instead
of assuming a position, so the observer always sees the value that the app
wrote.

## How the names of a new build are found

The names of a new build are not guessed. `scripts/find_build_twin.py` finds
them by the **shape of the instructions** of the seams, so the result does not
depend on any name of the build that is searched.

The script takes the dex files of the new build and the shape of the seams of
a known build (`scripts/reference_seams.json`), and it searches for the
methods that have the same shape:

- the prompt serializer is the method that reads a string field and writes it
  with a writer call whose key constant is 2, which is the prompt key of the
  request,
- the streaming SET is the method that takes a string, a fragment and a
  descriptor and calls the one argument setter of the content holder,
- the streaming APPEND is the method of the class of the SET that calls the
  two argument update of the same holder.

The tokens of the search are free of names: a call is a name and a number of
arguments, a field is a kind of a type. The script therefore finds the twin
of every seam, and it reports the class, the name, the descriptor, the prompt
field, the writer and the content holder of that twin.

The script was checked against the builds that were inspected by hand: it
reproduces the fingerprint sets of the builds with the version codes 293, 294
and 296 **exactly**, name for name and descriptor for descriptor. The set of
the build with the version code 295 was found with it and was applied to that
build in the workflow `Build the patched DeepSeek APK`.

The twin search runs in the workflow `Check the newest official DeepSeek
release`, which downloads the file that the official download address serves
and reports the seams of that build, so a new build can be supported without
guessing.

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

## The third inspected build: DeepSeek 2.7.5, version code 296

The newest release that was inspected. It is distributed as a split package,
it needs Android 12L or newer and it is obfuscated on its own, so every class
name of the builds with the version codes 293 and 294 changed.

| Role | Build 293 | Build 294 | Build 296 |
|---|---|---|---|
| prompt serializer | `Ljy1;->e` | `La02;->b` | `Lf02;->b` |
| prompt field | `Lly1;->c` | `Lc02;->c` | `Lh02;->c` |
| string writer | `Ly53;->x` | `Lv73;->x` | `La83;->x` |
| streaming SET | `Lv44;->l` | `Lv64;->l` | `Ly64;->l` |
| streaming APPEND | `Lv44;->m` | `Lv64;->m` | `Ly64;->m` |
| content holder | `Ldea;` | `Leia;` | `Llia;` |

The three seams of the build with the version code 296 were compared
instruction by instruction with the seams of the build with the version code
294: the prompt serializer matches for its first 81 instructions, the
streaming SET path and the streaming APPEND path match completely, only the
names changed. The launcher Activity keeps its name in every build, so the
fingerprint of the Activity is shared.

### The apply proof of the build with the version code 296

| Item | Value |
|---|---|
| run | `42360116129` |
| source package SHA-256 | `2362b0d6d98fd7288cc25fb889f4f3cacae7d2c42c5f5799c7aa50026598ae60` |
| base APK SHA-256 | `6befd98c06155b2b4583f57cd76d7b721e342fed77025a94b653e03e18b5c8a8` |
| patched APK SHA-256 | `6214d7bd3d292f41675f044b1862b386f9a5bad3c40cd91aefb9fbfe9cb33f13` |
| patched APK size | 23,167,296 bytes |
| hooks | `beforePrompt` reads `v8`, the SET observer reads `v3`, the APPEND observer reads `v4` |

Every step of that run succeeded, Morphe reported `success=True` and the
result JSON lists the four applied patches.

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
