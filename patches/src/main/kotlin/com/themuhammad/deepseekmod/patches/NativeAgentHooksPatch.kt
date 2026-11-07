package com.themuhammad.deepseekmod.patches

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.packageMetadata
import app.morphe.patcher.patch.patch

/**
 * The request hook adds tool guidance only when proposal mode is enabled.
 * Response hooks observe full ResponseFragment content after SSE SET/APPEND updates.
 * No command or network tool is executed.
 *
 * Every register is resolved from the inspected bytecode by the shared checks, so the
 * hook always observes the value that the original code just wrote. The fingerprints
 * are taken from the profile of the build that is being patched.
 */
@Suppress("unused")
val nativeAgentHooksPatch = patch(
    name = "Native Agent hook points (experimental)",
    description = "Hooks the prompt serializer and the actual streaming ResponseFragment SET/APPEND mutations. Tool proposals are collected only and the handback of a tool result is off by default.",
    default = false
) {
    compatibleWith(DEEPSEEK_2_7_4, DEEPSEEK_2_7_4_VERSION_CODE_294)
    dependsOn(compatibilityGatePatch)

    execute {
        val profile = deepSeekTargetProfile(packageMetadata.versionCode)

        // The prompt field read and the serializer call that consumes it.
        val prompt = resolvePromptAccess(profile.promptSerializer.method, profile)

        profile.promptSerializer.method.addInstructions(
            prompt.serializerIndex,
            """
                invoke-static { v${prompt.promptRegister} }, Lcom/themuhammad/deepseekmod/extension/SdsAgentHooks;->beforePrompt(Ljava/lang/String;)Ljava/lang/String;
                move-result-object v${prompt.promptRegister}
            """
        )

        // Streaming SET: the replacement content is the argument of the content update.
        val setUpdate = resolveSetContentUpdate(profile.responseContentSet.method, profile)

        profile.responseContentSet.method.addInstructions(
            setUpdate.invokeIndex + 1,
            """
                invoke-static { v${setUpdate.contentRegister} }, Lcom/themuhammad/deepseekmod/extension/SdsAgentHooks;->observeResponse(Ljava/lang/String;)V
            """
        )

        // Streaming APPEND: the complete accumulated content is the second argument of
        // the content update and is verified against the StringBuilder result.
        val appendedContent = resolveAppendedContent(profile.responseContentAppend.method, profile)

        // The inspected code ignores the boolean result of the content update, so the
        // observer is inserted directly after the update call.
        profile.responseContentAppend.method.addInstructions(
            appendedContent.invokeIndex + 1,
            """
                invoke-static { v${appendedContent.contentRegister} }, Lcom/themuhammad/deepseekmod/extension/SdsAgentHooks;->observeResponse(Ljava/lang/String;)V
            """
        )

        // The handback of a tool result has to happen before the update, because the
        // value that is written into the content holder is the value that the chat
        // renders. The hook is off by default and it never writes twice.
        profile.responseContentAppend.method.addInstructions(
            appendedContent.invokeIndex,
            """
                invoke-static { v${appendedContent.contentRegister} }, Lcom/themuhammad/deepseekmod/extension/SdsAgentHooks;->observeResponseAppend(Ljava/lang/String;)Ljava/lang/String;
                move-result-object v${appendedContent.contentRegister}
            """
        )
    }
}
