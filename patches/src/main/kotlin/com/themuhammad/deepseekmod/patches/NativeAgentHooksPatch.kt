package com.themuhammad.deepseekmod.patches

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.patch

/**
 * The request hook adds tool guidance only when proposal mode is enabled.
 * Response hooks observe full ResponseFragment content after SSE SET/APPEND updates.
 * No command or network tool is executed.
 *
 * Every register is resolved from the inspected bytecode by the shared checks, so the
 * hook always observes the value that the original code just wrote.
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
        // The prompt field read and the serializer call that consumes it.
        val prompt = resolvePromptAccess(ChatPromptSerializerFingerprint.method)

        ChatPromptSerializerFingerprint.method.addInstructions(
            prompt.serializerIndex,
            """
                invoke-static { v${prompt.promptRegister} }, Lcom/themuhammad/deepseekmod/extension/SdsAgentHooks;->beforePrompt(Ljava/lang/String;)Ljava/lang/String;
                move-result-object v${prompt.promptRegister}
            """
        )

        // Streaming SET: the replacement content is the argument of Ldea;->j(Object).
        val setUpdate = resolveSetContentUpdate(ChatResponseContentSetFingerprint.method)

        ChatResponseContentSetFingerprint.method.addInstructions(
            setUpdate.invokeIndex + 1,
            """
                invoke-static { v${setUpdate.contentRegister} }, Lcom/themuhammad/deepseekmod/extension/SdsAgentHooks;->observeResponse(Ljava/lang/String;)V
            """
        )

        // Streaming APPEND: the complete accumulated content is the second argument of
        // Ldea;->m(Object, Object) and is verified against the StringBuilder result.
        val appendedContent = resolveAppendedContent(ChatResponseContentAppendFingerprint.method)

        // The inspected code ignores the boolean result of the content update, so the
        // observer is inserted directly after the update call.
        ChatResponseContentAppendFingerprint.method.addInstructions(
            appendedContent.invokeIndex + 1,
            """
                invoke-static { v${appendedContent.contentRegister} }, Lcom/themuhammad/deepseekmod/extension/SdsAgentHooks;->observeResponse(Ljava/lang/String;)V
            """
        )
    }
}
