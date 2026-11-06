package com.themuhammad.deepseekmod.patches

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.patch
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

/**
 * The request hook adds tool guidance only when proposal mode is enabled.
 * Response hooks observe full ResponseFragment content after SSE SET/APPEND updates.
 * No command or network tool is executed.
 */
@Suppress("unused")
val nativeAgentHooksPatch = patch(
    name = "Native Agent hook points (experimental)",
    description = "Hooks the prompt serializer and the actual streaming ResponseFragment SET/APPEND mutations. Tool proposals are collected only.",
    default = false
) {
    compatibleWith(DEEPSEEK_2_7_4)
    dependsOn(compatibilityGatePatch)

    execute {
        val promptMatches = ChatPromptSerializerFingerprint.instructionMatches
        val promptRegister = promptMatches[0]
            .getInstruction<TwoRegisterInstruction>().registerA
        val promptWriteIndex = promptMatches[1].index

        ChatPromptSerializerFingerprint.method.addInstructions(
            promptWriteIndex,
            """
                invoke-static { v$promptRegister }, Lcom/themuhammad/deepseekmod/extension/SdsAgentHooks;->beforePrompt(Ljava/lang/String;)Ljava/lang/String;
                move-result-object v$promptRegister
            """
        )

        val setCall = ChatResponseContentSetFingerprint.instructionMatches.last()
        val setContentRegister = setCall
            .getInstruction<FiveRegisterInstruction>().registerD
        ChatResponseContentSetFingerprint.method.addInstructions(
            setCall.index + 1,
            """
                invoke-static { v$setContentRegister }, Lcom/themuhammad/deepseekmod/extension/SdsAgentHooks;->observeResponse(Ljava/lang/String;)V
            """
        )

        val appendCall = ChatResponseContentAppendFingerprint.instructionMatches
            .first { it.getInstruction<FiveRegisterInstruction>().registerCount == 3 }
        val appendedContentRegister = appendCall
            .getInstruction<FiveRegisterInstruction>().registerE
        // Keep the original boolean move-result intact; add the observer after it.
        ChatResponseContentAppendFingerprint.method.addInstructions(
            appendCall.index + 2,
            """
                invoke-static { v$appendedContentRegister }, Lcom/themuhammad/deepseekmod/extension/SdsAgentHooks;->observeResponse(Ljava/lang/String;)V
            """
        )
    }
}
