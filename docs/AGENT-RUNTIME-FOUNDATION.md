# Native Agent Runtime Foundation

## Implemented in the first native extension slice

- SdsExecutionPolicy: approval-first defaults, bounded timeouts, bounded output, network disabled by default, and a per-turn tool-call cap.
- SdsWorkspaceGuard: canonical-path check that rejects absolute paths, NUL bytes, traversal and symlink-resolved escapes.
- SdsToolCall: immutable envelope for an untrusted tool request. Arguments remain raw JSON until a registered adapter validates its schema.
- SdsToolResult: bounded result envelope with explicit approval, denial, failure, cancellation and timeout states.

## Not yet implemented

These classes are foundations only. They do not launch commands, access the network, start an agent loop or connect to the DeepSeek request/response pipeline. There is no hidden command execution.

## Required integration order

1. Identify and verify the actual response stream and Compose rendering path.
2. Parse complete tool calls with size limits and strict schemas.
3. Present the exact tool and arguments to the user for approval.
4. Dispatch only registered tools using the safe execution policy.
5. Emit bounded results and a visible activity event.
6. Feed results back only through a verified supported request flow.
7. Test Stop, timeout, cancellation, screen rotation, process death and failure handling on a real device.

## Security caveat

Canonical path checks are not a kernel-level security boundary and can still have time-of-check/time-of-use races. PRoot runs under the app UID; it must not be described as hardware or kernel isolation.
