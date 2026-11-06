package com.themuhammad.deepseekmod.patches

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.SupportedAbi

/**
 * First statically inspected target.
 * Do not widen this to any version until fingerprints and patched APK tests pass.
 */
internal val DEEPSEEK_2_7_4 = Compatibility(
    name = "DeepSeek",
    packageName = "com.deepseek.chat",
    apkFileType = ApkFileType.APK,
    appIconColor = 0x1E3A5F,
    targets = listOf(
        AppTarget(
            version = "2.7.4",
            versionCodes = mapOf(SupportedAbi.ARM64_V8A to 293)
        )
    )
)
