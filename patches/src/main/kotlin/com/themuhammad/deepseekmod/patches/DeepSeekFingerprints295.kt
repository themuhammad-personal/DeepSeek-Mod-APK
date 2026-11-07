/*
 * This patch follows the structure of the Morphe patch template:
 * https://github.com/MorpheApp/morphe-patches-template
 *
 * The Morphe patcher is used under its own licence. This project is not a
 * part of the Morphe project and does not use its name.
 */

package com.themuhammad.deepseekmod.patches

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

/**
 * The fingerprint set of the DeepSeek 2.7.5 build with version code 295.
 *
 * This is the build that the official download address of DeepSeek serves
 * right now. It is the universal APK of the app, it runs on Android 7 and it
 * is obfuscated on its own, so every class name of the builds with the
 * version codes 293, 294 and 296 changed.
 *
 * The names in this file were not guessed. They were found by
 * `scripts/find_build_twin.py`, which searches for the methods that have the
 * same instruction shape as the seams of the build with the version code 296
 * and reports the names that the fingerprint has to use. The script reproduces
 * the fingerprint sets of the builds with the version codes 293, 294 and 296
 * exactly, so the result was checked against the sets that were derived by
 * hand.
 *
 *  - Loy1;->e serializes the request and writes the prompt field Lqy1;->c
 *    with Ld63;->x, which is the JSON writer of this build,
 *  - Ly44;->l is the SSE path that replaces the content and updates the
 *    content holder Llea; with Llea;->j,
 *  - Ly44;->m is the SSE path that appends to the content and updates the
 *    content holder Llea; with Llea;->m.
 */

/** Prompt field on the inspected DeepSeek 2.7.5 request serializer of version code 295. */
internal object ChatPromptSerializerFingerprint295 : Fingerprint(
    definingClass = "Loy1;",
    name = "e",
    returnType = "V",
    parameters = listOf("Laa4;", "Ljava/lang/Object;"),
    filters = listOf(
        fieldAccess(
            definingClass = "Lqy1;",
            name = "c",
            type = "Ljava/lang/String;",
            opcode = Opcode.IGET_OBJECT
        ),
        methodCall(
            definingClass = "Ld63;",
            name = "x",
            parameters = listOf("Lyr9;", "I", "Ljava/lang/String;"),
            returnType = "V",
            location = MatchAfterImmediately()
        )
    )
)

/**
 * The SSE JSON-patch path of the build with version code 295 resolves
 * ResponseFragment.content to a mutable field and updates its state holder
 * with Llea;->j.
 */
internal object ChatResponseContentSetFingerprint295 : Fingerprint(
    definingClass = "Ly44;",
    name = "l",
    returnType = "V",
    parameters = listOf("Ljava/lang/String;", "Lh26;", "Liv1;"),
    filters = listOf(
        string("content"),
        methodCall(
            definingClass = "Llea;",
            name = "j",
            parameters = listOf("Ljava/lang/Object;"),
            returnType = "V"
        )
    )
)

/**
 * SSE APPEND of the build with version code 295 decodes the incoming chunk,
 * joins it to the current content and updates the holder with Llea;->m(). The
 * second value argument is the full String.
 *
 * The inspected code ignores the boolean result of the content update, so
 * there is no move-result instruction after the call.
 */
internal object ChatResponseContentAppendFingerprint295 : Fingerprint(
    definingClass = "Ly44;",
    name = "m",
    returnType = "V",
    parameters = listOf("Lh26;", "Ljava/lang/String;"),
    filters = listOf(
        string("content"),
        methodCall(
            definingClass = "Llea;",
            name = "m",
            parameters = listOf("Ljava/lang/Object;", "Ljava/lang/Object;"),
            returnType = "Z"
        )
    )
)

/** The profile of the inspected DeepSeek 2.7.5 build with version code 295. */
internal val deepSeekTargetProfile295 = DeepSeekTargetProfile(
    versionCode = 295,
    label = "DeepSeek 2.7.5, version code 295",
    promptSerializer = ChatPromptSerializerFingerprint295,
    responseContentSet = ChatResponseContentSetFingerprint295,
    responseContentAppend = ChatResponseContentAppendFingerprint295,
    promptFieldClass = "Lqy1;",
    promptFieldName = "c",
    promptFieldType = "Ljava/lang/String;",
    promptSerializerClass = "Ld63;",
    promptSerializerName = "x",
    contentHolderClass = "Llea;"
)
