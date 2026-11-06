package com.themuhammad.deepseekmod.patches

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import com.android.tools.smali.dexlib2.Opcode

/**
 * Version-scoped fingerprints based on the supplied apktool decode.
 * Each filter describes a real instruction/reference, not a textual name guess.
 */
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
 * The response content is read with index 2. A conditional branch occurs between
 * the index literal and the invoke-interface, so that pair is deliberately not
 * constrained to adjacency. The move-result-object must immediately follow m().
 */
internal object ChatResponseContentFingerprint : Fingerprint(
    definingClass = "Lefa;",
    name = "d",
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Lrn3;"),
    filters = listOf(
        literal(2),
        methodCall(
            definingClass = "Lx53;",
            name = "m",
            parameters = listOf("Lsr9;", "I"),
            returnType = "Ljava/lang/String;"
        ),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately())
    )
)
