package com.themuhammad.deepseekmod.patches

import app.morphe.patcher.Fingerprint

/**
 * The launcher Activity of the official app.
 *
 * Unlike the chat classes, this class and this method keep their original
 * names, so the fingerprint does not depend on the obfuscation mapping of the
 * build. It is therefore shared by every inspected build.
 */
internal object MainActivityOnCreateFingerprint : Fingerprint(
    definingClass = "Lcom/deepseek/chat/MainActivity;",
    name = "onCreate",
    returnType = "V",
    parameters = listOf("Landroid/os/Bundle;")
)
