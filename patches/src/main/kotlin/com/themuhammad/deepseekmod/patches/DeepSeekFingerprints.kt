package com.themuhammad.deepseekmod.patches

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import com.android.tools.smali.dexlib2.Opcode

/**
 * Version-scoped fingerprints based on the uploaded apktool 3.0.3 decode.
 * The field/call adjacency is checked so a name/descriptor match alone is not enough.
 */
internal object ChatPromptSerializerFingerprint : Fingerprint(
    definingClass = "Ljy1;",
    name = "e",
    returnType = "V",
    parameters = listOf("Lw94;", "Ljava/lang/Object;"),
    filters = listOf(
        fieldAccess(smali = "Lly1;->c:Ljava/lang/String;"),
        methodCall(
            smali = "Ly53;->x(Lsr9;ILjava/lang/String;)V",
            location = MatchAfterImmediately()
        )
    )
)

internal object ChatResponseContentFingerprint : Fingerprint(
    definingClass = "Lefa;",
    name = "d",
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Lrn3;"),
    filters = listOf(
        literal(2),
        // The value 2 is the response-content field index. The branch instruction
        // between this literal and the read is intentional, so do not require adjacency.
        methodCall(smali = "Lx53;->m(Lsr9;I)Ljava/lang/String;"),
        opcode(Opcode.MOVE_RESULT_OBJECT, location = MatchAfterImmediately())
    )
)
