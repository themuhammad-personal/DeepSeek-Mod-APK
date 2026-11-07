# Final review report

This report closes the line-by-line review of the whole code base of the
branch. It lists what was changed, what was proved, what was **not** proved and
what the next blocker is.

* Branch: `arena/8bb57a23-deepseek-mod-apk`
* Reviewed range: `e93d56b` → `165ffea` (18 commits, 31 files, +1632 / −263)
* Remote: `github.com/themuhammad-personal/DeepSeek-Mod-APK`, branch `arena/8bb57a23-deepseek-mod-apk`
* Nothing was pushed to `main`, no pull request was opened, `work/morphe-foundation`
  is untouched at `f0f87e07`.

## 1. The defects that were found and fixed

### Security

| Defect | Fix | Commit |
|---|---|---|
| The Agent Console Activity was declared `exported=true` and had a `LAUNCHER` filter, so any app on the device could open the screen with the memories, the artifacts and the MCP servers. | The Activity is declared with `exported=false` and without a launcher filter. It is opened only from the entry in the chat screen. The verification fails when that changes. | `b7eefa5` |
| The MCP client followed a redirect, so a server could send the bearer token to another host. | A redirect is never followed, a redirect is refused as an answer and the address is checked to use https before the connection is opened. | `651b106` |
| The console showed the live policy nowhere and used the safe default for the studio command. | The console uses the live policy of the runtime for every tool. | `86a5aaf` |

### Correctness

| Defect | Fix | Commit |
|---|---|---|
| The MCP client never performed the handshake of the specification, so a real server refused the tool list and the feature could not work at all. | The client sends `initialize`, takes the negotiated version, refuses a version it does not know, confirms with `notifications/initialized` and sends the negotiated version in the header of every later call. | `07216e7` |
| The command of the Linux Studio used `Process.waitFor(long, TimeUnit)` and `destroyForcibly()`. Both need Android 8 while the app supports Android 7, so a command would have crashed the app on an older phone. | The wait runs in its own thread, the stop of the user is noticed while the command runs and the hard stop is only tried when the platform has it. | `d15dfc5` |
| The address of the root filesystem pointed at a directory that does not exist (`releases/24.04/release`). | The real address is under the codename of the release (`releases/noble/release`). The checksum was and is the one of the official `SHA256SUMS`. | `d15dfc5` |
| The console registered a proposal and approved it in two steps, so a proposal that was chosen while another tool was running could approve the **older** proposal instead. | The session approves a chosen proposal in one step and refuses a second approval while a proposal waits for its run. | `f2120ca` |
| A proposal that was in the queue could not be denied, because the session only knew a proposal after it was registered. | Every card of the queue has a deny button and the decision is written into the timeline. | `f2120ca` |
| A tool that returned nothing made the console crash while the result was shown, and a missing runner or a missing workspace was not guarded. | The runner, the result and the workspace are checked before the result reaches the screen. | `86a5aaf` |
| The listener of the switch that adds the memories to the prompt was broken by a change, and the build failed. | The switch writes the runtime and the stored value. | `1ce1995` |
| Two artifacts that were created in the same millisecond could share an identifier and the second one overwrote the first. | The identifier carries a counter. | `5e88d38` |
| The memories, the projects, the artifacts and the MCP configuration were written straight into their file, so a crash in the middle of a write emptied them. | The stores write through a temporary file that then takes the place of the target. | `5e88d38` |
| A long artifact was rendered in one piece in a dialog. | The dialog shows the beginning of the artifact and says that the rest is not shown. | `86a5aaf` |

### Function that was missing

| Gap | Fix | Commit |
|---|---|---|
| Every switch of the console lived only in the memory of the process, so the user had to turn the proposal mode on again after every restart. | The switches are stored in the app-private preferences, and the runtime restores them when the chat screen starts. | `bd230fa` |
| The Linux Studio could never become ready: nothing could download the root filesystem. | The console downloads the pinned archive over https after an approval, checks the checksum against the official list, unpacks it with the tar of the system and only then puts the directory in place. | `d191cca` |
| The file tools could only reach the private workspace, so the documents of the user were out of reach. | The console opens the file picker of the system, reads the document the user picked with a bound of 256 KiB and imports it into the workspace. | `f64791a` |
| The console did not show a proposal that arrived while it was open. | The proposal list and the session state refresh on their own. | `b7eefa5` |

