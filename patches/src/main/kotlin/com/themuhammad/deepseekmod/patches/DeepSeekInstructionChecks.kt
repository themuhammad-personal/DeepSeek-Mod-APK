package com.themuhammad.deepseekmod.patches

import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

/**
 * Instruction level checks for the inspected DeepSeek 2.7.4 chat path.
 *
 * The checks are shared by the compatibility gate and by the experimental hook patch
 * so that both agree on which register carries which value. Every check reads the
 * actual bytecode instead of assuming a register layout, so a changed build fails with
 * a clear message instead of patching the wrong value.
 */

internal const val PROMPT_FIELD_CLASS = "Lly1;"
internal const val PROMPT_FIELD_NAME = "c"
internal const val PROMPT_FIELD_TYPE = "Ljava/lang/String;"
internal const val PROMPT_SERIALIZER_CLASS = "Ly53;"
internal const val PROMPT_SERIALIZER_NAME = "x"
internal const val CONTENT_HOLDER_CLASS = "Ldea;"
internal const val CONTENT_SET_NAME = "j"
internal const val CONTENT_APPEND_NAME = "m"
internal const val STRING_BUILDER_CLASS = "Ljava/lang/StringBuilder;"
internal const val STRING_BUILDER_TO_STRING = "toString"

/** The prompt field read and the serializer call that consumes it. */
internal data class PromptAccess(
    /** Index of the prompt field read instruction. */
    val fieldIndex: Int,
    /** Index of the serializer call that consumes the prompt. */
    val serializerIndex: Int,
    /** Register that holds the prompt. */
    val promptRegister: Int
)

/** A content update call on the streaming content holder. */
internal data class ContentUpdate(
    /** Index of the content update instruction inside the method. */
    val invokeIndex: Int,
    /** Register that holds the content passed to the update. */
    val contentRegister: Int
)

/** Reads the method reference of an instruction, or null when it is not a method call. */
internal fun methodReferenceOf(instruction: Instruction): MethodReference? =
    (instruction as? ReferenceInstruction)?.reference as? MethodReference

/** Reads the field reference of an instruction, or null when it is not a field access. */
internal fun fieldReferenceOf(instruction: Instruction): FieldReference? =
    (instruction as? ReferenceInstruction)?.reference as? FieldReference

private fun instructionsOf(method: Method, description: String): List<Instruction> =
    method.implementation?.instructions?.toList()
        ?: error("DeepSeek $description has no implementation.")

/**
 * Resolves the prompt field read and the serializer call that consumes the prompt.
 *
 * The inspected serializer reads the prompt field into a register and passes that
 * register to the string serializer directly afterwards.
 */
internal fun resolvePromptAccess(method: Method): PromptAccess {
    val instructions = instructionsOf(method, "prompt serializer")
    val fieldIndex = instructions.indices.firstOrNull { index ->
        val reference = fieldReferenceOf(instructions[index])
        reference != null &&
            reference.definingClass == PROMPT_FIELD_CLASS &&
            reference.name == PROMPT_FIELD_NAME &&
            reference.type == PROMPT_FIELD_TYPE
    } ?: error("DeepSeek prompt field $PROMPT_FIELD_CLASS->$PROMPT_FIELD_NAME was not found.")

    val fieldInstruction = instructions[fieldIndex] as? TwoRegisterInstruction
        ?: error("DeepSeek prompt field read is not a two-register instruction.")

    val serializerIndex = instructions.indices.firstOrNull { index ->
        index > fieldIndex &&
            methodReferenceOf(instructions[index])?.let { reference ->
                reference.definingClass == PROMPT_SERIALIZER_CLASS &&
                    reference.name == PROMPT_SERIALIZER_NAME
            } == true
    } ?: error("DeepSeek prompt serializer call was not found.")

    check(serializerIndex == fieldIndex + 1) {
        "DeepSeek prompt serializer call does not directly follow the prompt field read."
    }
    return PromptAccess(
        fieldIndex = fieldIndex,
        serializerIndex = serializerIndex,
        promptRegister = fieldInstruction.registerA
    )
}

/**
 * Resolves the content update call of the streaming SET path.
 *
 * The inspected SET path decodes the replacement value and writes it into the content
 * holder with a single argument call.
 */
internal fun resolveSetContentUpdate(method: Method): ContentUpdate {
    val instructions = instructionsOf(method, "streaming ResponseFragment SET method")
    val updateIndex = instructions.indices.firstOrNull { index ->
        methodReferenceOf(instructions[index])?.let { reference ->
            reference.definingClass == CONTENT_HOLDER_CLASS &&
                reference.name == CONTENT_SET_NAME &&
                reference.parameterTypes == listOf("Ljava/lang/Object;")
        } == true
    } ?: error("DeepSeek streaming ResponseFragment SET update was not found.")

    val update = instructions[updateIndex] as? FiveRegisterInstruction
        ?: error("DeepSeek streaming ResponseFragment SET update has an unexpected instruction format.")
    check(update.registerCount == 2) {
        "DeepSeek streaming ResponseFragment SET update does not pass exactly one argument."
    }
    return ContentUpdate(invokeIndex = updateIndex, contentRegister = update.registerD)
}

/**
 * Resolves the content update call of the streaming APPEND path.
 *
 * The inspected APPEND path concatenates the previous content with the incoming chunk
 * through a StringBuilder and writes the result into the content holder with
 * `dea.m(Object, Object)`. The second argument of that call carries the complete
 * content, and the register is verified against the StringBuilder result instead of
 * being assumed.
 *
 * The inspected code ignores the boolean result of the content update, so the observer
 * has to be inserted directly after the update call.
 */
internal fun resolveAppendedContent(method: Method): ContentUpdate {
    val instructions = instructionsOf(method, "streaming ResponseFragment APPEND method")
    val appendIndex = instructions.indices.firstOrNull { index ->
        methodReferenceOf(instructions[index])?.let { reference ->
            reference.definingClass == CONTENT_HOLDER_CLASS &&
                reference.name == CONTENT_APPEND_NAME &&
                reference.parameterTypes == listOf("Ljava/lang/Object;", "Ljava/lang/Object;")
        } == true
    } ?: error("DeepSeek streaming ResponseFragment APPEND update was not found.")

    val appendInvoke = instructions[appendIndex] as? FiveRegisterInstruction
        ?: error("DeepSeek streaming ResponseFragment APPEND update has an unexpected instruction format.")
    check(appendInvoke.registerCount == 3) {
        "DeepSeek streaming ResponseFragment APPEND update does not pass two arguments."
    }
    val contentRegister = appendInvoke.registerE

    val toStringIndex = instructions.indices.lastOrNull { index ->
        index < appendIndex &&
            methodReferenceOf(instructions[index])?.let { reference ->
                reference.definingClass == STRING_BUILDER_CLASS &&
                    reference.name == STRING_BUILDER_TO_STRING
            } == true
    } ?: error("DeepSeek streaming ResponseFragment APPEND does not build the content with a StringBuilder.")

    val moveResult = instructions.getOrNull(toStringIndex + 1)
    check(moveResult != null && moveResult.opcode == Opcode.MOVE_RESULT_OBJECT) {
        "DeepSeek streaming ResponseFragment APPEND does not move the built content into a register."
    }
    val movedRegister = (moveResult as? OneRegisterInstruction)?.registerA
    check(movedRegister == contentRegister) {
        "DeepSeek streaming ResponseFragment APPEND content register could not be confirmed."
    }
    return ContentUpdate(invokeIndex = appendIndex, contentRegister = contentRegister)
}
