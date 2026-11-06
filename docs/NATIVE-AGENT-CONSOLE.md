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

## Hard limitations

The console intentionally has no shell, MCP, network, file-write or arbitrary code execution. It does not provide an Approve/Run button because there is no verified executor or complete tool-result handoff.

The response parser currently observes the content value from a statically inspected deserializer. This does not prove the callback is once-per-message, final-only, or aligned with Compose rendering. Runtime tracing and a real device are required before using it as a dependable agent event source.

The separate launcher entry is a bridge for development. The final design should put an entry into the DeepSeek chat navigation only after the Compose UI/navigation and renderer methods are mapped and a narrowly scoped hook is proven safe.

## Test

    bash scripts/test-agent-core.sh
    gradle --no-daemon buildAndroid
