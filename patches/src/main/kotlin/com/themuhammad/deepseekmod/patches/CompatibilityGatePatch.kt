package com.themuhammad.deepseekmod.patches

import app.morphe.patcher.patch.patch

/**
 * Deliberately non-invasive. Resolves the two statically identified methods;
 * it does not change authentication, network traffic, app UI or response content.
 */
@Suppress("unused")
internal val compatibilityGatePatch = patch(
    name = "DeepSeek 2.7.4 compatibility check",
    description = "Checks the prompt-serializer and response-content method fingerprints.",
    default = true
) {
    compatibleWith(DEEPSEEK_2_7_4)

    execute {
        val promptMethod = ChatPromptSerializerFingerprint.method
        val responseMethod = ChatResponseContentFingerprint.method
        check(promptMethod.implementation != null) {
            "DeepSeek prompt serializer has no implementation."
        }
        check(responseMethod.implementation != null) {
            "DeepSeek response deserializer has no implementation."
        }
    }
}
