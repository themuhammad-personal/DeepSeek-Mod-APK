package com.themuhammad.deepseekmod.patches

import app.morphe.patcher.patch.patch

/**
 * Compatibility-only checks for the exact inspected DeepSeek 2.7.4 sample.
 * No authentication, TLS, account, anti-fraud or device-integrity behaviour is changed.
 */
@Suppress("unused")
val compatibilityGatePatch = patch(
    name = "DeepSeek 2.7.4 compatibility check",
    description = "Checks the prompt serializer and mutable SSE ResponseFragment SET/APPEND methods.",
    default = true
) {
    compatibleWith(DEEPSEEK_2_7_4)
    extendWith("extensions/sds-core.mpe")

    execute {
        val promptMethod = ChatPromptSerializerFingerprint.method
        val responseSetMethod = ChatResponseContentSetFingerprint.method
        val responseAppendMethod = ChatResponseContentAppendFingerprint.method
        check(promptMethod.implementation != null) {
            "DeepSeek prompt serializer has no implementation."
        }
        check(responseSetMethod.implementation != null) {
            "DeepSeek streaming ResponseFragment SET method has no implementation."
        }
        check(responseAppendMethod.implementation != null) {
            "DeepSeek streaming ResponseFragment APPEND method has no implementation."
        }
    }
}
