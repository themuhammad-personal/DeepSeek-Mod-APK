package com.themuhammad.deepseekmod.extension;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The MCP server configuration.
 *
 * A server is a name, an HTTPS address and an optional token. The list is
 * stored in the app-private directory and it never leaves the device. The
 * token is never written into a log, a timeline entry or a diagnostics record.
 */
public final class SdsMcpConfigStore {
    public static final int MAX_SERVERS = 16;
    public static final int MAX_NAME_LENGTH = 48;
    public static final int MAX_URL_LENGTH = 300;
    public static final int MAX_TOKEN_LENGTH = 400;

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final String TAB = "\t";

    private final File file;
    private final Map<String, Server> servers = new LinkedHashMap<>();

    public SdsMcpConfigStore(File file) {
        if (file == null) throw new IllegalArgumentException("file is required");
        this.file = file;
        load();
    }

    /** Adds a server and returns it. */
    public synchronized Server add(String name, String url, String token) {
        String normalizedName = normalizeName(name);
        if (servers.containsKey(normalizedName)) {
            throw new IllegalStateException("the server is already in the list");
        }
        if (servers.size() >= MAX_SERVERS) {
            throw new IllegalStateException("the server limit is reached");
        }
        Server server = new Server(normalizedName, normalizeUrl(url),
                token == null ? "" : token.trim(), false);
        servers.put(normalizedName, server);
        save();
        return server;
    }

    /** Removes a server. */
    public synchronized boolean remove(String name) {
        if (name == null) return false;
        boolean removed = servers.remove(name.trim()) != null;
        if (removed) save();
        return removed;
    }

    /** Turns a server on or off. */
    public synchronized boolean setEnabled(String name, boolean enabled) {
        Server server = name == null ? null : servers.get(name.trim());
        if (server == null) return false;
        server.setEnabled(enabled);
        save();
        return true;
    }

    public synchronized Server find(String name) {
        if (name == null) return null;
        return servers.get(name.trim());
    }

    public synchronized int size() {
        return servers.size();
    }

    public synchronized List<Server> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(servers.values()));
    }

    /** The servers that are switched on. */
    public synchronized List<Server> enabledServers() {
        List<Server> result = new ArrayList<>();
        for (Server server : servers.values()) {
            if (server.isEnabled()) result.add(server);
        }
        return Collections.unmodifiableList(result);
    }

    private static String normalizeName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("the server name is required");
        }
        String trimmed = name.trim();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("the server name is too long");
        }
        for (int index = 0; index < trimmed.length(); index++) {
            char c = trimmed.charAt(index);
            boolean allowed = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '-' || c == '_';
            if (!allowed) {
                throw new IllegalArgumentException("the server name has an unsupported character");
            }
        }
        return trimmed;
    }

    private static String normalizeUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("the server address is required");
        }
        String trimmed = url.trim();
        if (trimmed.length() > MAX_URL_LENGTH) {
            throw new IllegalArgumentException("the server address is too long");
        }
        if (!trimmed.startsWith("https://")) {
            throw new IllegalArgumentException("the server address has to use https");
        }
        if (trimmed.contains(" ")) {
            throw new IllegalArgumentException("the server address has an unsupported character");
        }
        return trimmed;
    }

    private void load() {
        if (!file.isFile()) return;
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), UTF8));
            String line;
            while ((line = reader.readLine()) != null && servers.size() < MAX_SERVERS) {
                String[] parts = line.split("\t", -1);
                if (parts.length < 3) continue;
                try {
                    servers.put(parts[0], new Server(parts[0], parts[1], parts[2], "1".equals(parts[3])));
                } catch (IllegalArgumentException ignored) {
                    // A damaged record is skipped instead of failing the whole list.
                }
            }
        } catch (IOException ignored) {
            // A list that cannot be read starts empty.
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
        Writer writer = null;
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                //noinspection ResultOfMethodCallIgnored
                parent.mkdirs();
            }
            writer = new OutputStreamWriter(new FileOutputStream(file), UTF8);
            for (Server server : servers.values()) {
                writer.write(server.getName());
                writer.write(TAB);
                writer.write(server.getUrl());
                writer.write(TAB);
                writer.write(server.getToken());
                writer.write(TAB);
                writer.write(server.isEnabled() ? "1" : "0");
                writer.write("\n");
            }
            writer.flush();
        } catch (IOException ignored) {
            // A list that cannot be written keeps its in-memory content.
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ignored) {
                    // Nothing else can be done here.
                }
            }
        }
    }

    /** One MCP server. */
    public static final class Server {
        private final String name;
        private final String url;
        private final String token;
        private boolean enabled;

        Server(String name, String url, String token, boolean enabled) {
            this.name = name;
            this.url = url;
            this.token = token;
            this.enabled = enabled;
        }

        public String getName() { return name; }
        public String getUrl() { return url; }

        /** The token of the server. It is never written into a log. */
        public String getToken() { return token; }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean value) { enabled = value; }

        /** The name of the tool of this server inside the allow-list. */
        public String toolPrefix() {
            return "mcp." + name + ".";
        }
    }
}
