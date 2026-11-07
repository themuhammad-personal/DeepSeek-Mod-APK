package com.themuhammad.deepseekmod.extension;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;

/**
 * The bounded HTTPS fetcher.
 *
 * It is used by the MCP client and by the research tool. The rules are
 * deliberately strict: only https, no redirect is followed, the answer is
 * bounded and the body is reduced to readable text, so nothing that a remote
 * page contains can become a command, a script or a view.
 */
public final class SdsHttpFetcher {
    public static final int MAX_BODY_BYTES = 512 * 1024;
    public static final int CONNECT_TIMEOUT_MS = 10_000;
    public static final int READ_TIMEOUT_MS = 20_000;

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private SdsHttpFetcher() {
    }

    /** The result of a fetch. */
    public static final class Response {
        private final int status;
        private final String body;
        private final String contentType;

        Response(int status, String body, String contentType) {
            this.status = status;
            this.body = body;
            this.contentType = contentType;
        }

        public int getStatus() { return status; }
        public String getBody() { return body; }
        public String getContentType() { return contentType; }
        public boolean isOk() { return status >= 200 && status < 300; }
    }

    /**
     * Fetches an address.
     *
     * @param url the address, it has to use https
     * @param maxBytes the largest answer that is read
     */
    public static Response fetch(String url, int maxBytes) throws IOException {
        if (url == null || !url.startsWith("https://")) {
            throw new IOException("only https is allowed");
        }
        if (url.length() > SdsMcpConfigStore.MAX_URL_LENGTH) {
            throw new IOException("the address is too long");
        }
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            // A redirect is not followed, so a server cannot move the request
            // to another host behind the back of the user.
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("Accept", "text/plain, text/html, application/json;q=0.9");
            connection.setRequestProperty("User-Agent", "DeepSeek-Android-Agent/1.0");
            int status = connection.getResponseCode();
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String body = readBounded(stream, maxBytes);
            return new Response(status, body, connection.getContentType());
        } catch (IOException failure) {
            throw failure;
        } catch (Exception refused) {
            throw new IOException("the address could not be reached");
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /**
     * Downloads a file.
     *
     * The download only runs over https, a redirect is not followed and the
     * size is bounded. The file is written next to the target and it only
     * takes the place of the target when the whole download worked, so an
     * interrupted download never leaves a truncated file behind.
     *
     * @param url the address, it has to use https
     * @param target the file that is written
     * @param maxBytes the largest file that is accepted
     * @param readTimeoutMs the time a single read may take
     * @return the number of bytes that were written
     */
    public static long download(String url, File target, long maxBytes, int readTimeoutMs)
            throws IOException {
        if (url == null || !url.startsWith("https://")) {
            throw new IOException("only https is allowed");
        }
        if (url.length() > SdsMcpConfigStore.MAX_URL_LENGTH) {
            throw new IOException("the address is too long");
        }
        if (target == null) throw new IllegalArgumentException("target is required");
        if (maxBytes <= 0) throw new IllegalArgumentException("the limit has to be positive");
        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) {
            //noinspection ResultOfMethodCallIgnored
            parent.mkdirs();
        }
        File part = new File(target.getAbsolutePath() + ".part");
        HttpURLConnection connection = null;
        long total = 0;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(readTimeoutMs);
            connection.setRequestProperty("Accept", "application/octet-stream");
            connection.setRequestProperty("User-Agent", "DeepSeek-Android-Agent/1.0");
            int status = connection.getResponseCode();
            if (status >= 300 && status < 400) {
                throw new IOException("the address answered with a redirect");
            }
            if (status >= 400) {
                throw new IOException("the address answered with the status " + status);
            }
            long declared = connection.getContentLength();
            if (declared > maxBytes) {
                throw new IOException("the file is larger than the limit");
            }
            InputStream input = connection.getInputStream();
            java.io.OutputStream output = new java.io.FileOutputStream(part);
            try {
                byte[] chunk = new byte[64 * 1024];
                int read;
                while ((read = input.read(chunk)) > 0) {
                    total += read;
                    if (total > maxBytes) {
                        throw new IOException("the file is larger than the limit");
                    }
                    output.write(chunk, 0, read);
                }
                output.flush();
            } finally {
                try {
                    output.close();
                } catch (IOException ignored) {
                    // The file is closed either way.
                }
                try {
                    input.close();
                } catch (IOException ignored) {
                    // The file is closed either way.
                }
            }
        } catch (IOException failure) {
            deleteQuietly(part);
            throw failure;
        } catch (Exception refused) {
            deleteQuietly(part);
            throw new IOException("the address could not be reached");
        } finally {
            if (connection != null) connection.disconnect();
        }
        if (target.exists() && !target.delete()) {
            deleteQuietly(part);
            throw new IOException("the old file could not be replaced");
        }
        if (!part.renameTo(target)) {
            deleteQuietly(part);
            throw new IOException("the file could not be moved into place");
        }
        return total;
    }

    /** Removes a file and ignores the failure. */
    static void deleteQuietly(File file) {
        if (file == null) return;
        try {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        } catch (Throwable ignored) {
            // Nothing else can be done here.
        }
    }

    private static String readBounded(InputStream stream, int maxBytes) throws IOException {
        if (stream == null) return "";
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8 * 1024];
        int read;
        int total = 0;
        try {
            while ((read = stream.read(chunk)) > 0) {
                total += read;
                if (total > maxBytes) break;
                buffer.write(chunk, 0, read);
            }
        } finally {
            stream.close();
        }
        return new String(buffer.toByteArray(), UTF8);
    }

    /**
     * Reduces a document to readable text.
     *
     * The script and the style parts are dropped, the tags are removed and the
     * common entities are decoded. The result is plain text, so a page can
     * never inject a view, a script or a command.
     */
    public static String toPlainText(String document, int maxChars) {
        if (document == null) return "";
        StringBuilder builder = new StringBuilder();
        int index = 0;
        int length = document.length();
        while (index < length && builder.length() < maxChars) {
            char c = document.charAt(index);
            if (c == '<') {
                int end = document.indexOf('>', index);
                if (end < 0) break;
                String tag = document.substring(index + 1, end).toLowerCase(java.util.Locale.US);
                if (tag.startsWith("script") || tag.startsWith("style") || tag.startsWith("noscript")) {
                    String name = tag.split(" ")[0].split("/")[0];
                    int close = indexOfIgnoreCase(document, "</" + name, end);
                    if (close > 0) {
                        index = document.indexOf('>', close);
                        index = index < 0 ? length : index + 1;
                        builder.append('\n');
                        continue;
                    }
                }
                if (tag.startsWith("br") || tag.startsWith("p") || tag.startsWith("div")
                        || tag.startsWith("li") || tag.startsWith("tr") || tag.startsWith("h")) {
                    builder.append('\n');
                }
                index = end + 1;
                continue;
            }
            if (c == '&') {
                int end = document.indexOf(';', index);
                if (end > 0 && end - index <= 8) {
                    String entity = document.substring(index + 1, end);
                    String decoded = decodeEntity(entity);
                    if (decoded != null) {
                        builder.append(decoded);
                        index = end + 1;
                        continue;
                    }
                }
            }
            builder.append(c);
            index++;
        }
        String text = builder.toString();
        text = text.replaceAll("[ \\t]{2,}", " ");
        text = text.replaceAll("\\n{3,}", "\n\n");
        return text.trim();
    }

    /**
     * Finds a text in a document without copying the document.
     *
     * The document is not lowered once, because that would change the length of
     * the string for a few characters and break the indexes.
     */
    static int indexOfIgnoreCase(String document, String needle, int from) {
        if (document == null || needle == null || needle.isEmpty()) return -1;
        int limit = document.length() - needle.length();
        for (int index = Math.max(0, from); index <= limit; index++) {
            if (document.regionMatches(true, index, needle, 0, needle.length())) {
                return index;
            }
        }
        return -1;
    }

    private static String decodeEntity(String entity) {
        if ("amp".equals(entity)) return "&";
        if ("lt".equals(entity)) return "<";
        if ("gt".equals(entity)) return ">";
        if ("quot".equals(entity)) return "\"";
        if ("apos".equals(entity)) return "'";
        if ("nbsp".equals(entity)) return " ";
        if (entity.startsWith("#")) {
            try {
                int code = entity.startsWith("#x") || entity.startsWith("#X")
                        ? Integer.parseInt(entity.substring(2), 16)
                        : Integer.parseInt(entity.substring(1));
                return String.valueOf((char) code);
            } catch (NumberFormatException refused) {
                return null;
            }
        }
        return null;
    }
}
