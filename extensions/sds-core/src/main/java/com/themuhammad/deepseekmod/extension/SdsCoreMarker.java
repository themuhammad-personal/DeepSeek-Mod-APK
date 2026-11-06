package com.themuhammad.deepseekmod.extension;

/**
 * Minimal DEX-extension marker. Intentionally inert until native feature modules
 * have their own API contracts, tests and narrowly scoped patch call sites.
 */
public final class SdsCoreMarker {
    public static final String VERSION = "0.1.0";
    public static final String TARGET_PACKAGE = "com.deepseek.chat";
    public static final String TARGET_VERSION = "2.7.4";

    private SdsCoreMarker() {
    }
}
