package com.themuhammad.deepseekmod.extension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A native user interface that the assistant asked for.
 *
 * The spec is a small, strictly validated description. It is rendered with
 * plain Android views in the console, so no web content, no script and no
 * remote resource is ever involved. The assistant can only choose from a
 * fixed set of fields, and every field is bounded.
 */
public final class SdsUiSpec {
    public static final int MAX_TITLE_LENGTH = 80;
    public static final int MAX_SUBTITLE_LENGTH = 160;
    public static final int MAX_BODY_CHARS = 8 * 1024;
    public static final int MAX_ITEMS = 20;
    public static final int MAX_ITEM_CHARS = 400;
    public static final int MAX_CODE_CHARS = 16 * 1024;
    public static final int MAX_ACTION_LENGTH = 40;
    public static final int MAX_ARGUMENT_CHARS = 200;

    private final String title;
    private final String subtitle;
    private final String body;
    private final List<String> items;
    private final String code;
    private final String language;
    private final String action;
    private final String argument;

    public SdsUiSpec(String title, String subtitle, String body, List<String> items,
                      String code, String language, String action, String argument) {
        this.title = clamp(title, MAX_TITLE_LENGTH);
        this.subtitle = clamp(subtitle, MAX_SUBTITLE_LENGTH);
        this.body = clamp(body, MAX_BODY_CHARS);
        this.code = clamp(code, MAX_CODE_CHARS);
        this.language = clamp(language, 24);
        this.action = clamp(action, MAX_ACTION_LENGTH);
        this.argument = clamp(argument, MAX_ARGUMENT_CHARS);

        List<String> bounded = new ArrayList<>();
        if (items != null) {
            for (String item : items) {
                if (bounded.size() >= MAX_ITEMS) break;
                String value = clamp(item, MAX_ITEM_CHARS);
                if (!value.isEmpty()) bounded.add(value);
            }
        }
        this.items = Collections.unmodifiableList(bounded);
    }

    /**
     * Builds a spec from the flat tool arguments.
     *
     * The items are separated by a line break, because the strict argument
     * parser only accepts flat strings.
     */
    public static SdsUiSpec fromArguments(String title, String subtitle, String body, String items,
                                           String code, String language, String action, String argument) {
        List<String> list = new ArrayList<>();
        if (items != null && !items.trim().isEmpty()) {
            for (String line : items.split("\\r?\\n")) {
                String value = line.trim();
                if (!value.isEmpty()) list.add(value);
            }
        }
        return new SdsUiSpec(title, subtitle, body, list, code, language, action, argument);
    }

    public String getTitle() { return title; }
    public String getSubtitle() { return subtitle; }
    public String getBody() { return body; }
    public List<String> getItems() { return items; }
    public String getCode() { return code; }
    public String getLanguage() { return language; }
    public String getAction() { return action; }
    public String getArgument() { return argument; }

    /** True when the spec has nothing to show. */
    public boolean isEmpty() {
        return title.isEmpty() && subtitle.isEmpty() && body.isEmpty() && items.isEmpty()
                && code.isEmpty();
    }

    /** True when the spec carries an action the user can trigger. */
    public boolean hasAction() {
        return !action.isEmpty();
    }

    /** A short description for the console list and for the prompt block. */
    public String describe() {
        StringBuilder builder = new StringBuilder();
        if (!title.isEmpty()) builder.append(title);
        if (!subtitle.isEmpty()) builder.append(builder.length() > 0 ? " - " : "").append(subtitle);
        if (builder.length() == 0 && !body.isEmpty()) {
            builder.append(body.length() > 40 ? body.substring(0, 40) : body);
        }
        if (builder.length() == 0) builder.append("(empty interface)");
        return builder.toString();
    }

    private static String clamp(String value, int maxChars) {
        if (value == null) return "";
        String trimmed = value.trim();
        if (trimmed.length() > maxChars) trimmed = trimmed.substring(0, maxChars);
        return trimmed;
    }
}
