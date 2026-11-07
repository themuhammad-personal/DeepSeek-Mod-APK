# Device test checklist

This is the procedure for the one thing that is still missing: the proof that
the native runtime works on a real phone with the official DeepSeek app.

Everything in this document is written so that a second person can follow it
without reading the code. It is the counterpart of the static proofs in
docs/P0-VERIFICATION.md.

## 0. Rules during the test

- **Do not write any chat content, no token, no cookie and no screenshot of the
  chat into a report.** The diagnostics of the runtime are built for exactly
  this: they count the callbacks, they count the characters and they keep a
  salted digest, never the text of an answer.
- **Do not uninstall the official DeepSeek app** unless the test is finished and
  you have decided to. The patched app has another signature, so Android cannot
  update the official installation in place. A second phone or a work profile is
  the safe way to test.
- **Do not publish the patched APK.** It carries the payload of the official app
  and it is signed with a local key.

## 1. Build

```bash
bash scripts/build-patched-apk.sh --mpp <bundle.mpp> --source-file <extraction.zip>
```

The script prints the SHA-256 of the result and runs the hook proof on the
patched DEX. Keep that output; it belongs in the report.

Requirements: a JDK 21, python3, curl, unzip and the Android SDK with the build
tools 36.0.0.

## 2. Install

```bash
adb install -r build-local/deepseek-patched.apk
```

If the official app is on the same device, the install fails with a signature
mismatch. That is expected. Use a second device, or remove the official app
first (that removes its data).

## 3. The first run: the official app must stay untouched

| # | Do | Expect | Pass when |
|---|---|---|---|
| 1 | Start the app, log in with the official flow | The login screen behaves exactly like the official app | The login, the session and the captcha are unchanged, nothing is asked for that the official app does not ask for |
| 2 | Open a chat and send a normal message | The answer arrives and is rendered | The answer is complete, no duplicates, no flicker |
| 3 | Send a message that asks for a tool | Nothing happens | **No tool runs.** The console shows a proposal and waits |
| 4 | Open the console from the "Agent" pill | The console opens | The pill is visible, it does not cover the composer or a message, one tap opens the console |

## 4. The observer

Turn the switch **"Tool proposal mode"** on and send a long answer (ask for a
list of 30 items so the answer streams in many chunks).

| # | Look at | Expect |
|---|---|---|
| 5 | The diagnostics of the console | `callbacks` grows while the answer streams, `distinct` grows with it, `duplicates` stays small, `grow` grows, `shrink` stays 0 |
| 6 | `last` and `max` chars | They grow with the answer and end at the length of the answer |
| 7 | `threads` | The number of threads that delivered the callbacks. It may be more than one, but it must not grow without a bound |
| 8 | The chat screen | No stall, no flicker, the answer is not cut off, the text does not jump back |

Copy the summary of the diagnostics into the report. It contains no content.

## 5. The proposals

| # | Do | Expect |
|---|---|---|
| 9 | Approve a proposal of the file tools | The tool runs, the result is shown in the console, the timeline holds the decision |
| 10 | Deny a proposal | Nothing runs, the proposal leaves the queue, the timeline holds the decision |
| 11 | Approve a proposal while another tool runs | The approval is refused with a message, the older proposal is **not** approved by mistake |
| 12 | Press Stop while a tool runs | The tool stops, the state shows the cancellation, the next turn is not poisoned by it |
| 13 | Restart the app | The switches are still on, the queue is empty (it lives in the memory of the process) |

## 6. The features, one by one

| # | Feature | Do | Expect |
|---|---|---|---|
| 14 | Memories | Add a memory, then `/memory` in the chat | The memory is listed and can be deleted |
| 15 | Memories in the prompt | Turn the switch on and ask something personal | The prompt carries the memory block |
| 16 | Slash commands | `/help`, `/files`, `/projects` | The command is expanded, an unknown command leaves the text untouched |
| 17 | Projects | Create a project | The project is listed and survives a restart |
| 18 | Artifacts | Let the assistant store an artifact | The artifact is listed, it can be viewed, saved and shared |
| 19 | Export | Export the timeline, then cancel a save | The file is written where the picker says, a cancelled export leaves nothing behind |
| 20 | Generative UI | Let the assistant ask for an interface | Plain views are shown, no web view, nothing executes |
| 21 | File tools | Read and write a file in the workspace | Only the private workspace is touched, a path that leaves it is refused |
| 22 | Document picker | Pick a document and import it | The document arrives in the workspace, the app asked for no permission |
| 23 | MCP | Add a server, turn the network on, list the tools | The handshake runs, the tools of the server appear, every call asks for an approval |
| 24 | Deep Research | Fetch an address | The exact address is shown before the fetch, the network switch is honoured |
| 25 | Linux Studio | Download the root filesystem | The checksum is checked, the archive is unpacked, the studio says that the runtime is missing (the PRoot binaries are not bundled) |

## 7. The handback of a tool result

Turn the switch **"Return the result of a tool to the chat"** on.

| # | Do | Expect |
|---|---|---|
| 26 | Approve a tool and continue the chat | The result reaches the conversation, the marker appears exactly once |
| 27 | Send another message | The result is not repeated |

## 8. The stability

| # | Do | Expect |
|---|---|---|
| 28 | Rotate the phone with the console open | The console comes back, the state is the same |
| 29 | Send the app to the background and back | Nothing breaks, the queue is the same |
| 30 | Kill the app from the recent apps and start it again | The switches are back, no store is empty, no file is half written |
| 31 | Turn the airplane mode on and try a network tool | The tool is refused with a message, nothing hangs |

## 9. The report

Copy this template and fill it in. Attach the summary of the diagnostics and
nothing else.

```
Device test of the native agent runtime
Date:
Device (model, Android version, API level):
Build SHA-256 of the patched APK:
The build came from the local builder: yes / no

Official app unchanged (login, session, chat):        pass / fail
The observer fires with the complete content:          pass / fail
  callbacks:      distinct:      duplicates:      grow:      shrink:
  last chars:     max chars:     threads:
The chat does not stall while the observer runs:       pass / fail
A proposal never runs without an approval:            pass / fail
The approval applies to the proposal that was chosen:  pass / fail
Stop cancels the running tool and not the next turn:  pass / fail
The switches survive a restart:                       pass / fail
Memories:      Slash commands:   Projects:
Artifacts:     Export:           Generative UI:
File tools:    Document picker:  MCP:      Deep Research:      Linux Studio:
The result of a tool reaches the chat exactly once:   pass / fail
Rotation, background, restart and offline mode:       pass / fail

Problems that were found:
How they were reproduced:
What was changed to fix them:
```