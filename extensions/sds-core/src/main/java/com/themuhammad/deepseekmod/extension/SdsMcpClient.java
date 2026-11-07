package com.themuhammad.deepseekmod.extension;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The MCP client.
 *
 * It speaks JSON-RPC 2.0 over HTTP to a server the user entered. The
 * transport is only used when the network policy allows it, so the client
 * itself never opens a connection on its own.
 *
 * The client is deliberately small: it performs the handshake of the
 * specification, it lists the tools of the server and it calls one tool. The
 * answers are parsed with the strict reader {@link SdsJson}, so a broken or
 * hostile answer is refused instead of being used.
 *
 * The handshake follows the revisions that use a session (2024-11-05 up to
 * 2025-11-25): the client sends {@code initialize} with the version it
 * prefers, the server answers with the version it will speak, the client
 * confirms with {@code notifications/initialized} and it sends the negotiated
 * version in the {@code MCP-Protocol-Version} header of every later call.
 */
public final class SdsMcpClient {
    public static final int MAX_RESPONSE_BYTES = 256 * 1024;
    public static final int CONNECT_TIMEOUT_MS = 10_000;
    public static final int READ_TIMEOUT_MS = 20_000;
    /** The version the client prefers. The server may answer with another one. */
    public static final String PREFERRED_PROTOCOL_VERSION = "2025-06-18";
    /** The oldest version the client understands. */
    public static final String OLDEST_PROTOCOL_VERSION = "2024-11-05";
    public static final String CLIENT_NAME = "DeepSeek-Android-Agent";
    public static final String CLIENT_VERSION = "1.0.0";

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private final SdsMcpConfigStore.Server server;
    private volatile String sessionId;
    private volatile String protocolVersion = PREFERRED_PROTOCOL_VERSION;
    private volatile boolean initialized;

    public SdsMcpClient(SdsMcpConfigStore.Server server, String sessionId) {
        if (server == null) throw new IllegalArgumentException("the server is required");
        this.server = server;
        this.sessionId = sessionId;
    }

    /** The version that was negotiated with the server. */
    public String getProtocolVersion() {
        return protocolVersion;
    }

    /** The session the server handed out, empty when the server has no session. */
    public String getSessionId() {
        return sessionId == null ? "" : sessionId;
    }

    /** True when the handshake was done. */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Performs the handshake of the specification.
     *
     * The server answers with the version it will speak. A version the client
     * does not know is refused, because the rest of the conversation would be
     * built on an unknown dialect.
     */
    public void initialize() throws IOException {
        if (initialized) return;
        Map<String, Object> result = call("initialize", buildInitializeParameters());
        applyInitializeResult(result);
        notify("notifications/initialized");
        initialized = true;
    }

    /** The parameters of the initialize request. */
    static Map<String, Object> buildInitializeParameters() {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("protocolVersion", PREFERRED_PROTOCOL_VERSION);
        parameters.put("capabilities", new LinkedHashMap<String, Object>());
        Map<String, Object> clientInfo = new LinkedHashMap<>();
        clientInfo.put("name", CLIENT_NAME);
        clientInfo.put("version", CLIENT_VERSION);
        parameters.put("clientInfo", clientInfo);
        return parameters;
    }

    /**
     * Takes the answer of the server.
     *
     * @param result the result of the initialize request.
     * @throws IOException when the server speaks a version the client does not know.
     */
    void applyInitializeResult(Map<String, Object> result) throws IOException {
        String negotiated = result == null ? null : SdsJson.optString(result, "protocolVersion");
        if (negotiated == null || negotiated.trim().isEmpty()) {
            throw new IOException("the server did not answer with a protocol version");
        }
        String trimmed = negotiated.trim();
        if (!trimmed.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new IOException("the server answered with an unknown protocol version");
        }
        if (trimmed.compareTo(OLDEST_PROTOCOL_VERSION) < 0) {
            throw new IOException("the server speaks a protocol version that is too old");
        }
        protocolVersion = trimmed;
    }

    /** The tools the server offers. */
    public List<ToolInfo> listTools() throws IOException {
        initialize();
        Map<String, Object> result = call("tools/list", null);
        List<Object> tools = SdsJson.optArray(result, "tools");
        List<ToolInfo> list = new ArrayList<>();
        if (tools == null) return list;
        for (Object entry : tools) {
            Map<String, Object> tool = SdsJson.asObject(entry);
            if (tool == null) continue;
            String name = SdsJson.optString(tool, "name");
            if (name == null || name.isEmpty()) continue;
            String description = SdsJson.optString(tool, "description");
            Map<String, Object> schema = SdsJson.optObject(tool, "inputSchema");
            list.add(new ToolInfo(name, description == null ? "" : description, schema));
        }
        return list;
    }

    /** Calls one tool of the server and returns the content of the answer. */
    public String callTool(String toolName, String argumentsJson) throws IOException {
        if (toolName == null || toolName.trim().isEmpty()) {
            throw new IllegalArgumentException("the tool name is required");
        }
        initialize();
        Map<String, Object> arguments = new LinkedHashMap<>();
        if (argumentsJson != null && !argumentsJson.trim().isEmpty()) {
            arguments = SdsJson.parseObject(argumentsJson);
        }
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("name", toolName.trim());
        parameters.put("arguments", arguments);
        Map<String, Object> result = call("tools/call", parameters);
        List<Object> content = SdsJson.optArray(result, "content");
        if (content == null) return "";
        StringBuilder builder = new StringBuilder();
        for (Object entry : content) {
            Map<String, Object> item = SdsJson.asObject(entry);
            if (item == null) continue;
            String type = SdsJson.optString(item, "type");
            String text = SdsJson.optString(item, "text");
            if (text == null) continue;
            if (builder.length() > 0) builder.append('\n');
            if (type == null || "text".equals(type)) builder.append(text);
        }
        return builder.toString();
    }

