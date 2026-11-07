#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
SRC="$ROOT/extensions/sds-core/src/main/java/com/themuhammad/deepseekmod/extension"
TEST="$ROOT/extensions/sds-core/src/test/java/com/themuhammad/deepseekmod/extension/SdsAgentCoreSelfTest.java"

javac -encoding UTF-8 -d "$TMP/classes" \
  "$SRC/SdsCoreMarker.java" \
  "$SRC/SdsExecutionPolicy.java" \
  "$SRC/SdsToolCall.java" \
  "$SRC/SdsToolResult.java" \
  "$SRC/SdsWorkspaceGuard.java" \
  "$SRC/SdsToolCallParser.java" \
  "$SRC/SdsObserverDiagnostics.java" \
  "$SRC/SdsCancellation.java" \
  "$SRC/SdsAgentEvent.java" \
  "$SRC/SdsAgentSession.java" \
  "$SRC/SdsToolArguments.java" \
  "$SRC/SdsTool.java" \
  "$SRC/SdsToolContext.java" \
  "$SRC/SdsToolRegistry.java" \
  "$SRC/SdsToolExecutor.java" \
  "$SRC/SdsWorkspaceTools.java" \
  "$SRC/SdsMemoryStore.java" \
  "$SRC/SdsProjectRegistry.java" \
  "$SRC/SdsArtifactStore.java" \
  "$SRC/SdsArtifactTools.java" \
  "$SRC/SdsUiSpec.java" \
  "$SRC/SdsUiTools.java" \
  "$SRC/SdsSlashCommands.java" \
  "$SRC/SdsRuntimeRegistry.java" \
  "$SRC/SdsAgentHooks.java"
javac -encoding UTF-8 -cp "$TMP/classes" -d "$TMP/classes" "$TEST"
java -cp "$TMP/classes" com.themuhammad.deepseekmod.extension.SdsAgentCoreSelfTest
