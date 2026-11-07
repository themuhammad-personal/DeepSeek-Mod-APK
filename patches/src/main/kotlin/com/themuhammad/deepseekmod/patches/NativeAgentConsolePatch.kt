/*
 * This patch follows the structure of the Morphe patch template:
 * https://github.com/MorpheApp/morphe-patches-template
 *
 * The Morphe patcher is used under its own licence. This project is not a
 * part of the Morphe project and does not use its name.
 */

package com.themuhammad.deepseekmod.patches

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.patch
import app.morphe.patcher.patch.resourceXml

private const val SDS_AGENT_CONSOLE_ACTIVITY =
    "com.themuhammad.deepseekmod.extension.SdsAgentConsoleActivity"

@Suppress("unused")
val nativeAgentConsolePatch = patch(
    name = "Super DeepSeek native Agent Console",
    description = "Adds an Android-native screen to enable proposal mode and review or dismiss parsed tool proposals. It is not exported and it is opened from the entry in the chat screen. It does not execute tools.",
    default = false
) {
    compatibleWith(DEEPSEEK_2_7_4, DEEPSEEK_2_7_4_VERSION_CODE_294, DEEPSEEK_2_7_5_VERSION_CODE_296)
    dependsOn(nativeAgentHooksPatch)

    execute {
        // The runtime prepares itself when the chat screen starts, so the
        // switches of the user are restored before the first prompt is sent.
        val onCreate = MainActivityOnCreateFingerprint.method
        val returnIndex = resolveReturnIndex(onCreate)
        onCreate.addInstructions(
            returnIndex,
            """
                invoke-static { p0 }, Lcom/themuhammad/deepseekmod/extension/SdsAppContext;->initialize(Landroid/content/Context;)V
            """
        )

        resourceXml("AndroidManifest.xml").use { manifest ->
            val application = manifest.elements("application").firstOrNull()
                ?: error("DeepSeek AndroidManifest.xml has no application element")
            val alreadyDeclared = application.elements("activity").any {
                it.attribute("android:name") == SDS_AGENT_CONSOLE_ACTIVITY
            }
            if (!alreadyDeclared) {
                // The console is not exported and it has no launcher entry: only the
                // app itself may open it, through the entry in the chat screen.
                // The console shows the memories, the artifacts and the list of the
                // MCP servers, so it must not be reachable from another app.
                application.appendChild("activity") {
                    setAttribute("android:name", SDS_AGENT_CONSOLE_ACTIVITY)
                    setAttribute("android:label", "Super DeepSeek Agent")
                    setAttribute("android:exported", "false")
                    setAttribute("android:launchMode", "singleTop")
                    setAttribute("android:theme", "@android:style/Theme.Material.NoActionBar")
                }
            }
        }
    }
}
