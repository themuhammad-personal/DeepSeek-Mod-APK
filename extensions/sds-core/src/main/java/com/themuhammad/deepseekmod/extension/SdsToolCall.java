package com.themuhammad.deepseekmod.extension;

/**
 * Immutable, untrusted tool request parsed from model output.
 * Arguments remain raw JSON until a registered tool validates its schema.
 */
public final class SdsToolCall {
    private static final int MAX_ID_LENGTH = 128;
    private static final int MAX_NAME_LENGTH = 96;
    private static final int MAX_ARGUMENTS_LENGTH = 64 * 1024;

    private final String id;
    private final String name;
    private final String argumentsJson;

    public SdsToolCall(String id, String name, String argumentsJson) {
        this.id = requireText(id, "id", MAX_ID_LENGTH);
        this.name = requireText(name, "name", MAX_NAME_LENGTH);
        this.argumentsJson = argumentsJson == null ? "{}" : argumentsJson;
        if (this.argumentsJson.length() > MAX_ARGUMENTS_LENGTH) {
            throw new IllegalArgumentException("tool arguments exceed the size limit");
        }
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getArgumentsJson() { return argumentsJson; }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(field + " is too long");
        }
        return trimmed;
    }
}
