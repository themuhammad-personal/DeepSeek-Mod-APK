package com.themuhammad.deepseekmod.patches

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

/** Prompt field on the inspected DeepSeek 2.7.4 request serializer. */
internal object ChatPromptSerializerFingerprint : Fingerprint(
    definingClass = "Ljy1;",
    name = "e",
    returnType = "V",
    parameters = listOf("Lw94;", "Ljava/lang/Object;"),
    filters = listOf(
        fieldAccess(
            definingClass = "Lly1;",
            name = "c",
            type = "Ljava/lang/String;",
            opcode = Opcode.IGET_OBJECT
        ),
        methodCall(
            definingClass = "Ly53;",
            name = "x",
            parameters = listOf("Lsr9;", "I", "Ljava/lang/String;"),
            returnType = "V",
            location = MatchAfterImmediately()
        )
    )
)

/**
 * The SSE JSON-patch path resolves ResponseFragment.content to a mutable v44.
 * SET decodes a replacement String then updates its state holder with dea.j().
 */
internal object ChatResponseContentSetFingerprint : Fingerprint(
    definingClass = "Lv44;",
    name = "l",
    returnType = "V",
    parameters = listOf("Ljava/lang/String;", "Lc26;", "Lgv1;"),
    filters = listOf(
        string("content"),
        methodCall(
            definingClass = "Ldea;",
            name = "j",
            parameters = listOf("Ljava/lang/Object;"),
            returnType = "V"
        )
    )
)

/**
 * SSE APPEND decodes the incoming chunk, joins it to the current content, then
 * updates the holder with dea.m(). The second value argument is the full String.
 *
 * The inspected code ignores the boolean result of dea.m(), so there is no
 * move-result instruction after the call. A fingerprint that expects one does
 * not resolve against the inspected build.
 */
internal object ChatResponseContentAppendFingerprint : Fingerprint(
    definingClass = "Lv44;",
    name = "m",
    returnType = "V",
    parameters = listOf("Lc26;", "Ljava/lang/String;"),
    filters = listOf(
        string("content"),
        methodCall(
            definingClass = "Ldea;",
            name = "m",
            parameters = listOf("Ljava/lang/Object;", "Ljava/lang/Object;"),
            returnType = "Z"
        )
    )
)