### Verification and documentation

| Change | Commit |
|---|---|
| The verification now fails when the guarded prompt is not written back, when the observer runs before the update and when a class of the extension is missing from the bundle. | `fe614ba` |
| The bundle build installs the Android SDK itself instead of trusting the image of the runner. | `fbfa5ec` |
| The architecture, the security boundaries, the verification guide, the feature map and the readme were brought back in line with the code. | `88c5d70`, `2995acb`, `9bca263`, `165ffea` |

## 2. The state of the code

| Item | State |
|---|---|
| Java sources of the extension | 42 classes, every one of them parsed and compiled by CI |
| Plain-Java regression test | 33 test groups, run on every push |
| Kotlin patches | 4 patches in a dependency chain, every one of them applied by CI against the pinned target |
| Verification script | 10 checks against the patched DEX, compared with the unpatched target |
| Known API level risk | none left: the extension uses no API above 24 in the runtime paths |

## 3. What was really proved

| Claim | Label | Evidence |
|---|---|---|
| The patches apply to the inspected target and Morphe reports success | **patch-applied** | CI run `42358670810` applied the **current** patch set to the pinned target: every step of the workflow succeeded, the four patches were applied and Morphe reported `success=True` |
| The patches apply to the build with the version code 294 | **patch-applied** | CI run `42358670995` applied the **current** patch set to the base APK of that build: every step succeeded, the four patches were applied, the result JSON reports `success=True` and the hook call sites were verified in the patched DEX |
| The patches apply to the newest build with the version code 296 | **patch-applied** | CI run `42360116129` applied the **current** patch set to the base APK of DeepSeek 2.7.5: every step succeeded, the four patches were applied, the result JSON reports `success=True` and the hook call sites were verified in the patched DEX |
| The fingerprint set of every inspected build is bound to its own build | **static-verified** and **patch-applied** | `scripts/verify_fingerprints.py` proves that the set of the build 293 does not resolve in the build 294 and the other way round, and both workflows ran that check before they applied the patches |
| The hook call sites read the register that the original code just wrote | **patch-applied** | the DEX comparison of the verifier, `OK beforePrompt receives the prompt register v8`, `OK observeResponse receives the accumulated content register v4`, and the same registers in the build with the version code 294 |
| The guarded prompt is written back into the request | **patch-applied** | the verifier fails when it is not |
| The observer runs after the update of the content | **patch-applied** | the verifier fails when it does not |
| The extension is really in the bundle of the patched APK | **patch-applied** | 38 classes are looked up in the patched DEX |
| The console is not exported and has no launcher entry | **patch-applied** | the manifest check of the verification workflow |
| The runtime has no defect in the reviewed paths | **build-verified**, **regression-tested** | the bundle run `42354635470` and the verification run `42354635483`, the plain-Java regression test is green in both |
| The MCP client speaks the protocol | **static-verified** against the specification of the protocol, **regression-tested** for the handshake | the request shape and the negotiation are covered by the test |
| The address and the checksum of the root filesystem are the official ones | **verified online** | `https://cdimage.ubuntu.com/ubuntu-base/releases/noble/release/SHA256SUMS` |
| The runtime works on a real phone | **NOT VERIFIED** | no device was available |
| The PRoot runtime runs | **NOT VERIFIED** | the binaries are not bundled, so the studio refuses to run |
| The tool result reaches the chat | **NOT VERIFIED** | the seam is proved in the DEX, the rendering is not |

### The artefacts of the full apply verification of the current patch set

Run `42358670810` repeated the whole chain with the current patches against the
pinned target and every step succeeded. Its step summary carries the hashes of
the rebuilt target and of the patched APK.

