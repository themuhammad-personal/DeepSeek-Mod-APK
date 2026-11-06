# Native Agent Console

## What it is

The Morphe patch named Super DeepSeek native Agent Console adds a second launcher entry to the inspected DeepSeek 2.7.4 APK. It opens an Android-native Activity implemented in the DEX extension. The original DeepSeek launcher Activity and sign-in flow are not replaced.

Enable these patches in Morphe:

1. DeepSeek 2.7.4 compatibility check
2. Native Agent hook points (experimental)
3. Super DeepSeek native Agent Console

Both experimental patches are disabled by default.

## Current controls

- Allow DeepSeek to propose native tools: enables prompt guidance for the current app process. It resets after process restart.
- Refresh: reads the in-memory proposal queue without consuming entries.
- Copy: copies the selected proposal's JSON arguments.
- Dismiss: removes a proposal from the queue. It does not approve or execute it.
- Clear: clears the queue.

## Response source

The response observer is attached to the mutable SSE ResponseFragment content SET and APPEND methods in the inspected 2.7.4 APK. SET passes the replacement content. APPEND first combines old content with the new chunk and passes the complete accumulated string. A bounded parser detects complete SDS tool-call envelopes within that accumulated text and deduplicates them by call ID.

This is a static instruction-level finding. It does not yet prove runtime callback frequency, final-message semantics, threading, lifecycle, or Compose renderer alignment. Device verification is required before treating it as a reliable agent event source.

## Hard limitations

The console intentionally has no shell, MCP, network, file-write or arbitrary code execution. It does not provide an Approve/Run button because there is no verified executor or complete tool-result handoff. The proposal queue is in-memory and mode is session-only.

The separate launcher entry is a development bridge. The final design should put an entry into DeepSeek chat navigation only after the Compose UI/navigation and renderer methods are mapped and a narrowly scoped hook is proven safe.

## Test

    bash scripts/test-agent-core.sh
    gradle --no-daemon buildAndroid
