package com.themuhammad.deepseekmod.patches

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

/**
 * The fingerprint set of the DeepSeek 2.7.5 build with version code 296.
 *
 * This is the third inspected build. It is the newest release that the owner
 * has on his device, it is distributed as a split package, it needs Android 12L
 * or newer and it is obfuscated on its own, so every class name of the builds
 * with the version codes 293 and 294 changed.
 *
 * The three seams below have the same shape as the seams of the builds with the
 * version codes 293 and 294. Every name in this file was read out of the dex
 * files of this build and belongs to this build only.
 *
 *  - Lf02;->b serializes the request and writes the prompt field Lh02;->c
 *    with La83;->x, which is the JSON writer of this build,
 *  - Ly64;->l is the SSE path that replaces the content and updates the
 *    content holder Llia; with Llia;->j,
 *  - Ly64;->m is the SSE path that appends to the content and updates the
 *    content holder Llia; with Llia;->m.
 */

/** Prompt field on the inspected DeepSeek 2.7.5 request serializer of version code 296. */
internal object ChatPromptSerializerFingerprint296 : Fingerprint(
    definingClass = "Lf02;",
    name = "b",
    returnType = "V",
    parameters = listOf("Lac4;", "Ljava/lang/Object;"),
    filters = listOf(
        fieldAccess(
            definingClass = "Lh02;",
            name = "c",
            type = "Ljava/lang/String;",
            opcode = Opcode.IGET_OBJECT
        ),
        methodCall(
            definingClass = "La83;",
            name = "x",
            parameters = listOf("Lvv9;", "I", "Ljava/lang/String;"),
            returnType = "V",
            location = MatchAfterImmediately()
        )
    )
)

/**
 * The SSE JSON-patch path of the build with version code 296 resolves
 * ResponseFragment.content to a mutable field and updates its state holder
 * with Llia;->j.
 */
internal object ChatResponseContentSetFingerprint296 : Fingerprint(
    definingClass = "Ly64;",
    name = "l",
    returnType = "V",
    parameters = listOf("Ljava/lang/String;", "Lx46;", "Lzw1;"),
    filters = listOf(
        string("content"),
        methodCall(
            definingClass = "Llia;",
            name = "j",
            parameters = listOf("Ljava/lang/Object;"),
            returnType = "V"
        )
    )
)

/**
 * SSE APPEND of the build with version code 296 decodes the incoming chunk,
 * joins it to the current content and updates the holder with Llia;->m(). The
 * second value argument is the full String.
 *
 * The inspected code ignores the boolean result of the content update, so
 * there is no move-result instruction after the call.
 */
internal object ChatResponseContentAppendFingerprint296 : Fingerprint(
    definingClass = "Ly64;",
    name = "m",
    returnType = "V",
    parameters = listOf("Lx46;", "Ljava/lang/String;"),
    filters = listOf(
        string("content"),
        methodCall(
            definingClass = "Llia;",
            name = "m",
            parameters = listOf("Ljava/lang/Object;", "Ljava/lang/Object;"),
            returnType = "Z"
        )
    )
)

/** The profile of the inspected DeepSeek 2.7.5 build with version code 296. */
internal val deepSeekTargetProfile296 = DeepSeekTargetProfile(
    versionCode = 296,
    label = "DeepSeek 2.7.5, version code 296",
    promptSerializer = ChatPromptSerializerFingerprint296,
    responseContentSet = ChatResponseContentSetFingerprint296,
    responseContentAppend = ChatResponseContentAppendFingerprint296,
    promptFieldClass = "Lh02;",
    promptFieldName = "c",
    promptFieldType = "Ljava/lang/String;",
    promptSerializerClass = "La83;",
    promptSerializerName = "x",
    contentHolderClass = "Llia;"
)
