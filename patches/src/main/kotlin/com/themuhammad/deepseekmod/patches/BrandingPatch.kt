package com.themuhammad.deepseekmod.patches

import app.morphe.patcher.patch.patch

/**
 * Optional proof that this patch set can modify an Android resource.
 * Changes only the launcher label; package identity and sign-in remain untouched.
 */
@Suppress("unused")
internal val superDeepSeekLauncherLabelPatch = patch(
    name = "Super DeepSeek launcher label",
    description = "Changes the launcher label from DeepSeek to Super DeepSeek.",
    default = false
) {
    compatibleWith(DEEPSEEK_2_7_4)
    dependsOn(compatibilityGatePatch)

    execute {
        resourceXml("res/values/strings.xml").use { strings ->
            val appName = strings.resource("app_name")
            check(appName.text == "DeepSeek") {
                "Unexpected launcher label; refusing to overwrite an unknown resource."
            }
            appName.text = "Super DeepSeek"
        }
    }
}
