package com.themuhammad.deepseekmod.extension;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * The persisted settings of the native runtime.
 *
 * The switches of the console used to live only in the memory of the process,
 * so every switch had to be turned on again after the app was closed. They are
 * stored in the app-private preferences of the app, which are removed with the
 * app and are not reachable by another app.
 *
 * The stored values are only the switches of the user. Nothing about a
 * conversation, a proposal or a tool result is stored here.
 */
public final class SdsSettings {
    private static final String FILE = "sds-settings";
    private static final String KEY_AGENT = "agent-enabled";
    private static final String KEY_HANDOFF = "handoff-enabled";
    private static final String KEY_NETWORK = "network-allowed";
    private static final String KEY_MEMORY = "memory-in-prompt";
    private static final String KEY_SANDBOX = "sandbox-enabled";

    private static volatile SharedPreferences preferences;

    private SdsSettings() {
    }

    /**
     * Attaches the preferences of the app. Safe to call more than once.
     */
    public static synchronized void attach(Context context) {
        if (context == null || preferences != null) return;
        try {
            Context application = context.getApplicationContext();
            Context target = application == null ? context : application;
            preferences = target.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        } catch (Throwable ignored) {
            // The runtime keeps the values of the process when the preferences are not usable.
            preferences = null;
        }
    }

    /** True when the proposal mode was left on by the user. */
    public static boolean isAgentEnabled() {
        return get(KEY_AGENT, false);
    }

    public static void setAgentEnabled(boolean value) {
        put(KEY_AGENT, value);
    }

    /** True when the result of a tool may be put back into the conversation. */
    public static boolean isHandoffEnabled() {
        return get(KEY_HANDOFF, false);
    }

    public static void setHandoffEnabled(boolean value) {
        put(KEY_HANDOFF, value);
    }

    /** True when the user allowed the agent to use the network. */
    public static boolean isNetworkAllowed() {
        return get(KEY_NETWORK, false);
    }

    public static void setNetworkAllowed(boolean value) {
        put(KEY_NETWORK, value);
    }

    /** True when the stored memories are added to the prompt. */
    public static boolean isMemoryInPrompt() {
        return get(KEY_MEMORY, false);
    }

    public static void setMemoryInPrompt(boolean value) {
        put(KEY_MEMORY, value);
    }

    /** True when the sandbox command is offered to the agent. */
    public static boolean isSandboxEnabled() {
        return get(KEY_SANDBOX, false);
    }

    public static void setSandboxEnabled(boolean value) {
        put(KEY_SANDBOX, value);
    }

    private static boolean get(String key, boolean fallback) {
        SharedPreferences store = preferences;
        if (store == null) return fallback;
        try {
            return store.getBoolean(key, fallback);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static void put(String key, boolean value) {
        SharedPreferences store = preferences;
        if (store == null) return;
        try {
            store.edit().putBoolean(key, value).apply();
        } catch (Throwable ignored) {
            // The value stays as it is when the preferences cannot be written.
        }
    }
}
