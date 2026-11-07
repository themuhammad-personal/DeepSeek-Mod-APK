# Native Agent Console

## What it is

The Morphe patch named Super DeepSeek native Agent Console adds a second launcher entry to the inspected DeepSeek 2.7.4 APK. It opens an Android-native Activity implemented in the DEX extension. The original DeepSeek launcher Activity and sign-in flow are not replaced.

Enable these patches in Morphe:

1. DeepSeek 2.7.4 compatibility check
2. Native Agent hook points (experimental)
3. Super DeepSeek native Agent Console

Both experimental patches are disabled by default.

## Current controls

**Tool proposal mode**

- Allow DeepSeek to propose native tools: enables prompt guidance for the current app process. It resets after process restart.

**Agent session**

- Approve: approves the pending proposal. Nothing runs before that; the approval only moves the session into `AWAITING_APPROVAL`.
- Deny: refuses the pending proposal and removes it from the queue.
- Stop: cancels a waiting or running tool and sets the cancellation flag. It never approves anything.

**Tool allow-list**

- The list shows every registered tool with its description and its exact argument schema, and marks the read-only ones. Only these tools can run.

**Observer diagnostics**

- The counters show the callbacks, the distinct and the duplicated contents, the growing and shrinking contents, the last and the maximum content length and the threads the callbacks arrived on. No assistant content is stored.
- Reset diagnostics clears the counters.

**Proposals**

- Approve: approves that proposal and starts it.
- Copy: copies the selected proposal's JSON arguments.
- Dismiss: removes a proposal from the queue. It does not approve or execute it.
- Clear: clears the queue and resets the session.
- Refresh: reads the in-memory proposal queue without consuming entries.

**Activity timeline**

- The timeline lists the recorded decisions in reverse order: proposal received, approved, denied, stop requested, run started, run finished and run failed. It holds at most 64 entries and contains no assistant content.

## Response source

The response observer is attached to the mutable SSE ResponseFragment content SET and APPEND methods in the inspected 2.7.4 APK. SET passes the replacement content. APPEND first combines old content with the new chunk and passes the complete accumulated string. A bounded parser detects complete SDS tool-call envelopes within that accumulated text and deduplicates them by call ID.

This is a static instruction-level finding. The CI verification proves that the patched DEX calls the observer with the register that carries the complete accumulated content, but it does not prove runtime callback frequency, final-message semantics, threading, lifecycle or Compose renderer alignment. The observer diagnostics are the instrument for the device run that has to close this gap.

## What a tool can do

Only `workspace.list`, `workspace.read` and `workspace.write` exist, and only inside the app-private workspace directory of the app. There is no shell, no process execution, no network tool and no access outside the workspace. Every path is resolved with a canonical-path check, and an argument that the tool does not declare is refused.

A tool that is not on the allow-list cannot run at all: the executor refuses it before any code of the tool is reached.

## Hard limitations

- The console is not a chat replacement and does not change the official chat screen.
- Tool results are **not** sent back to DeepSeek yet. There is no verified native flow for that, and the results are only shown in the console.
- The proposal queue is in-memory and proposal mode is session-only.
- The separate launcher entry is a development bridge. The final design should put an entry into DeepSeek chat navigation only after the Compose UI/navigation and renderer methods are mapped and a narrowly scoped hook is proven safe.

## Failure modes

| Situation | Behaviour |
| --- | --- |
| The proposal names an unknown tool | The executor refuses it with `tool is not registered` |
| The proposal was not approved | The executor refuses it with `approval is required` |
| The tool needs the network | The executor refuses it while the network policy forbids it |
| The path leaves the workspace | The tool returns a failed result with `the path was refused` |
| The arguments do not match the schema | The tool returns a failed result with `the arguments did not match the schema` |
| The tool exceeds the timeout | The executor interrupts it and reports `TIMED_OUT` |
| The user pressed Stop | The cancellation flag is set and the run reports `CANCELLED` |
| The tool threw | The executor reports the exception class and a sanitised message, without a stack trace and without the workspace path |

## Test

    bash scripts/test-agent-core.sh
    gradle --no-daemon buildAndroid
