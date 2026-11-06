package com.themuhammad.deepseekmod.patches

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.patch
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

/**
 * Experimental wiring only. The extension defaults to agent-disabled and only queues
 * parsed proposals; it never executes shell commands or network tools.
 */
@Suppress("unused")
val nativeAgentHooksPatch = patch(
    name = "Native Agent hook points (experimental)",
    description = "Connects the verified prompt and response seams to the approval-first native proposal queue. No tools execute.",
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

        val responseMatches = ChatResponseContentFingerprint.instructionMatches
        val contentRegister = responseMatches[2]
            .getInstruction<OneRegisterInstruction>().registerA
        val contentReadIndex = responseMatches[2].index

        ChatResponseContentFingerprint.method.addInstructions(
            contentReadIndex + 1,
            """
                invoke-static { v$contentRegister }, Lcom/themuhammad/deepseekmod/extension/SdsAgentHooks;->observeResponse(Ljava/lang/String;)V
            """
        )
    }
}
