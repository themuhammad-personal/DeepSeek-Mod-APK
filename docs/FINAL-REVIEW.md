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
| The patches apply to the inspected target and Morphe reports success | **patch-applied** | CI run `42349185806`, four patches applied, `success=True` |
| The hook call sites read the register that the original code just wrote | **patch-applied** | the DEX comparison of the verifier, `OK beforePrompt receives the prompt register v8`, `OK observeResponse receives the accumulated content register v4` |
| The guarded prompt is written back into the request | **patch-applied** | the verifier fails when it is not |
| The observer runs after the update of the content | **patch-applied** | the verifier fails when it does not |
| The extension is really in the bundle of the patched APK | **patch-applied** | 38 classes are looked up in the patched DEX |
| The console is not exported and has no launcher entry | **patch-applied** | the manifest check of the verification workflow |
| The runtime has no defect in the reviewed paths | **build-verified**, **regression-tested** | CI runs `42353976805` (bundle) and `42353976704` (verification), the regression test is green |
| The MCP client speaks the protocol | **static-verified** against the specification of the protocol, **regression-tested** for the handshake | the request shape and the negotiation are covered by the test |
| The address and the checksum of the root filesystem are the official ones | **verified online** | `https://cdimage.ubuntu.com/ubuntu-base/releases/noble/release/SHA256SUMS` |
| The runtime works on a real phone | **NOT VERIFIED** | no device was available |
| The PRoot runtime runs | **NOT VERIFIED** | the binaries are not bundled, so the studio refuses to run |
| The tool result reaches the chat | **NOT VERIFIED** | the seam is proved in the DEX, the rendering is not |

### The artefacts of the last full apply verification (run `42349185806`)

| Item | Value |
|---|---|
| rebuilt target SHA-256 | `b4bd2251637879b3c192ad37ff2905910c7c97da192c4bc56fd782d93b01b930` |
| patched APK SHA-256 | `69b01e2fb2d16184584218c924823de993a415852bb6ece2fc6a8c3098ca8278` |
| patched APK size | 31,253,709 bytes |
| source bundle SHA-256 | `b0ec418ec9b8a273479acd8fed73acefbdbddd34162684c50e1737abb70d74de` |
| Morphe patch bundle artefact | `sha256:ceac6957e34065d5c8de4bdeea507d07c87bef69b9542bcb8cc73f810aab43dd` |

The runs after that one report `NOT VERIFIED`, because `.github/pinned-source.env`
was deleted after the verification. The workflow skips every step and says so;
it never reports a pass it did not perform.

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
   build with a different version code. It needs its own fingerprint set; the
   fingerprints of 2.7.4 must never be applied to it.
