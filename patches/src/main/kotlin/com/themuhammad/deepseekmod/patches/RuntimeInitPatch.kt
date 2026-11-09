/*
 * This patch follows the structure of the Morphe patch template:
 * https://github.com/MorpheApp/morphe-patches-template
 *
 * The Morphe patcher is used under its own licence. This project is not a
 * part of the Morphe project and does not use its name.
 */

package com.themuhammad.deepseekmod.patches

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.patch

/**
 * Prepares the Super DeepSeek runtime (settings, memory, artifacts, MCP config)
 * when the launcher Activity is created, so the switches of the user are
 * restored before the first prompt is sent.
 *
 * This replaces the initialisation that used to live in the removed Agent
 * Console patch. It adds no screen, no manifest entry and no view.
 */
@Suppress("unused")
val superDeepSeekRuntimeInitPatch = patch(
    name = "Super DeepSeek runtime init",
    description = "Initialises the Super DeepSeek runtime at the end of MainActivity.onCreate.",
    default = true
) {
    compatibleWith(DEEPSEEK_2_7_5_VERSION_CODE_296)
    dependsOn(nativeAgentHooksPatch)

    execute {
        val onCreate = MainActivityOnCreateFingerprint.method
        val returnIndex = resolveReturnIndex(onCreate)
        onCreate.addInstructions(
            returnIndex,
            """
                invoke-static { p0 }, Lcom/themuhammad/deepseekmod/extension/SdsAppContext;->initialize(Landroid/content/Context;)V
            """
        )
    }
}
