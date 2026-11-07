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
 * The app-private memory store.
 *
 * A memory is a short note the user keeps for the assistant. The store is a
 * plain file in the app-private directory, so it is removed with the app and it
 * is never shared with other apps. The entries are bounded in number and in
 * size, and a value never leaves the device unless the user copies it.
 *
 * The file format is one record per line: the key, a tab and the Base64 encoded
 * value. That keeps the format unambiguous without a JSON dependency.
 */
public final class SdsMemoryStore {
    public static final int MAX_ENTRIES = 200;
    public static final int MAX_VALUE_CHARS = 4 * 1024;
    public static final int MAX_KEY_LENGTH = 64;

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final String TAB = "\t";

    private final File file;
    private final Map<String, String> entries = new LinkedHashMap<>();

    public SdsMemoryStore(File file) {
        if (file == null) throw new IllegalArgumentException("file is required");
        this.file = file;
        load();
    }

    /** Stores a note. An empty key is refused and the value is bounded. */
    public synchronized boolean put(String key, String value) {
        String normalizedKey = normalizeKey(key);
        String normalizedValue = value == null ? "" : value;
        if (normalizedValue.length() > MAX_VALUE_CHARS) {
            throw new IllegalArgumentException("the memory value is too long");
        }
        if (!entries.containsKey(normalizedKey) && entries.size() >= MAX_ENTRIES) {
            throw new IllegalStateException("the memory store is full");
        }
        entries.put(normalizedKey, normalizedValue);
        save();
        return true;
    }

    /** Reads a note, or null when it does not exist. */
    public synchronized String get(String key) {
        if (key == null) return null;
        return entries.get(key.trim());
    }

    /** Removes a note. */
    public synchronized boolean remove(String key) {
        if (key == null) return false;
        boolean removed = entries.remove(key.trim()) != null;
        if (removed) save();
        return removed;
    }

    /** Removes every note. */
    public synchronized void clear() {
        entries.clear();
        save();
    }

    public synchronized int size() {
        return entries.size();
    }

    /** A snapshot of every note, in insertion order. */
    public synchronized Map<String, String> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(entries));
    }

    /** The notes as a bounded block of text, ready for a prompt. */
    public synchronized String asPromptBlock(int maxChars) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            String line = "- " + entry.getKey() + ": " + entry.getValue();
            if (builder.length() + line.length() > maxChars) break;
            if (builder.length() > 0) builder.append('\n');
            builder.append(line);
        }
        return builder.toString();
    }

    private static String normalizeKey(String key) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("the memory key is required");
        }
        String trimmed = key.trim();
        if (trimmed.length() > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException("the memory key is too long");
        }
        for (int index = 0; index < trimmed.length(); index++) {
            char c = trimmed.charAt(index);
            boolean allowed = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '.' || c == '_' || c == '-';
            if (!allowed) {
                throw new IllegalArgumentException("the memory key has an unsupported character");
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
            while ((line = reader.readLine()) != null && entries.size() < MAX_ENTRIES) {
                int separator = line.indexOf(TAB);
                if (separator <= 0) continue;
                String key = line.substring(0, separator);
                String encoded = line.substring(separator + 1);
                try {
                    String value = new String(Base64Codec.decode(encoded), UTF8);
                    entries.put(key, value);
                } catch (IllegalArgumentException ignored) {
                    // A damaged record is skipped instead of failing the whole store.
                }
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
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            content.append(entry.getKey());
            content.append(TAB);
            content.append(Base64Codec.encode(entry.getValue().getBytes(UTF8)));
            content.append('\n');
        }
        try {
            SdsAtomicFile.write(file, content.toString(), UTF8);
        } catch (IOException ignored) {
            // A store that cannot be written keeps its in-memory content.
        }
    }

    /** A tiny Base64 codec so the store stays free of Android and of Java 8 APIs. */
    static final class Base64Codec {
        private static final char[] ALPHABET =
                "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();

        private Base64Codec() {
        }

        static String encode(byte[] data) {
            StringBuilder builder = new StringBuilder();
            for (int index = 0; index < data.length; index += 3) {
                int remaining = data.length - index;
                int chunk = (data[index] & 0xff) << 16;
                if (remaining > 1) chunk |= (data[index + 1] & 0xff) << 8;
                if (remaining > 2) chunk |= data[index + 2] & 0xff;
                builder.append(ALPHABET[(chunk >> 18) & 0x3f]);
                builder.append(ALPHABET[(chunk >> 12) & 0x3f]);
                builder.append(remaining > 1 ? ALPHABET[(chunk >> 6) & 0x3f] : '=');
                builder.append(remaining > 2 ? ALPHABET[chunk & 0x3f] : '=');
            }
            return builder.toString();
        }

        static byte[] decode(String value) {
            int[] table = new int[128];
            for (int index = 0; index < table.length; index++) table[index] = -1;
            for (int index = 0; index < ALPHABET.length; index++) table[ALPHABET[index]] = index;
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            int buffer = 0;
            int bits = 0;
            for (int index = 0; index < value.length(); index++) {
                char c = value.charAt(index);
                if (c == '=') break;
                if (c >= table.length || table[c] < 0) {
                    throw new IllegalArgumentException("invalid base64 data");
                }
                buffer = (buffer << 6) | table[c];
                bits += 6;
                if (bits >= 8) {
                    bits -= 8;
                    output.write((buffer >> bits) & 0xff);
                }
            }
            return output.toByteArray();
        }
    }

    /** The keys of every note, for the console. */
    public synchronized List<String> keys() {
        return Collections.unmodifiableList(new ArrayList<>(entries.keySet()));
    }
}
