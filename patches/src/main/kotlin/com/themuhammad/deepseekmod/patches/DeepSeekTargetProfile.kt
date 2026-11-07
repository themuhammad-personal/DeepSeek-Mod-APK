package com.themuhammad.deepseekmod.patches

import app.morphe.patcher.Fingerprint

/**
 * The parts of a DeepSeek build that the native agent hooks depend on.
 *
 * Every inspected build has its own set of fingerprints and its own obfuscated
 * class names, because every release of the app is compiled and obfuscated on
 * its own. A profile therefore belongs to exactly one version code and is only
 * used for that version code, so a fingerprint that was derived from one build
 * can never be applied to another build.
 *
 * The profile also carries the class names that the instruction level checks
 * need, so that the checks read the same build that the fingerprints were
 * taken from.
 */
internal data class DeepSeekTargetProfile(
    /** The version code the profile was derived from. */
    val versionCode: Int,
    /** A readable label of the inspected build. */
    val label: String,
    /** The fingerprint of the method that serializes the prompt of a request. */
    val promptSerializer: Fingerprint,
    /** The fingerprint of the streaming method that replaces the content. */
    val responseContentSet: Fingerprint,
    /** The fingerprint of the streaming method that appends to the content. */
    val responseContentAppend: Fingerprint,
    /** The class that holds the prompt of a request. */
    val promptFieldClass: String,
    /** The name of the prompt field. */
    val promptFieldName: String,
    /** The type of the prompt field. */
    val promptFieldType: String,
    /** The class that writes the string fields of a request. */
    val promptSerializerClass: String,
    /** The name of the method that writes a string field. */
    val promptSerializerName: String,
    /** The class that holds the streamed content of an answer. */
    val contentHolderClass: String
)

/**
 * Every build of the official app that was inspected for this project.
 *
 * A build that is not listed here has no fingerprint set, so the patches fail
 * with a readable message instead of patching an unknown build.
 */
internal val deepSeekTargetProfiles: List<DeepSeekTargetProfile> = listOf(
    deepSeekTargetProfile293,
    deepSeekTargetProfile294
)

/**
 * Returns the profile of the build that is being patched.
 *
 * The version code is read from the APK that is being patched, so the profile
 * always belongs to the build in the hands and never to a build that was only
 * inspected on paper.
 */
internal fun deepSeekTargetProfile(versionCode: String): DeepSeekTargetProfile {
    val code = versionCode.trim().toIntOrNull()
        ?: error(
            "The version code of the DeepSeek APK is '$versionCode', which is not a number. " +
                "The fingerprints of this project are only known for " +
                deepSeekTargetProfiles.joinToString(", ") { "${it.versionCode}" } + "."
        )
    return deepSeekTargetProfiles.firstOrNull { it.versionCode == code }
        ?: error(
            "DeepSeek version code $code was not inspected. " +
                "The fingerprints of this project were derived for " +
                deepSeekTargetProfiles.joinToString(", ") {
                    "${it.versionCode} (${it.label})"
                } + ". Nothing was patched."
        )
}
