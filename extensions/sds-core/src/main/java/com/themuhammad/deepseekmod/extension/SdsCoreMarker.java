package com.themuhammad.deepseekmod.extension;

/**
 * The marker of the DEX extension.
 *
 * It carries the version of the extension and the target it was built for, so
 * the console and the verification can show what is running.
 */
public final class SdsCoreMarker {
    public static final String VERSION = "1.0.0";
    public static final String TARGET_PACKAGE = "com.deepseek.chat";
    public static final String TARGET_VERSION = "2.7.4";

    private SdsCoreMarker() {
    }
}
