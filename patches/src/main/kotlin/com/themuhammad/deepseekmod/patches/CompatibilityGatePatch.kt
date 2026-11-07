/*
 * This patch follows the structure of the Morphe patch template:
 * https://github.com/MorpheApp/morphe-patches-template
 *
 * The Morphe patcher is used under its own licence. This project is not a
 * part of the Morphe project and does not use its name.
 */

package com.themuhammad.deepseekmod.patches

import app.morphe.patcher.patch.packageMetadata
import app.morphe.patcher.patch.patch

/**
 * Compatibility-only checks for the inspected DeepSeek 2.7.4 samples.
 * No authentication, TLS, account, anti-fraud or device-integrity behaviour is changed.
 *
 * The gate is a real pre-flight validator: it resolves the fingerprints of the build
 * that is being patched and then proves that the instructions the experimental hooks
 * rely on are present in the inspected shape. A build that no longer matches fails here
 * with a readable message instead of being patched at the wrong instruction.
 *
 * The build is recognised by its version code, so the fingerprints of one build are
 * never used for another build.
 */
@Suppress("unused")
val compatibilityGatePatch = patch(
    name = "DeepSeek 2.7.4 compatibility check",
    description = "Validates the prompt serializer and the mutable SSE ResponseFragment SET/APPEND methods before any hook is applied.",
    default = true
) {
    compatibleWith(
        DEEPSEEK_2_7_4,
        DEEPSEEK_2_7_4_VERSION_CODE_294,
        DEEPSEEK_2_7_5_VERSION_CODE_295,
        DEEPSEEK_2_7_5_VERSION_CODE_296
    )
    extendWith("extensions/sds-core.mpe")

    execute {
        val profile = deepSeekTargetProfile(packageMetadata.versionCode)
        val promptMethod = profile.promptSerializer.methodOrNull
            ?: error("The DeepSeek prompt serializer of ${profile.label} was not found.")
        val responseSetMethod = profile.responseContentSet.methodOrNull
            ?: error("The DeepSeek streaming ResponseFragment SET method of ${profile.label} was not found.")
        val responseAppendMethod = profile.responseContentAppend.methodOrNull
            ?: error("The DeepSeek streaming ResponseFragment APPEND method of ${profile.label} was not found.")

        // Resolving the registers proves that the inspected instruction shapes are present.
        val prompt = resolvePromptAccess(promptMethod, profile)
        val setUpdate = resolveSetContentUpdate(responseSetMethod, profile)
        val appendedContent = resolveAppendedContent(responseAppendMethod, profile)

        println(
            "DeepSeek compatibility check passed for ${profile.label}: " +
                "prompt register v${prompt.promptRegister}, " +
                "SET content register v${setUpdate.contentRegister}, " +
                "APPEND content register v${appendedContent.contentRegister} " +
                "after instruction ${appendedContent.invokeIndex}."
        )
    }
}
