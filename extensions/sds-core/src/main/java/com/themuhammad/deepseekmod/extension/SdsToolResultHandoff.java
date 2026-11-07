package com.themuhammad.deepseekmod.extension;

/**
 * The handback of a tool result.
 *
 * The result of an approved tool run can be put back into the conversation, so
 * the model can read what the tool returned. The flow uses the app's own
 * content holder: the value that the app writes there is the value that the
 * chat renders, which is the same seam that the reference proof of concept
 * used for its own answer rewriting.
 *
 * The handback is off by default, because it has not been proven on a device
 * yet. It is also idempotent: the marker is checked before anything is added,
 * so a repeated callback can never duplicate the result.
 */
public final class SdsToolResultHandoff {
    public static final String MARKER = "[SDS:TOOL_RESULT]";
    public static final int MAX_RESULT_CHARS = 4 * 1024;

    private static volatile boolean enabled;
    private static volatile String pending;

    private SdsToolResultHandoff() {
    }

    /** True when the result may be put back into the conversation. */
    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        if (!value) pending = null;
    }

    /** Remembers the result of an approved run. */
    public static void setPendingResult(String result) {
        if (result == null || result.trim().isEmpty()) {
            pending = null;
            return;
        }
        String bounded = result.length() > MAX_RESULT_CHARS
                ? result.substring(0, MAX_RESULT_CHARS)
                : result;
        pending = bounded;
    }

    public static void clearPendingResult() {
        pending = null;
    }

    /** True when a result is waiting to be returned. */
    public static boolean hasPendingResult() {
        return pending != null;
    }

    /**
     * Adds the waiting result to the content.
     *
     * @return the content, unchanged when the handback is off, when nothing is
     *         waiting or when the marker is already there
     */
    public static String applyTo(String content) {
        if (content == null) return null;
        if (!enabled) return content;
        String result = pending;
        if (result == null) return content;
        if (content.contains(MARKER)) {
            // The result is already in the conversation, so it is not added twice.
            pending = null;
            return content;
        }
        pending = null;
        return content + "\n\n" + MARKER + "\n" + result;
    }
}
