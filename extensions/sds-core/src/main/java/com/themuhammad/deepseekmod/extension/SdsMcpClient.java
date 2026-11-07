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
 * The client is deliberately small: it initializes a session, it lists the
 * tools of the server and it calls one tool. The answers are parsed with the
 * strict reader {@link SdsJson}, so a broken or hostile answer is refused
 * instead of being used.
 */
public final class SdsMcpClient {
    public static final int MAX_RESPONSE_BYTES = 256 * 1024;
    public static final int CONNECT_TIMEOUT_MS = 10_000;
    public static final int READ_TIMEOUT_MS = 20_000;
    public static final String PROTOCOL_VERSION = "2024-11-05";

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private final SdsMcpConfigStore.Server server;
    private final String sessionId;

    public SdsMcpClient(SdsMcpConfigStore.Server server, String sessionId) {
        if (server == null) throw new IllegalArgumentException("the server is required");
        this.server = server;
        this.sessionId = sessionId;
    }

    /** The tools the server offers. */
    public List<ToolInfo> listTools() throws IOException {
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

    /** Performs one JSON-RPC call. */
    public Map<String, Object> call(String method, Map<String, Object> parameters) throws IOException {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("id", nextRequestId());
        request.put("method", method);
        if (parameters != null) request.put("params", parameters);

        HttpURLConnection connection = null;
        try {
            URL url = new URL(server.getUrl());
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("MCP-Protocol-Version", PROTOCOL_VERSION);
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
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String body = readBounded(stream);
            if (status >= 400) {
                throw new IOException("the server answered with the status " + status);
            }
            Map<String, Object> answer = SdsJson.parseObject(body);
            Object error = answer.get("error");
            if (error instanceof Map) {
                Map<String, Object> failure = SdsJson.asObject(error);
                String message = SdsJson.optString(failure, "message");
                throw new IOException("the server refused the call"
                        + (message == null ? "" : ": " + message));
            }
            Map<String, Object> result = SdsJson.optObject(answer, "result");
            return result == null ? new LinkedHashMap<String, Object>() : result;
        } catch (IOException failure) {
            throw failure;
        } catch (Exception refused) {
            throw new IOException("the server could not be reached");
        } finally {
            if (connection != null) connection.disconnect();
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
