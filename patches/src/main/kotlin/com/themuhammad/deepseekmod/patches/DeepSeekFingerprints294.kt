package com.themuhammad.deepseekmod.patches

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

/**
 * The fingerprint set of the DeepSeek 2.7.4 build with version code 294.
 *
 * The build with version code 294 is a different obfuscation of the same app
 * version: every class name of the build with version code 293 changed, so the
 * fingerprint set of that build does not resolve here at all. The names in this
 * file were read out of the dex files of the build with version code 294 and
 * they belong to that build only.
 *
 * The three seams below have the same shape as the seams of the build with
 * version code 293:
 *
 *  - La02;->b serializes the request and writes the prompt field Lc02;->c
 *    with Lv73;->x, which is the JSON writer of this build,
 *  - Lv64;->l is the SSE path that replaces the content and updates the
 *    content holder Leia; with Leia;->j,
 *  - Lv64;->m is the SSE path that appends to the content and updates the
 *    content holder Leia; with Leia;->m.
 */

/** Prompt field on the inspected DeepSeek 2.7.4 request serializer of version code 294. */
internal object ChatPromptSerializerFingerprint294 : Fingerprint(
    definingClass = "La02;",
    name = "b",
    returnType = "V",
    parameters = listOf("Lwb4;", "Ljava/lang/Object;"),
    filters = listOf(
        fieldAccess(
            definingClass = "Lc02;",
            name = "c",
            type = "Ljava/lang/String;",
            opcode = Opcode.IGET_OBJECT
        ),
        methodCall(
            definingClass = "Lv73;",
            name = "x",
            parameters = listOf("Lqv9;", "I", "Ljava/lang/String;"),
            returnType = "V",
            location = MatchAfterImmediately()
        )
    )
)

/**
 * The SSE JSON-patch path of the build with version code 294 resolves
 * ResponseFragment.content to a mutable field and updates its state holder
 * with Leia;->j.
 */
internal object ChatResponseContentSetFingerprint294 : Fingerprint(
    definingClass = "Lv64;",
    name = "l",
    returnType = "V",
    parameters = listOf("Ljava/lang/String;", "Ls46;", "Lxw1;"),
    filters = listOf(
        string("content"),
        methodCall(
            definingClass = "Leia;",
            name = "j",
            parameters = listOf("Ljava/lang/Object;"),
            returnType = "V"
        )
    )
)

/**
 * SSE APPEND of the build with version code 294 decodes the incoming chunk,
 * joins it to the current content and updates the holder with Leia;->m(). The
 * second value argument is the full String.
 *
 * The inspected code ignores the boolean result of the content update, so
 * there is no move-result instruction after the call.
 */
internal object ChatResponseContentAppendFingerprint294 : Fingerprint(
    definingClass = "Lv64;",
    name = "m",
    returnType = "V",
    parameters = listOf("Ls46;", "Ljava/lang/String;"),
    filters = listOf(
        string("content"),
        methodCall(
            definingClass = "Leia;",
            name = "m",
            parameters = listOf("Ljava/lang/Object;", "Ljava/lang/Object;"),
            returnType = "Z"
        )
    )
)

/** The profile of the inspected DeepSeek 2.7.4 build with version code 294. */
internal val deepSeekTargetProfile294 = DeepSeekTargetProfile(
    versionCode = 294,
    label = "DeepSeek 2.7.4, version code 294",
    promptSerializer = ChatPromptSerializerFingerprint294,
    responseContentSet = ChatResponseContentSetFingerprint294,
    responseContentAppend = ChatResponseContentAppendFingerprint294,
    promptFieldClass = "Lc02;",
    promptFieldName = "c",
    promptFieldType = "Ljava/lang/String;",
    promptSerializerClass = "Lv73;",
    promptSerializerName = "x",
    contentHolderClass = "Leia;"
)
