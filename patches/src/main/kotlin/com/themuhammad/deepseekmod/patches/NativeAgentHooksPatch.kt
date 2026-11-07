package com.themuhammad.deepseekmod.patches

import app.morphe.patcher.Match
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.patch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val PROMPT_FIELD_CLASS = "Lly1;"
private const val PROMPT_FIELD_NAME = "c"
private const val PROMPT_SERIALIZER_CLASS = "Ly53;"
private const val PROMPT_SERIALIZER_NAME = "x"
private const val CONTENT_HOLDER_CLASS = "Ldea;"
private const val CONTENT_SET_NAME = "j"
private const val CONTENT_APPEND_NAME = "m"
private const val STRING_BUILDER_CLASS = "Ljava/lang/StringBuilder;"

/** Reads the method reference of a matched instruction, or null when it is not a method call. */
private fun Match.InstructionMatch.methodCallReference(): MethodReference? =
    (instruction as? ReferenceInstruction)?.reference as? MethodReference

/** Reads the field reference of a matched instruction, or null when it is not a field access. */
private fun Match.InstructionMatch.fieldAccessReference(): FieldReference? =
    (instruction as? ReferenceInstruction)?.reference as? FieldReference

/**
 * Resolves the register that holds the complete accumulated assistant content.
 *
 * The inspected 2.7.4 APPEND path concatenates the previous content with the incoming
 * chunk through a StringBuilder and writes the result into the content holder with
 * `dea.m(Object, Object)`. The second argument of that call carries the complete
 * content. The register is verified against the StringBuilder result instead of being
 * assumed, so a changed build fails loudly instead of observing a wrong string.
 */
private fun resolveAppendedContentRegister(method: Method): Int {
    val instructions = method.implementation?.instructions
        ?: error("DeepSeek streaming ResponseFragment APPEND method has no implementation.")
    val appendIndex = instructions.indexOfFirst { instruction ->
        val reference = (instruction as? ReferenceInstruction)?.reference
        reference is MethodReference &&
            reference.definingClass == CONTENT_HOLDER_CLASS &&
            reference.name == CONTENT_APPEND_NAME
    }
    check(appendIndex >= 0) {
        "DeepSeek streaming ResponseFragment APPEND update was not found."
    }

    val appendInvoke = instructions[appendIndex] as? FiveRegisterInstruction
        ?: error("DeepSeek streaming ResponseFragment APPEND update has an unexpected instruction format.")
    check(appendInvoke.registerCount == 3) {
        "DeepSeek streaming ResponseFragment APPEND update does not pass two arguments."
    }
    val contentRegister = appendInvoke.registerE

    val toStringIndex = instructions.indices
        .filter { index ->
            index < appendIndex &&
                (instructions[index] as? ReferenceInstruction)?.reference.let { reference ->
                    reference is MethodReference &&
                        reference.definingClass == STRING_BUILDER_CLASS &&
                        reference.name == "toString"
                }
        }
        .lastOrNull()
        ?: error("DeepSeek streaming ResponseFragment APPEND does not build the content with a StringBuilder.")

    val moveResult = instructions.getOrNull(toStringIndex + 1)
    check(moveResult != null && moveResult.opcode == Opcode.MOVE_RESULT_OBJECT) {
        "DeepSeek streaming ResponseFragment APPEND does not move the built content into a register."
    }
    val movedRegister = (moveResult as? OneRegisterInstruction)?.registerA
    check(movedRegister == contentRegister) {
        "DeepSeek streaming ResponseFragment APPEND content register could not be confirmed."
    }
    return contentRegister
}

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

        // The prompt field read: iget-object vX, ..., Lly1;->c:Ljava/lang/String;
        val promptFieldMatch = promptMatches.firstOrNull { match ->
            val reference = match.fieldAccessReference()
            reference != null &&
                reference.definingClass == PROMPT_FIELD_CLASS &&
                reference.name == PROMPT_FIELD_NAME
        } ?: error("DeepSeek prompt field $PROMPT_FIELD_CLASS->$PROMPT_FIELD_NAME was not found.")

        // iget-object puts the read value in the first register of the instruction.
        val promptRegister = (promptFieldMatch.instruction as? TwoRegisterInstruction)?.registerA
            ?: error("DeepSeek prompt field read is not a two-register instruction.")

        // The serializer call that consumes the prompt: Ly53;->x(Lsr9;ILjava/lang/String;)V
        val promptWriteIndex = promptMatches.firstOrNull { match ->
            val reference = match.methodCallReference()
            reference != null &&
                reference.definingClass == PROMPT_SERIALIZER_CLASS &&
                reference.name == PROMPT_SERIALIZER_NAME
        }?.index ?: error("DeepSeek prompt serializer call was not found.")

        ChatPromptSerializerFingerprint.method.addInstructions(
            promptWriteIndex,
            """
                invoke-static { v$promptRegister }, Lcom/themuhammad/deepseekmod/extension/SdsAgentHooks;->beforePrompt(Ljava/lang/String;)Ljava/lang/String;
                move-result-object v$promptRegister
            """
        )

        // Streaming SET: the replacement content is the argument of Ldea;->j(Object).
        val setInvoke = ChatResponseContentSetFingerprint.instructionMatches.firstOrNull { match ->
            val reference = match.methodCallReference()
            reference != null &&
                reference.definingClass == CONTENT_HOLDER_CLASS &&
                reference.name == CONTENT_SET_NAME
        } ?: error("DeepSeek streaming ResponseFragment SET update was not found.")

        val setContentRegister = (setInvoke.instruction as? FiveRegisterInstruction)
            ?.takeIf { it.registerCount == 2 }
            ?.registerD
            ?: error("DeepSeek streaming ResponseFragment SET update has an unexpected register layout.")

        ChatResponseContentSetFingerprint.method.addInstructions(
            setInvoke.index + 1,
            """
                invoke-static { v$setContentRegister }, Lcom/themuhammad/deepseekmod/extension/SdsAgentHooks;->observeResponse(Ljava/lang/String;)V
            """
        )

        // Streaming APPEND: the complete accumulated content is the second argument of
        // Ldea;->m(Object, Object) and is verified against the StringBuilder result.
        val appendedContentRegister = resolveAppendedContentRegister(
            ChatResponseContentAppendFingerprint.method
        )

        val appendMoveResult = ChatResponseContentAppendFingerprint.instructionMatches.firstOrNull { match ->
            match.instruction.opcode == Opcode.MOVE_RESULT
        } ?: error("DeepSeek streaming ResponseFragment APPEND result was not found.")

        // Keep the original boolean move-result intact; add the observer after it.
        ChatResponseContentAppendFingerprint.method.addInstructions(
            appendMoveResult.index + 1,
            """
                invoke-static { v$appendedContentRegister }, Lcom/themuhammad/deepseekmod/extension/SdsAgentHooks;->observeResponse(Ljava/lang/String;)V
            """
        )
    }
}
