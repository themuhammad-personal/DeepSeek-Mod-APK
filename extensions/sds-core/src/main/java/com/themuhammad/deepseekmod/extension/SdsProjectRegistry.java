package com.themuhammad.deepseekmod.extension;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The native project registry.
 *
 * A project is a named folder inside the app-private workspace. The registry
 * stores the name, the folder and the creation time, so the chat, the file tools
 * and the console can talk about the same project without a document provider
 * and without any access outside the workspace.
 */
public final class SdsProjectRegistry {
    public static final int MAX_PROJECTS = 50;
    public static final int MAX_NAME_LENGTH = 48;

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final String TAB = "\t";

    private final File file;
    private final Map<String, Project> projects = new LinkedHashMap<>();

    public SdsProjectRegistry(File file) {
        if (file == null) throw new IllegalArgumentException("file is required");
        this.file = file;
        load();
    }

    /** Creates a project and returns it. */
    public synchronized Project create(String name) {
        String normalized = normalizeName(name);
        if (projects.containsKey(normalized)) {
            throw new IllegalStateException("the project already exists: " + normalized);
        }
        if (projects.size() >= MAX_PROJECTS) {
            throw new IllegalStateException("the project limit is reached");
        }
        Project project = new Project(normalized, folderFor(normalized), System.currentTimeMillis());
        projects.put(normalized, project);
        save();
        return project;
    }

    /** Removes a project. The files inside it are not touched. */
    public synchronized boolean remove(String name) {
        if (name == null) return false;
        boolean removed = projects.remove(name.trim()) != null;
        if (removed) save();
        return removed;
    }

    public synchronized Project find(String name) {
        if (name == null) return null;
        return projects.get(name.trim());
    }

    public synchronized int size() {
        return projects.size();
    }

    public synchronized List<Project> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(projects.values()));
    }

    /** The projects as a bounded block of text, ready for a prompt. */
    public synchronized String asPromptBlock(int maxChars) {
        StringBuilder builder = new StringBuilder();
        for (Project project : projects.values()) {
            String line = "- " + project.getName() + " (folder: " + project.getFolder() + ")";
            if (builder.length() + line.length() > maxChars) break;
            if (builder.length() > 0) builder.append('\n');
            builder.append(line);
        }
        return builder.toString();
    }

    private static String normalizeName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("the project name is required");
        }
        String trimmed = name.trim();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("the project name is too long");
        }
        for (int index = 0; index < trimmed.length(); index++) {
            char c = trimmed.charAt(index);
            boolean allowed = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == ' ' || c == '-' || c == '_';
            if (!allowed) {
                throw new IllegalArgumentException("the project name has an unsupported character");
            }
        }
        return trimmed;
    }

    private static String folderFor(String name) {
        return "project-" + name.toLowerCase(java.util.Locale.US).replace(' ', '-');
    }

    private void load() {
        if (!file.isFile()) return;
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), UTF8));
            String line;
            while ((line = reader.readLine()) != null && projects.size() < MAX_PROJECTS) {
                String[] parts = line.split("\t", -1);
                if (parts.length < 3) continue;
                long createdAt;
                try {
                    createdAt = Long.parseLong(parts[2]);
                } catch (NumberFormatException ignored) {
                    continue;
                }
                projects.put(parts[0], new Project(parts[0], parts[1], createdAt));
            }
        } catch (IOException ignored) {
            // A registry that cannot be read starts empty.
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {
                    // Nothing else can be done here.
                }
            }
        }
    }

    private void save() {
        StringBuilder content = new StringBuilder();
        for (Project project : projects.values()) {
            content.append(project.getName());
            content.append(TAB);
            content.append(project.getFolder());
            content.append(TAB);
            content.append(Long.toString(project.getCreatedAt()));
            content.append('\n');
        }
        try {
            SdsAtomicFile.write(file, content.toString(), UTF8);
        } catch (IOException ignored) {
            // A registry that cannot be written keeps its in-memory content.
        }
    }

    /** One project. */
    public static final class Project {
        private final String name;
        private final String folder;
        private final long createdAt;

        Project(String name, String folder, long createdAt) {
            this.name = name;
            this.folder = folder;
            this.createdAt = createdAt;
        }

        public String getName() { return name; }

        /** The folder of the project relative to the workspace root. */
        public String getFolder() { return folder; }

        public long getCreatedAt() { return createdAt; }
    }
}
