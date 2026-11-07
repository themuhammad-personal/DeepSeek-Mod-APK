package com.themuhammad.deepseekmod.patches

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.SupportedAbi

/**
 * The official DeepSeek 2.7.4 build with version code 293.
 *
 * This is the build that the project inspected first and the build that the
 * patches were applied to in the verification workflow. Its fingerprint set is
 * `DeepSeekFingerprints293.kt` and it is never used for another build.
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

/**
 * The DeepSeek 2.7.4 build with version code 294.
 *
 * The same app version, but a different build: it is distributed as a split
 * package, it needs Android 10 or newer and it is obfuscated on its own, so
 * every class name of the build with version code 293 changed. Its fingerprint
 * set is `DeepSeekFingerprints294.kt`.
 *
 * The target is experimental, because the fingerprints of this build were read
 * out of its dex files and the patches were applied to it in the verification
 * workflow, but it has not been run on a device yet.
 */
internal val DEEPSEEK_2_7_4_VERSION_CODE_294 = Compatibility(
    name = "DeepSeek",
    packageName = "com.deepseek.chat",
    apkFileType = ApkFileType.APK,
    appIconColor = 0x1E3A5F,
    targets = listOf(
        AppTarget(
            version = "2.7.4",
            versionCodes = mapOf(SupportedAbi.ARM64_V8A to 294),
            isExperimental = true,
            minSdk = 29,
            description = "The build with version code 294 has its own fingerprint set. " +
                "The patches were not applied to it and they were not run on a device yet."
        )
    )
)

/**
 * The DeepSeek 2.7.5 build with version code 296.
 *
 * The newest release that was inspected. It is distributed as a split package,
 * it needs Android 12L or newer and it is obfuscated on its own, so every
 * class name of the builds with the version codes 293 and 294 changed. Its
 * fingerprint set is `DeepSeekFingerprints296.kt`.
 *
 * The target is experimental, because the fingerprints of this build were read
 * out of its dex files and the patches were applied to it in the verification
 * workflow, but it has not been run on a device yet.
 */
internal val DEEPSEEK_2_7_5_VERSION_CODE_296 = Compatibility(
    name = "DeepSeek",
    packageName = "com.deepseek.chat",
    apkFileType = ApkFileType.APK,
    appIconColor = 0x1E3A5F,
    targets = listOf(
        AppTarget(
            version = "2.7.5",
            versionCodes = mapOf(SupportedAbi.ARM64_V8A to 296),
            isExperimental = true,
            minSdk = 32,
            description = "The build with version code 296 has its own fingerprint set. " +
                "The patches were applied to it in the verification workflow, but it was " +
                "not run on a device yet."
        )
    )
)
