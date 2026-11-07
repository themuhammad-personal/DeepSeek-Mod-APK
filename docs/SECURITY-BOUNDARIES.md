# Security and compatibility boundaries

This document has two parts. The first part states the rules that were never
broken. The second part states what the native runtime enforces today, so a
reader can see the boundary of every tool.

## Authentication and app integrity

These rules are absolute and were never touched by any patch in this repository:

- Preserve the official account sign-in and session flow.
- Do not bypass CAPTCHA, anti-fraud, anti-tamper, signature, root-detection, device-integrity or risk checks.
- Do not intercept TLS, install user certificates, extract session cookies/tokens or rewrite private authentication requests.
- Never log authorization headers, cookies, refresh tokens, personal chat content or MCP credentials.
- If the official app blocks a modified build or presents a risk/integrity failure, stop and report the exact non-sensitive error.

## What the native runtime enforces today

A model response is untrusted input. Nothing in the runtime executes a command, a file operation or a network call without the user seeing it first.

| Boundary | How it is enforced | Where |
|---|---|---|
| Only allow-listed tools run | The executor refuses a tool that is not in the registry of the console | `SdsToolExecutor`, `SdsToolRegistry` |
| Explicit approval | A tool only runs after the user approved that exact proposal; there is no path that runs a tool automatically | `SdsAgentSession`, `SdsAgentConsoleActivity` |
| Strict arguments | The arguments of a proposal must match the schema of the tool; unknown keys are refused | `SdsToolArguments` |
| Timeout | Every tool is interrupted when it exceeds the timeout of the policy | `SdsToolExecutor`, `SdsExecutionPolicy` |
| Cancellation | The Stop control cancels the running tool and the next turn is not affected | `SdsCancellation`, `SdsAgentSession` |
| Bounded output | The output of a tool is cut to the limit of the policy, again by the executor and not only by the tool | `SdsToolExecutor`, `SdsExecutionPolicy` |
| Workspace confinement | Every path is resolved to a canonical path inside the app-private workspace; an absolute path, a `..` path and a symlink that leaves the workspace are refused | `SdsWorkspaceGuard` |
| No arbitrary commands | There is no shell tool. The only command surface is the Linux Studio, it is off by default and it needs its own approval that shows the exact command | `SdsSandboxTool`, `SdsSandboxManager` |
| PRoot is not a kernel boundary | PRoot runs with the same user id as the app. The console and the documents say so in plain words | `SdsSandboxConfig`, `docs/NATIVE-FEATURE-MODULES.md` |
| Network off by default | The network switch is off until the user turns it on, and the network tools refuse to run while it is off | `SdsRuntimeRegistry`, `SdsMcpTools`, `SdsResearchTools` |
| HTTPS only, no redirect | The fetcher and the MCP client accept only `https` and never follow a redirect, so a token cannot reach another host | `SdsHttpFetcher`, `SdsMcpClient` |
| Bounded answers | The body of an answer is bounded, the JSON is parsed with a strict grammar and the tools of a server are bounded in number and in size | `SdsHttpFetcher`, `SdsJson`, `SdsMcpClient` |
| No hidden execution | Nothing runs in the background behind the screen. Every long action runs in a daemon worker of the console and the result is shown | `SdsAgentConsoleActivity` |
| The console is private | The console Activity is not exported and has no launcher entry, so only the app itself can open it | `NativeAgentConsolePatch.kt` |
| The data stays on the device | The memories, the projects, the artifacts, the settings and the tokens of the MCP servers live in the app-private directory and are removed with the app | `SdsAppContext`, `SdsSettings` |
| The data survives a crash | The stores are written through a temporary file that then takes the place of the target | `SdsAtomicFile` |
| Nothing sensitive is logged | The diagnostics keep the length and the tail of the answer, never the content, and the bound is limited | `SdsObserverDiagnostics` |
| Nothing is sent to DeepSeek without consent | The handback of a tool result is off by default and the user has to turn it on | `SdsToolResultHandoff` |

## What is still open

- The CPU, memory and disk limits of the Linux Studio are the limits of PRoot. A real kernel sandbox is not available on a normal Android device.
- The MCP servers are trusted by the user. A server can return anything within the bounds, and the result is shown to the user before it is put back into the chat.
- The patched APK is signed with another key than the official one. Android may refuse an update over the official installation.

## Distribution

- Do not commit the official APK, extracted third-party code, signing keys or generated patched APKs.
- A re-signed patched APK will not use DeepSeek's original certificate. Android may refuse an in-place update over the official installation. Do not uninstall the user's official app or erase its data as an automated workaround.
- Check applicable app terms and third-party licenses before distributing patches or modified binaries.
