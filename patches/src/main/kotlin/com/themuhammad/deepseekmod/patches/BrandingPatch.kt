/*
 * This patch follows the structure of the Morphe patch template:
 * https://github.com/MorpheApp/morphe-patches-template
 *
 * The Morphe patcher is used under its own licence. This project is not a
 * part of the Morphe project and does not use its name.
 */

package com.themuhammad.deepseekmod.patches

import app.morphe.patcher.patch.listDecodableFiles
import app.morphe.patcher.patch.patch
import app.morphe.patcher.patch.resourceFile
import app.morphe.patcher.patch.resourceXml

private const val SDS_APP_NAME = "Super DeepSeek"

// Drawable names added by this patch. They carry the sds_ prefix so they can
// never collide with a resource of the official app.
private const val ICON_BACKGROUND = "sds_icon_background"
private const val ICON_FOREGROUND = "sds_icon_foreground"
private const val ICON_MONOCHROME = "sds_icon_monochrome"

private const val ICON_BACKGROUND_XML = """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#FF1E1F23"
        android:pathData="M0,0h108v108h-108z" />
</vector>
"""

// A four-point spark with a small companion spark, inside the 66dp safe zone.
private const val SPARK_PATH =
    "M54,30 C56,46 62,52 78,54 C62,56 56,62 54,78 C52,62 46,56 30,54 C46,52 52,46 54,30 Z " +
        "M76,28 C76.8,33 78.6,34.8 83.6,35.6 C78.6,36.4 76.8,38.2 76,43.2 " +
        "C75.2,38.2 73.4,36.4 68.4,35.6 C73.4,34.8 75.2,33 76,28 Z"

private fun iconLayerXml(color: String) = """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="$color"
        android:pathData="$SPARK_PATH" />
</vector>
"""

private const val ADAPTIVE_ICON_XML = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/$ICON_BACKGROUND" />
    <foreground android:drawable="@drawable/$ICON_FOREGROUND" />
    <monochrome android:drawable="@drawable/$ICON_MONOCHROME" />
</adaptive-icon>
"""

private fun stringResourceName(reference: String): String? =
    if (reference.startsWith("@string/")) reference.removePrefix("@string/") else null

private fun resourceName(reference: String): String? {
    // "@mipmap/ic_launcher" -> "ic_launcher"; framework references are skipped.
    if (!reference.startsWith("@") || reference.contains(':')) return null
    val slash = reference.indexOf('/')
    return if (slash < 0) null else reference.substring(slash + 1)
}

/**
 * Super DeepSeek branding: app name, adaptive launcher icon.
 *
 * Package identity, sign-in and every other resource stay untouched. The icon
 * is a vector adaptive icon written to mipmap-anydpi-v26, which Android prefers
 * over density specific rasters, so no raster has to be edited.
 *
 * The splash screen of Android 12+ shows the launcher icon on the window
 * background, so it follows the new icon. The splash theme of the app is
 * printed to the patch log so a dedicated splash step can be added once the
 * real theme names are known.
 */
@Suppress("unused")
val superDeepSeekBrandingPatch = patch(
    name = "Super DeepSeek branding",
    description = "Sets the app name to Super DeepSeek and replaces the launcher icon with a vector adaptive icon.",
    default = true
) {
    compatibleWith(DEEPSEEK_2_7_5_VERSION_CODE_296)
    dependsOn(compatibilityGatePatch)

    execute {
        val iconNames = linkedSetOf<String>()
        val labelNames = linkedSetOf<String>()
        var literalLabelSet = false

        resourceXml("AndroidManifest.xml").use { manifest ->
            val application = manifest.elements("application").firstOrNull()
                ?: error("DeepSeek AndroidManifest.xml has no application element")

            application.attribute("android:label")?.let { label ->
                val name = stringResourceName(label)
                if (name != null) {
                    labelNames += name
                } else if (!label.startsWith("@")) {
                    application.setAttribute("android:label", SDS_APP_NAME)
                    literalLabelSet = true
                }
            }

            for (attribute in arrayOf("android:icon", "android:roundIcon")) {
                application.attribute(attribute)?.let { resourceName(it) }?.let { iconNames += it }
            }

            arrayOf("activity", "activity-alias").forEach { tag ->
                manifest.elements(tag).forEach { element ->
                    element.attribute("android:label")?.let { stringResourceName(it) }?.let { labelNames += it }
                    element.attribute("android:icon")?.let { resourceName(it) }?.let { iconNames += it }
                    element.attribute("android:roundIcon")?.let { resourceName(it) }?.let { iconNames += it }
                }
            }

            println("Branding: icon resources ${iconNames}, label resources ${labelNames}")
            println("Branding: application theme ${application.attribute("android:theme")}")
        }

        // App name: set the default string, drop localised copies because a
        // launcher resolves the label with the device locale.
        var nameSet = literalLabelSet
        listDecodableFiles("res/values*/strings.xml").forEach { path ->
            resourceXml(path).use { strings ->
                labelNames.forEach { name ->
                    val entry = strings.resourceOrNull(name) ?: return@forEach
                    if (path == "res/values/strings.xml") {
                        entry.text = SDS_APP_NAME
                        nameSet = true
                    } else {
                        entry.remove()
                    }
                }
            }
        }
        check(nameSet) { "No app name resource was found to rename; refusing to continue silently." }

        // New drawables of the icon.
        fun write(path: String, content: String) {
            val file = resourceFile(path)
            file.parentFile.mkdirs()
            file.writeText(content)
        }
        write("res/drawable/$ICON_BACKGROUND.xml", ICON_BACKGROUND_XML)
        write("res/drawable/$ICON_FOREGROUND.xml", iconLayerXml("#FF4D6BFE"))
        write("res/drawable/$ICON_MONOCHROME.xml", iconLayerXml("#FF000000"))

        // Replace every adaptive icon the manifest points to, and always add the
        // anydpi-v26 version, which wins over density rasters (minSdk is 32).
        val icons = if (iconNames.isEmpty()) linkedSetOf("ic_launcher") else iconNames
        var replaced = 0
        icons.forEach { name ->
            listDecodableFiles("res/mipmap*/$name.xml").forEach { path ->
                resourceFile(path, false).writeText(ADAPTIVE_ICON_XML)
                replaced++
            }
            write("res/mipmap-anydpi-v26/$name.xml", ADAPTIVE_ICON_XML)
        }
        println("Branding: replaced $replaced existing adaptive icon file(s); wrote anydpi-v26 icon for ${icons}")
    }
}
