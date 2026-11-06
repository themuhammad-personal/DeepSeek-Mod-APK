package com.themuhammad.deepseekmod.patches

import app.morphe.patcher.patch.patch
import app.morphe.patcher.patch.resourceXml

private const val SDS_AGENT_CONSOLE_ACTIVITY =
    "com.themuhammad.deepseekmod.extension.SdsAgentConsoleActivity"

@Suppress("unused")
val nativeAgentConsolePatch = patch(
    name = "Super DeepSeek native Agent Console",
    description = "Adds a separate Android-native screen to enable proposal mode and review or dismiss parsed tool proposals. It does not execute tools.",
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
                application.appendChild("activity") {
                    setAttribute("android:name", SDS_AGENT_CONSOLE_ACTIVITY)
                    setAttribute("android:label", "Super DeepSeek Agent")
                    setAttribute("android:exported", "true")
                    setAttribute("android:launchMode", "singleTop")
                    setAttribute("android:theme", "@android:style/Theme.Material.NoActionBar")
                    appendChild("intent-filter") {
                        appendChild("action") {
                            setAttribute("android:name", "android.intent.action.MAIN")
                        }
                        appendChild("category") {
                            setAttribute("android:name", "android.intent.category.LAUNCHER")
                        }
                    }
                }
            }
        }
    }
}
