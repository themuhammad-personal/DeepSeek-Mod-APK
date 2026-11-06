package com.themuhammad.deepseekmod.patches

import app.morphe.patcher.Fingerprint

/**
 * Static reverse-engineering notes from the supplied DeepSeek 2.7.4 decode.
 * These R8-obfuscated names are deliberately scoped to 2.7.4.
 */
internal object ChatPromptSerializerFingerprint : Fingerprint(
    definingClass = "Ljy1;",
    name = "e",
    returnType = "V",
    parameters = listOf("Lw94;", "Ljava/lang/Object;")
)

internal object ChatResponseContentFingerprint : Fingerprint(
    definingClass = "Lefa;",
    name = "d",
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Lrn3;")
)
