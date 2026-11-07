# Build and patch verification

Two independent CI workflows are used.

1. **Build Morphe patch bundle** runs on every push to the working branch. It runs the Java
   agent-core regression tests and builds the MPP/MPE bundle.
2. **Verify Morphe patches against the pinned DeepSeek target** runs on every push that changes
   the patches, the extension, the verification scripts or the workflow. It resolves a
   hash-pinned source, rebuilds a content-equivalent target APK, applies the patches with the
   pinned Morphe Desktop CLI, and then verifies the resulting APK.

The detailed result of the apply verification is in **[P0-VERIFICATION.md](P0-VERIFICATION.md)**.

## Source of the target

The live DeepSeek download URL is a moving latest-version endpoint. When it stops serving the
inspected 2.7.4 sample, the SHA check intentionally fails before Morphe applies any patch.
Supply a stable HTTPS source for the exact sample and its known SHA-256; **do not change the
pinned hash to match an uninspected version**. The official 2.7.4 APK is not redistributable
and is never committed to this repository.

The source location is read from `.github/pinned-source.env`. That file points at the owner's own
copy, is not part of the project, and is removed once the verification has run. Without it the
workflow resolves `has_source=false`, skips every verification step and states in the run
summary that nothing was verified.

The original and patched APKs are temporary CI files and are deleted at job completion. They are
not uploaded as release assets and not committed. This is static/build-time integration
evidence only. It does not prove app installation, account login, CAPTCHA/risk behaviour,
correct callback frequency, threading, Compose rendering or real-device execution.

## What the verification proves

- the target is rebuilt from a hash-pinned source and every payload file is re-verified against
  the pinned hashes;
- the compatibility gate, the experimental hook patch, the native Agent Console patch and the
  native Agent chat entry patch apply to that target, and Morphe reports success;
- the patched APK keeps the official package name, version, version code and ABI, is signed,
  is aligned, and every DEX file disassembles;
- the original launcher Activity is still declared and the Agent Console Activity is added, declared as not exported and without a launcher filter;
- each hook call site reads the register that the original code just wrote, proven against the
  patched DEX rather than assumed;
- the guarded prompt is written back into the register that the original code reads, so the
  change of the prompt really reaches the request;
- the observer of the streaming set path runs after the update of the content, so it sees the
  value that the app stores;
- every class of the extension is really in the bundle of the patched APK, so a module cannot be
  missing without the verification failing;
- the chat entry call site sits directly in front of the return of `MainActivity.onCreate`, so
  the original code of the activity runs completely before the entry is added;
- the patch introduces no reference to a type that is not defined in any DEX file. The official
  APK already references optional third-party SDK types that it does not bundle, so the check is
  a comparison with the unpatched target and not an absolute one.

## Local checks

    bash scripts/test-agent-core.sh      # needs a JDK, runs the plain-Java regression test
    gradle --no-daemon buildAndroid      # builds the patch bundle and the extension
    python3 scripts/verify_patched_hooks.py --apk <patched.apk> --dex-dir <dex-dir>

The CI runs the same test on every push. The last CI result is the reference for
the state of the branch.

## Manual device checks still required

- Install a separately patched APK without uninstalling or erasing the user's official DeepSeek
  installation.
- Confirm the original DeepSeek `MainActivity` and the login/session flow remain unchanged.
- Enable proposal mode and verify that the SET/APPEND observer receives the full accumulated
  assistant content, with the expected callback frequency and without duplicated events.
- Confirm proposals are not executed, Dismiss removes them, and the Console is usable across
  rotation.
- Open the chat screen and check that the floating entry appears in a sensible place, that it
  opens the console, that it never covers the composer or a message, and that the official chat
  behaves exactly as before with the patch enabled and with it disabled.
- Confirm that the memories, the projects, the artifacts, the slash commands and the Generative
  UI survive an app restart and that the files stay inside the app-private directory.
- Confirm that the switches of the console survive a restart of the app and that the proposal
  mode is off again after a fresh installation.
- Add an MCP server with an https address, turn the network switch on, ask for the list of the
  tools and confirm that the call needs an approval that shows the address.
- Ask for a research address and confirm that the exact address is shown before anything is
  fetched and that nothing is fetched while the network switch is off.
- Try to leave the workspace with a path like `../sds-settings.xml` and confirm that the tool
  refuses it.
- Confirm that the export writes through the system file picker and that a cancelled export
  leaves no file behind.
