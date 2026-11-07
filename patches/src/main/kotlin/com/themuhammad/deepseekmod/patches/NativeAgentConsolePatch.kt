package com.themuhammad.deepseekmod.patches

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
    compatibleWith(DEEPSEEK_2_7_4)
    dependsOn(nativeAgentHooksPatch)

    execute {
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
