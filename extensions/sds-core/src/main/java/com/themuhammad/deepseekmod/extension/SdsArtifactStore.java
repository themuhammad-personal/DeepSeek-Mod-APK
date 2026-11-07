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
 * The native artifact store.
 *
 * An artifact is a piece of content the assistant produced, for example a
 * snippet, a document or a table. The store keeps it in the app-private
 * directory, so it is removed with the app and it never leaves the device
 * unless the user exports it.
 *
 * The content is bounded, the number of artifacts is bounded and the file
 * format is the same tab separated Base64 format the memory store uses.
 */
public final class SdsArtifactStore {
    public static final int MAX_ARTIFACTS = 50;
    public static final int MAX_CONTENT_CHARS = 256 * 1024;
    public static final int MAX_TITLE_LENGTH = 80;
    public static final int MAX_LANGUAGE_LENGTH = 24;

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final String TAB = "\t";

    private final File file;
    private final Map<String, Artifact> artifacts = new LinkedHashMap<>();
    private long nextId = 1;

    public SdsArtifactStore(File file) {
        if (file == null) throw new IllegalArgumentException("file is required");
        this.file = file;
        load();
    }

    /** Stores an artifact and returns it. */
    public synchronized Artifact create(String title, String language, String content) {
        String normalizedTitle = normalizeTitle(title);
        String normalizedLanguage = normalizeLanguage(language);
        String normalizedContent = content == null ? "" : content;
        if (normalizedContent.length() > MAX_CONTENT_CHARS) {
            throw new IllegalArgumentException("the artifact content is too long");
        }
        if (artifacts.size() >= MAX_ARTIFACTS) {
            throw new IllegalStateException("the artifact limit is reached");
        }
        // The counter keeps the identifier unique even when two artifacts are
        // created inside the same millisecond.
        String id = "a-" + System.currentTimeMillis() + "-" + nextId++;
        if (nextId > MAX_ARTIFACTS + 1) nextId = artifacts.size() + 1;
        Artifact artifact = new Artifact(id, normalizedTitle, normalizedLanguage, normalizedContent,
                System.currentTimeMillis());
        artifacts.put(id, artifact);
        save();
        return artifact;
    }

    /** Removes an artifact. */
    public synchronized boolean delete(String id) {
        if (id == null) return false;
        boolean removed = artifacts.remove(id.trim()) != null;
        if (removed) save();
        return removed;
    }

    public synchronized Artifact find(String id) {
        if (id == null) return null;
        return artifacts.get(id.trim());
    }

    public synchronized int size() {
        return artifacts.size();
    }

    /** Every artifact, newest first. */
    public synchronized List<Artifact> snapshot() {
        List<Artifact> result = new ArrayList<>(artifacts.values());
        Collections.reverse(result);
        return Collections.unmodifiableList(result);
    }

    /** The artifact titles, for a prompt block. */
    public synchronized String asPromptBlock(int maxChars) {
        StringBuilder builder = new StringBuilder();
        for (Artifact artifact : snapshot()) {
            String line = "- " + artifact.getId() + " " + artifact.getTitle()
                    + " (" + artifact.getLanguage() + ", " + artifact.getContentLength() + " characters)";
            if (builder.length() + line.length() > maxChars) break;
            if (builder.length() > 0) builder.append('\n');
            builder.append(line);
        }
        return builder.toString();
    }

    private static String normalizeTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("the artifact title is required");
        }
        String trimmed = title.trim();
        if (trimmed.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("the artifact title is too long");
        }
        return trimmed;
    }

    private static String normalizeLanguage(String language) {
        if (language == null || language.trim().isEmpty()) return "text";
        String trimmed = language.trim().toLowerCase(java.util.Locale.US);
        if (trimmed.length() > MAX_LANGUAGE_LENGTH) {
            throw new IllegalArgumentException("the artifact language is too long");
        }
        for (int index = 0; index < trimmed.length(); index++) {
            char c = trimmed.charAt(index);
            boolean allowed = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '+' || c == '#';
            if (!allowed) {
                throw new IllegalArgumentException("the artifact language has an unsupported character");
            }
        }
        return trimmed;
    }

    private void load() {
        if (!file.isFile()) return;
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), UTF8));
            String line;
            while ((line = reader.readLine()) != null && artifacts.size() < MAX_ARTIFACTS) {
                String[] parts = line.split("\t", -1);
                if (parts.length < 5) continue;
                long createdAt;
                try {
                    createdAt = Long.parseLong(parts[4]);
                } catch (NumberFormatException ignored) {
                    continue;
                }
                try {
                    String title = new String(SdsMemoryStore.Base64Codec.decode(parts[1]), UTF8);
                    String language = new String(SdsMemoryStore.Base64Codec.decode(parts[2]), UTF8);
                    String content = new String(SdsMemoryStore.Base64Codec.decode(parts[3]), UTF8);
                    artifacts.put(parts[0], new Artifact(parts[0], title, language, content, createdAt));
                } catch (IllegalArgumentException ignored) {
                    // A damaged record is skipped instead of failing the whole store.
                }
                nextId = artifacts.size() + 1;
            }
        } catch (IOException ignored) {
            // A store that cannot be read starts empty.
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
        for (Artifact artifact : artifacts.values()) {
            content.append(artifact.getId());
            content.append(TAB);
            content.append(SdsMemoryStore.Base64Codec.encode(artifact.getTitle().getBytes(UTF8)));
            content.append(TAB);
            content.append(SdsMemoryStore.Base64Codec.encode(artifact.getLanguage().getBytes(UTF8)));
            content.append(TAB);
            content.append(SdsMemoryStore.Base64Codec.encode(artifact.getContent().getBytes(UTF8)));
            content.append(TAB);
            content.append(Long.toString(artifact.getCreatedAt()));
            content.append('\n');
        }
        try {
            SdsAtomicFile.write(file, content.toString(), UTF8);
        } catch (IOException ignored) {
            // A store that cannot be written keeps its in-memory content.
        }
    }

    /** One artifact. */
    public static final class Artifact {
        private final String id;
        private final String title;
        private final String language;
        private final String content;
        private final long createdAt;

        Artifact(String id, String title, String language, String content, long createdAt) {
            this.id = id;
            this.title = title;
            this.language = language;
            this.content = content;
            this.createdAt = createdAt;
        }

        public String getId() { return id; }
        public String getTitle() { return title; }
        public String getLanguage() { return language; }
        public String getContent() { return content; }
        public long getCreatedAt() { return createdAt; }
        public int getContentLength() { return content.length(); }

        /** The name that is used when the artifact is exported. */
        public String getExportName() {
            StringBuilder builder = new StringBuilder();
            for (int index = 0; index < title.length(); index++) {
                char c = title.charAt(index);
                boolean allowed = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                        || (c >= '0' && c <= '9') || c == '-' || c == '_';
                builder.append(allowed ? c : '-');
            }
            String name = builder.toString();
            while (name.startsWith("-")) name = name.substring(1);
            if (name.isEmpty()) name = "artifact";
            return name + "." + language;
        }
    }
}
