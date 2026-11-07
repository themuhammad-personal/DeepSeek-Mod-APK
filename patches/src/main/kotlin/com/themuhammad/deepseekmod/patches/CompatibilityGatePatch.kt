package com.themuhammad.deepseekmod.patches

import app.morphe.patcher.patch.patch

/**
 * Compatibility-only checks for the exact inspected DeepSeek 2.7.4 sample.
 * No authentication, TLS, account, anti-fraud or device-integrity behaviour is changed.
 *
 * The gate is a real pre-flight validator: it resolves the fingerprints and then proves
 * that the instructions the experimental hooks rely on are present in the inspected shape.
 * A build that no longer matches fails here with a readable message instead of being
 * patched at the wrong instruction.
 */
@Suppress("unused")
val compatibilityGatePatch = patch(
    name = "DeepSeek 2.7.4 compatibility check",
    description = "Validates the prompt serializer and the mutable SSE ResponseFragment SET/APPEND methods before any hook is applied.",
    default = true
) {
    compatibleWith(DEEPSEEK_2_7_4)
    extendWith("extensions/sds-core.mpe")

    execute {
        val promptMethod = ChatPromptSerializerFingerprint.methodOrNull
            ?: error("DeepSeek prompt serializer Ljy1;->e(Lw94;Ljava/lang/Object;)V was not found.")
        val responseSetMethod = ChatResponseContentSetFingerprint.methodOrNull
            ?: error("DeepSeek streaming ResponseFragment SET method Lv44;->l was not found.")
        val responseAppendMethod = ChatResponseContentAppendFingerprint.methodOrNull
            ?: error("DeepSeek streaming ResponseFragment APPEND method Lv44;->m was not found.")

        // Resolving the registers proves that the inspected instruction shapes are present.
        val prompt = resolvePromptAccess(promptMethod)
        val setUpdate = resolveSetContentUpdate(responseSetMethod)
        val appendedContent = resolveAppendedContent(responseAppendMethod)

        println(
            "DeepSeek 2.7.4 compatibility check passed: " +
                "prompt register v${prompt.promptRegister}, " +
                "SET content register v${setUpdate.contentRegister}, " +
                "APPEND content register v${appendedContent.contentRegister} " +
                "after instruction ${appendedContent.invokeIndex}."
        )
    }
}