    /**
     * Sends a notification.
     *
     * A notification has no id and no answer, so the connection is closed right
     * after the body was sent.
     */
    public void notify(String method) throws IOException {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("method", method);
        send(request, false);
    }

    /** Performs one JSON-RPC call. */
    public Map<String, Object> call(String method, Map<String, Object> parameters) throws IOException {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("id", nextRequestId());
        request.put("method", method);
        if (parameters != null) request.put("params", parameters);
        Map<String, Object> answer = send(request, true);
        Object error = answer.get("error");
        if (error instanceof Map) {
            Map<String, Object> failure = SdsJson.asObject(error);
            String message = SdsJson.optString(failure, "message");
            throw new IOException("the server refused the call"
                    + (message == null ? "" : ": " + message));
        }
        Map<String, Object> result = SdsJson.optObject(answer, "result");
        return result == null ? new LinkedHashMap<String, Object>() : result;
    }

    /**
     * Sends one request and reads the answer.
     *
     * @param request  the JSON-RPC message.
     * @param expectAnswer true for a call, false for a notification.
     * @return the answer of the server, empty for a notification.
     */
    private Map<String, Object> send(Map<String, Object> request, boolean expectAnswer)
            throws IOException {
        HttpURLConnection connection = null;
        try {
            String address = server.getUrl();
            if (address == null || !address.trim().toLowerCase(java.util.Locale.US).startsWith("https://")) {
                throw new IOException("the server address has to use https");
            }
            URL url = new URL(address);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            // A redirect is never followed: the token of the server must not reach
            // another host, and the answer of a redirect is not a JSON-RPC answer.
            connection.setInstanceFollowRedirects(false);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("MCP-Protocol-Version", protocolVersion);
            if (sessionId != null && !sessionId.isEmpty()) {
                connection.setRequestProperty("Mcp-Session-Id", sessionId);
            }
            if (server.getToken() != null && !server.getToken().isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + server.getToken());
            }

            byte[] payload = SdsJsonWriter.write(request).getBytes(UTF8);
            OutputStream output = connection.getOutputStream();
            try {
                output.write(payload);
                output.flush();
            } finally {
                output.close();
            }

            int status = connection.getResponseCode();
            if (status >= 300 && status < 400) {
                throw new IOException("the server answered with a redirect");
            }
            // The server may hand out a session with the handshake.
            String answerSession = connection.getHeaderField("Mcp-Session-Id");
            if (answerSession != null && !answerSession.trim().isEmpty()) {
                sessionId = answerSession.trim();
            }
            if (!expectAnswer) {
                // A notification is answered with 202 and an empty body.
                drain(connection, status);
                return new LinkedHashMap<String, Object>();
            }
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String body = readBounded(stream);
            if (status >= 400) {
                throw new IOException("the server answered with the status " + status);
            }
            if (body.trim().isEmpty()) {
                throw new IOException("the server answered with an empty body");
            }
            return SdsJson.parseObject(body);
        } catch (IOException failure) {
            throw failure;
        } catch (Exception refused) {
            throw new IOException("the server could not be reached");
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /** Reads and closes the answer of a notification. */
    private static void drain(HttpURLConnection connection, int status) {
        try {
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            if (stream != null) {
                byte[] chunk = new byte[4096];
                while (stream.read(chunk) > 0) {
                    // The answer of a notification is not needed.
                }
                stream.close();
            }
        } catch (IOException ignored) {
            // The notification was sent, the answer of the server does not matter.
        }
    }

    private static final java.util.concurrent.atomic.AtomicInteger REQUEST_ID =
            new java.util.concurrent.atomic.AtomicInteger(1);

    /** Every call carries its own id, so a server can tell the calls apart. */
    private static int nextRequestId() {
        return REQUEST_ID.getAndIncrement();
    }

    private static String readBounded(InputStream stream) throws IOException {
        if (stream == null) return "";
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8 * 1024];
        int read;
        int total = 0;
        try {
            while ((read = stream.read(chunk)) > 0) {
                total += read;
                if (total > MAX_RESPONSE_BYTES) {
                    throw new IOException("the answer of the server is too long");
                }
                buffer.write(chunk, 0, read);
            }
        } finally {
            stream.close();
        }
        return new String(buffer.toByteArray(), UTF8);
    }

    /** One tool that the server offers. */
    public static final class ToolInfo {
        private final String name;
        private final String description;
        private final Map<String, Object> schema;

        ToolInfo(String name, String description, Map<String, Object> schema) {
            this.name = name;
            this.description = description;
            this.schema = schema;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
        public Map<String, Object> getSchema() { return schema; }

        /** The schema as a readable text, so the console can show it. */
        public String getSchemaText() {
            if (schema == null || schema.isEmpty()) return "{}";
            return SdsJsonWriter.write(schema);
        }
    }
}
