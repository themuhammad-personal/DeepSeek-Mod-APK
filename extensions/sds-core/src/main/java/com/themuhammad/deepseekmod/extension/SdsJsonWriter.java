package com.themuhammad.deepseekmod.extension;

import java.util.List;
import java.util.Map;

/**
 * A small JSON writer.
 *
 * It is the counterpart of {@link SdsJson} and it is used to build the
 * JSON-RPC requests and to show the schema of a tool. The output only holds
 * the types the reader understands, so a value that was read can always be
 * written back.
 */
public final class SdsJsonWriter {
    private SdsJsonWriter() {
    }

    /** Writes a value as a JSON document. */
    public static String write(Object value) {
        StringBuilder builder = new StringBuilder();
        writeValue(builder, value, 0);
        return builder.toString();
    }

    private static void writeValue(StringBuilder builder, Object value, int depth) {
        if (depth > SdsJson.MAX_DEPTH) {
            builder.append("null");
            return;
        }
        if (value == null) {
            builder.append("null");
        } else if (value instanceof String) {
            writeString(builder, (String) value);
        } else if (value instanceof Boolean) {
            builder.append(((Boolean) value) ? "true" : "false");
        } else if (value instanceof Double || value instanceof Float) {
            double number = ((Number) value).doubleValue();
            if (Double.isNaN(number) || Double.isInfinite(number)) {
                // A number that has no JSON form becomes null.
                builder.append("null");
            } else if (number == Math.rint(number) && Math.abs(number) < 9.0e15) {
                builder.append((long) number);
            } else {
                builder.append(number);
            }
        } else if (value instanceof Number) {
            builder.append(((Number) value).longValue());
        } else if (value instanceof Map) {
            builder.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (!first) builder.append(',');
                first = false;
                writeString(builder, String.valueOf(entry.getKey()));
                builder.append(':');
                writeValue(builder, entry.getValue(), depth + 1);
            }
            builder.append('}');
        } else if (value instanceof List) {
            builder.append('[');
            boolean first = true;
            for (Object item : (List<?>) value) {
                if (!first) builder.append(',');
                first = false;
                writeValue(builder, item, depth + 1);
            }
            builder.append(']');
        } else {
            writeString(builder, String.valueOf(value));
        }
    }

    private static void writeString(StringBuilder builder, String value) {
        builder.append('"');
        for (int index = 0; index < value.length(); index++) {
            char c = value.charAt(index);
            switch (c) {
                case '"': builder.append("\\\""); break;
                case '\\': builder.append("\\\\"); break;
                case '\n': builder.append("\\n"); break;
                case '\r': builder.append("\\r"); break;
                case '\t': builder.append("\\t"); break;
                case '\b': builder.append("\\b"); break;
                case '\f': builder.append("\\f"); break;
                default:
                    if (c < 0x20) {
                        builder.append(String.format("\\u%04x", (int) c));
                    } else {
                        builder.append(c);
                    }
            }
        }
        builder.append('"');
    }
}
