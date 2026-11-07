package com.themuhammad.deepseekmod.patches

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.patch

/**
 * Adds a native Agent Console entry to the official chat screen.
 *
 * The entry is a plain Android view that is added to the activity window after
 * the original `MainActivity.onCreate` finished. It does not touch the Compose
 * tree, the message renderer or the composer, and it is a separate patch so it
 * can be enabled or disabled on its own.
 *
 * The patch is default-off: it only runs when it is explicitly enabled.
 */
@Suppress("unused")
val nativeAgentChatEntryPatch = patch(
    name = "Super DeepSeek native Agent chat entry",
    description = "Adds a small native entry to the official chat screen that opens the native Agent Console.",
    default = false
) {
    compatibleWith(DEEPSEEK_2_7_4, DEEPSEEK_2_7_4_VERSION_CODE_294)
    dependsOn(nativeAgentConsolePatch)

    execute {
        val onCreate = MainActivityOnCreateFingerprint.method
        val returnIndex = resolveReturnIndex(onCreate)

        onCreate.addInstructions(
            returnIndex,
            """
                invoke-static { p0 }, Lcom/themuhammad/deepseekmod/extension/SdsChatEntry;->attach(Landroid/app/Activity;)V
            """
        )
    }
}
