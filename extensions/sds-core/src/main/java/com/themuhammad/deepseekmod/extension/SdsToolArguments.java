package com.themuhammad.deepseekmod.extension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Strict reader for the flat JSON arguments of a tool call.
 *
 * Only a flat JSON object is accepted: string, number, boolean and null values.
 * Nested objects and arrays are rejected, so every tool has a small, explicit
 * schema that can be validated before anything runs. Unknown keys are rejected
 * by {@link #requireOnly(String...)}.
 */
public final class SdsToolArguments {
    private static final int MAX_KEYS = 16;
    private static final int MAX_KEY_LENGTH = 64;
    private static final int MAX_STRING_LENGTH = 64 * 1024;

    private final Map<String, Object> values;

    private SdsToolArguments(Map<String, Object> values) {
        this.values = values;
    }

    /** Parses flat JSON arguments. Throws when the payload is not a flat object. */
    public static SdsToolArguments parse(String json) {
        String source = json == null ? "{}" : json.trim();
        if (source.isEmpty()) source = "{}";
        if (source.length() > MAX_STRING_LENGTH) {
            throw new IllegalArgumentException("tool arguments exceed the size limit");
        }
        if (source.charAt(0) != '{' || source.charAt(source.length() - 1) != '}') {
            throw new IllegalArgumentException("tool arguments must be a JSON object");
        }
        Map<String, Object> parsed = new LinkedHashMap<>();
        Reader reader = new Reader(source);
        reader.skipWhitespace();
        reader.expect('{');
        reader.skipWhitespace();
        if (!reader.consume('}')) {
            while (true) {
                reader.skipWhitespace();
                String key = reader.readString();
                if (key.length() > MAX_KEY_LENGTH) {
                    throw new IllegalArgumentException("tool argument name is too long");
                }
                reader.skipWhitespace();
                reader.expect(':');
                reader.skipWhitespace();
                Object value = reader.readValue(0);
                if (parsed.size() >= MAX_KEYS) {
                    throw new IllegalArgumentException("too many tool arguments");
                }
                parsed.put(key, value);
                reader.skipWhitespace();
                if (reader.consume('}')) break;
                reader.expect(',');
            }
        }
        reader.skipWhitespace();
        if (!reader.atEnd()) {
            throw new IllegalArgumentException("trailing data after the tool arguments");
        }
        return new SdsToolArguments(parsed);
    }

    public Set<String> keys() {
        return Collections.unmodifiableSet(values.keySet());
    }

    public boolean has(String name) {
        return values.containsKey(name);
    }

    public String getString(String name, String fallback) {
        Object value = values.get(name);
        return value instanceof String ? (String) value : fallback;
    }

    public String requireString(String name) {
        Object value = values.get(name);
        if (!(value instanceof String) || ((String) value).isEmpty()) {
            throw new IllegalArgumentException("missing string argument: " + name);
        }
        return (String) value;
    }

    public int getInt(String name, int fallback) {
        Object value = values.get(name);
        if (value instanceof Integer) return (Integer) value;
        return fallback;
    }

    public boolean getBoolean(String name, boolean fallback) {
        Object value = values.get(name);
        if (value instanceof Boolean) return (Boolean) value;
        return fallback;
    }

    /** Rejects any argument that the tool does not declare. */
    public void requireOnly(String... allowed) {
        List<String> allowedKeys = new ArrayList<>();
        if (allowed != null) {
            for (String key : allowed) {
                if (key != null) allowedKeys.add(key);
            }
        }
        for (String key : values.keySet()) {
            if (!allowedKeys.contains(key)) {
                throw new IllegalArgumentException("unexpected argument: " + key);
            }
        }
    }

    /** Minimal strict JSON reader for flat objects. */
    private static final class Reader {
        private static final int MAX_DEPTH = 4;

        private final String source;
        private int index;

        Reader(String source) {
            this.source = source;
        }

        Object readValue(int depth) {
            if (depth > MAX_DEPTH) throw new IllegalArgumentException("tool arguments are nested too deeply");
            skipWhitespace();
            char c = peek();
            if (c == '{') throw new IllegalArgumentException("nested objects are not allowed");
            if (c == '[') throw new IllegalArgumentException("nested arrays are not allowed");
            if (c == '"') return readString();
            if (c == 't') { readLiteral("true"); return Boolean.TRUE; }
            if (c == 'f') { readLiteral("false"); return Boolean.FALSE; }
            if (c == 'n') { readLiteral("null"); return null; }
            return readNumber();
        }

        String readString() {
            expect('"');
            StringBuilder value = new StringBuilder();
            while (index < source.length()) {
                char c = source.charAt(index++);
                if (c == '"') return value.toString();
                if (c < 0x20) throw new IllegalArgumentException("invalid tool arguments");
                if (c == '\\') {
                    if (index >= source.length()) throw new IllegalArgumentException("invalid tool arguments");
                    char escaped = source.charAt(index++);
                    switch (escaped) {
                        case '"': value.append('"'); break;
                        case '\\': value.append('\\'); break;
                        case '/': value.append('/'); break;
                        case 'b': value.append('\b'); break;
                        case 'f': value.append('\f'); break;
                        case 'n': value.append('\n'); break;
                        case 'r': value.append('\r'); break;
                        case 't': value.append('\t'); break;
                        case 'u':
                            if (index + 4 > source.length()) {
                                throw new IllegalArgumentException("invalid tool arguments");
                            }
                            String hex = source.substring(index, index + 4);
                            try {
                                value.append((char) Integer.parseInt(hex, 16));
                            } catch (NumberFormatException error) {
                                throw new IllegalArgumentException("invalid tool arguments");
                            }
                            index += 4;
                            break;
                        default:
                            throw new IllegalArgumentException("invalid tool arguments");
                    }
                } else {
                    value.append(c);
                }
                if (value.length() > MAX_STRING_LENGTH) {
                    throw new IllegalArgumentException("tool argument value is too long");
                }
            }
            throw new IllegalArgumentException("invalid tool arguments");
        }

        private Object readNumber() {
            int start = index;
            if (peek() == '-') index++;
            while (index < source.length() && Character.isDigit(source.charAt(index))) index++;
            if (index == start) throw new IllegalArgumentException("invalid tool arguments");
            String number = source.substring(start, index);
            try {
                return Integer.valueOf(number);
            } catch (NumberFormatException error) {
                throw new IllegalArgumentException("tool arguments only accept integer numbers");
            }
        }

        private void readLiteral(String literal) {
            if (!source.startsWith(literal, index)) {
                throw new IllegalArgumentException("invalid tool arguments");
            }
            index += literal.length();
        }

        private boolean atEnd() {
            return index >= source.length();
        }

        private char peek() {
            return index < source.length() ? source.charAt(index) : '\0';
        }

        private void skipWhitespace() {
            while (index < source.length()) {
                char c = source.charAt(index);
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t') index++;
                else break;
            }
        }

        private boolean consume(char c) {
            if (peek() != c) return false;
            index++;
            return true;
        }

        private void expect(char c) {
            if (!consume(c)) throw new IllegalArgumentException("invalid tool arguments");
        }
    }
}