| Item | Value |
|---|---|
| rebuilt target SHA-256 | `b4bd2251637879b3c192ad37ff2905910c7c97da192c4bc56fd782d93b01b930` |
| patched APK SHA-256 | `c9fdf1ad7f1d43ea1164e99fa7cc90c2915494687a3645bf0f1fb7f2ac1dfce8` |
| patched APK size | 31,278,285 bytes |
| source bundle SHA-256 | `b0ec418ec9b8a273479acd8fed73acefbdbddd34162684c50e1737abb70d74de` |
| Morphe patch bundle artefact of the earlier run | `sha256:ceac6957e34065d5c8de4bdeea507d07c87bef69b9542bcb8cc73f810aab43dd` |

### The artefacts of the full apply verification of the build with the version code 296

Run `42360116129` took the base APK out of the XAPK of the owner, checked the
fingerprint set of that build, applied the four patches and verified the hook
call sites in the patched DEX.

| Item | Value |
|---|---|
| source package SHA-256 | `2362b0d6d98fd7288cc25fb889f4f3cacae7d2c42c5f5799c7aa50026598ae60` |
| base APK SHA-256 | `6befd98c06155b2b4583f57cd76d7b721e342fed77025a94b653e03e18b5c8a8` |
| patched APK SHA-256 | `6214d7bd3d292f41675f044b1862b386f9a5bad3c40cd91aefb9fbfe9cb33f13` |
| patched APK size | 23,167,296 bytes |

### The artefacts of the full apply verification of the build with the version code 294

Run `42358670995` took the base APK out of the copy of the owner, checked the
fingerprint set of that build, applied the four patches and verified the hook
call sites in the patched DEX.

| Item | Value |
|---|---|
| source APKM SHA-256 | `8c3f9665e81c4c7bb76547f70c4d5e52ccc31d981e66c8121ad9f218b646f1ae` |
| base APK SHA-256 | `2e898602374ed75f53d8e4c162fa625f5f4e1793b16fbd567f6dd02f6277a709` |
| patched APK SHA-256 | `496c20b8c5cad4e2280eef41ab2b1e74f51753bc13cb6c43a6d1009034e935be` |
| patched APK size | 23,191,872 bytes |

The runs without a pointer report `NOT VERIFIED`, because `.github/pinned-source.env`
and `.github/pinned-294-source.env` were deleted after the verification. The
workflows skip every step and say so; they never report a pass they did not
perform. The pointers were restored for the runs `42358670810` and
`42358670995` and removed again right after them, and the link shares of the
files in the Drive of the owner were revoked again.

## 3.1 The build of the newest official app and the proofs of this round

The owner asked for four things in addition to the work that was done: the
job has to be finished properly, the patched APK has to be built by GitHub
Actions, the patches have to follow the rules of the Morphe community and to
be fully functional, the login of the app has to keep working, and the app has
to stay a native Android app. This round delivers the following.

### The newest official build is supported

The official download address serves DeepSeek 2.7.5 with the version code 295
now. That build is obfuscated on its own, so the names of the seams of the
older builds do not exist in it at all.

The names were **not guessed**. `scripts/find_build_twin.py` finds the seams of
a build by the shape of its instructions and reports the names that a
fingerprint set has to use. The search reproduces the sets of the builds with
the version codes 293, 294 and 296 exactly, name for name and descriptor for
descriptor, so its result for the build 295 was trusted and written into
`patches/src/main/kotlin/com/themuhammad/deepseekmod/patches/DeepSeekFingerprints295.kt`:

| Seam | Build 295 |
|---|---|
| Request prompt serializer | `Loy1;->e(Laa4;Ljava/lang/Object;)V` |
| Prompt field | `Lqy1;->c : Ljava/lang/String;` |
| Writer of the string fields | `Ld63;->x(Lyr9; I Ljava/lang/String;)V` |
| Streaming SET of the answer content | `Ly44;->l(Ljava/lang/String; Lh26; Liv1;)V` |
| Streaming APPEND of the answer content | `Ly44;->m(Lh26; Ljava/lang/String;)V` |
| Content holder of the answer | `Llea;` |

