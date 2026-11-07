package com.themuhammad.deepseekmod.extension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The native slash commands.
 *
 * A slash command is a native word that the user types at the start of a chat
 * message. The request hook expands it before the prompt is serialised, so the
 * model receives a plain instruction and the app keeps the behaviour.
 *
 * An unknown command is left untouched: the app never silently drops what the
 * user typed.
 */
public final class SdsSlashCommands {
    /** Expands a command into the text that replaces it. */
    public interface Expander {
        String expand(String arguments);
    }

    private static final int MAX_COMMANDS = 32;
    private static final int MAX_ARGUMENTS = 4 * 1024;

    private static final Map<String, Command> COMMANDS = new LinkedHashMap<>();

    private SdsSlashCommands() {
    }

    /** Registers a command. A duplicate name is refused. */
    public static synchronized void register(String name, String description, Expander expander) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("the command name is required");
        }
        if (expander == null) {
            throw new IllegalArgumentException("the command expander is required");
        }
        String key = name.trim().toLowerCase(java.util.Locale.US);
        if (!key.matches("[/][a-z][a-z0-9-]{0,31}")) {
            throw new IllegalArgumentException("invalid command name: " + name);
        }
        if (COMMANDS.containsKey(key)) {
            throw new IllegalArgumentException("the command is already registered: " + key);
        }
        if (COMMANDS.size() >= MAX_COMMANDS) {
            throw new IllegalArgumentException("too many slash commands");
        }
        COMMANDS.put(key, new Command(key, description == null ? "" : description, expander));
    }

    /** Removes every registered command. Used by the tests. */
    public static synchronized void clear() {
        COMMANDS.clear();
    }

    public static synchronized List<String> names() {
        return Collections.unmodifiableList(new ArrayList<>(COMMANDS.keySet()));
    }

    public static synchronized List<String> describe() {
        List<String> lines = new ArrayList<>();
        for (Command command : COMMANDS.values()) {
            lines.add(command.getName() + " - " + command.getDescription());
        }
        return lines;
    }

    /** True when the prompt starts with a slash command. */
    public static boolean isCommand(String prompt) {
        if (prompt == null) return false;
        String trimmed = prompt.trim();
        return trimmed.startsWith("/") && trimmed.length() > 1;
    }

    /**
     * Expands a slash command.
     *
     * @return the expanded text, or the untouched prompt when it is not a known
     *         slash command.
     */
    public static String expand(String prompt) {
        if (prompt == null) return null;
        String trimmed = prompt.trim();
        if (!isCommand(trimmed)) return prompt;
        int space = trimmed.indexOf(' ');
        String name = (space < 0 ? trimmed : trimmed.substring(0, space)).toLowerCase(java.util.Locale.US);
        String arguments = space < 0 ? "" : trimmed.substring(space + 1).trim();
        if (arguments.length() > MAX_ARGUMENTS) {
            arguments = arguments.substring(0, MAX_ARGUMENTS);
        }
        Command command;
        synchronized (SdsSlashCommands.class) {
            command = COMMANDS.get(name);
        }
        if (command == null) return prompt;
        String expanded = command.getExpander().expand(arguments);
        return expanded == null || expanded.isEmpty() ? prompt : expanded;
    }

    /** One registered command. */
    public static final class Command {
        private final String name;
        private final String description;
        private final Expander expander;

        Command(String name, String description, Expander expander) {
            this.name = name;
            this.description = description;
            this.expander = expander;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
        public Expander getExpander() { return expander; }
    }
}
