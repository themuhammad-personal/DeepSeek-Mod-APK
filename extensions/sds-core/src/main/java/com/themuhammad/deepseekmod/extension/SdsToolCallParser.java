package com.themuhammad.deepseekmod.extension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the deliberately small, explicit SDS tool-call envelope.
 * This class only parses untrusted model text; it never executes a tool.
 */
public final class SdsToolCallParser {
    public static final int MAX_RESPONSE_CHARS = 256 * 1024;
    public static final int MAX_CALLS_PER_RESPONSE = 8;
    private static final int MAX_ARGUMENTS_CHARS = 64 * 1024;

    private static final Pattern CALL = Pattern.compile(
            "<SDS:TOOL_CALL\\s+id=\"([A-Za-z0-9._:-]{1,128})\"\\s+name=\"([A-Za-z][A-Za-z0-9_.-]{0,63})\">(.*?)</SDS:TOOL_CALL>",
            Pattern.DOTALL);

    private SdsToolCallParser() { }

    /**
     * True when the arguments are exactly one JSON object.
     *
     * The strict reader of the runtime does the work, so there is only one
     * JSON grammar in the project.
     */
    private static boolean isJsonObject(String arguments) {
        try {
            SdsJson.parseObject(arguments);
            return true;
        } catch (IllegalArgumentException refused) {
            return false;
        }
    }

    public static List<SdsToolCall> parse(String response) {
        if (response == null || response.length() > MAX_RESPONSE_CHARS) {
            return Collections.emptyList();
        }

        List<SdsToolCall> calls = new ArrayList<>();
        Matcher matcher = CALL.matcher(response);
        while (matcher.find() && calls.size() < MAX_CALLS_PER_RESPONSE) {
            String id = matcher.group(1);
            String name = matcher.group(2);
            String arguments = matcher.group(3) == null ? "{}" : matcher.group(3).trim();
            if (arguments.length() > MAX_ARGUMENTS_CHARS || !isJsonObject(arguments)) {
                continue;
            }
            try {
                calls.add(new SdsToolCall(id, name, arguments));
            } catch (IllegalArgumentException ignored) {
                // Malformed model output is ignored, not promoted to executable state.
            }
        }
        return Collections.unmodifiableList(calls);
    }

}