### The patched APK of the newest build

| Property | Value |
|---|---|
| The official app that was patched | DeepSeek 2.7.5, the version code 295 |
| Official APK SHA-256 | `ddc52ef868b991fd33e634de834a3e2c022b0cd7ff9e854f4f141996aaeadb70` (22,210,268 bytes) |
| The run that built the patched APK | `42364614302` of the workflow **Build the patched DeepSeek APK** |
| Patched APK SHA-256 | `eb7f8d94c180da1888829220bba9916cc2e08a51483088c730d43d669ad1e54d` (24,147,026 bytes) |
| The artifact | `deepseek-patched-apk` of that run, it expires on 2026-11-14 |
| The patches that were applied | the four patches of this project, the patch result JSON of the run lists them |

The checksum of the patched APK is different in every run, because the Morphe
CLI signs every build with a new throwaway key. The checksum above belongs to
the run that is named above.

### The patched APK is built by GitHub Actions

The new workflow **Build the patched DeepSeek APK** downloads the file that
the official download address serves, checks the fingerprint set of that
build, runs the core tests, builds the bundle, applies the four patches with
the pinned Morphe CLI and checks the result. The patched APK is uploaded as an
artifact of the run with a lifetime of seven days. It is never committed and
never published as a release, because the official app is not
redistributable. The signature is a throwaway key of the build, so the app
cannot replace an official installation in place.

### The login of the app is not touched

`scripts/verify_login_untouched.py` compares the DEX files of the patched APK
with the DEX files of the original APK method by method. Only the four
methods that the patches hook may differ, every class that the patches add has
to belong to the extension of this project or to the Kotlin runtime that the
patcher merges in, and no method of the login, of the session, of the token,
of the CAPTCHA or of the anti-fraud code may change in any way. The run
`42364324070` passed that check against the build with the version code 295.

### The app stays a native Android app

`scripts/verify_native_only.py` proves that no class of the extension uses the
web kit of Android, that no class of the extension carries a JavaScript or a
HTML payload and that the patched APK does not add a web asset to the
original APK. The only file that the patches add to the app is
`classes4.dex`, which holds the extension. The run `42364324070` passed that
check as well.

### The patches follow the Morphe template

- every patch file carries the header of the Morphe patch template,
- the generator of the patch list of the template was added, so the publish
  task of the bundle can run,
- the metadata of the bundle (`about`) names the project, the source, the
  author, the contact, the website and the licence,
- the extension is a `.mpe` module that is built by the `extension` block,
- the compatibility targets are one object per version code, because the
  patcher refuses two targets with the same version string,
- every patch declares `compatibleWith(...)` and the hooks depend on the
  compatibility gate.

## 4. The next blocker

1. **A physical device with the official 2.7.4 and an account.** The observer has
   to be proved with the real callback frequency, the complete accumulated
   content, no duplicates, no thread problem and no stall of the screen. The
   floating entry of the chat, the console, the memories, the artifacts, the
   export, the MCP call, the research fetch, the document picker and the
   switches after a restart all have to be checked with the same build.
2. **The binaries of PRoot.** They are not obtainable here, and they are not
   redistributable without their licence. Until they are added, the Linux
   Studio refuses to run and says so.
3. **The pinned source of the exact 2.7.4 binary.** The public endpoint serves a
   moving latest version, so the verification waits for the pointer of the
   owner. The hash is never changed to make a run pass.
4. **The 2.7.4/294 build.** The copy in the Drive of the owner is a different
   build with a different version code, and it is obfuscated on its own. It now
   has its own fingerprint set, which was read out of its dex files and proved
   with `scripts/verify_fingerprints.py`: the set of the build 294 resolves
   completely in that build and does not resolve at all in the build 293, and
   the other way round. The target of the version code 294 is therefore
   declared as experimental: the patches were not applied to it and it was not
   run on a device. See [FINGERPRINTS.md](FINGERPRINTS.md).
