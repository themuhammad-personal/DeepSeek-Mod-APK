package com.themuhammad.deepseekmod.extension;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A strict and bounded JSON reader.
 *
 * The tool arguments use the flat reader {@link SdsToolArguments}, which only
 * accepts one level of strings and numbers. The protocols that talk to the
 * outside world, like JSON-RPC, need the nested form, so this reader exists.
 *
 * It is strict on purpose: no trailing data, no unquoted keys, no comments, no
 * duplicate keys, a depth limit, a size limit and a limit on the number of
 * entries in a container. A document that breaks one of these rules is
 * refused instead of being guessed.
 */
public final class SdsJson {
    public static final int MAX_TEXT_CHARS = 256 * 1024;
    public static final int MAX_DEPTH = 16;
    public static final int MAX_CONTAINER_ENTRIES = 1024;
    public static final int MAX_STRING_CHARS = 64 * 1024;

    private final String text;
    private int index;

    private SdsJson(String text) {
        this.text = text;
    }

    /** Parses a JSON document. */
    public static Object parse(String text) {
        if (text == null) throw new IllegalArgumentException("the document is required");
        if (text.length() > MAX_TEXT_CHARS) {
            throw new IllegalArgumentException("the document is too long");
        }
        SdsJson reader = new SdsJson(text);
        reader.skipWhitespace();
        Object value = reader.readValue(0);
        reader.skipWhitespace();
        if (reader.index != text.length()) {
            throw new IllegalArgumentException("the document has trailing data");
        }
        return value;
    }

    /** Parses a document that has to be an object. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object value = parse(text);
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("the document is not an object");
        }
        return (Map<String, Object>) value;
    }

    /** Parses a document that has to be an array. */
    @SuppressWarnings("unchecked")
    public static List<Object> parseArray(String text) {
        Object value = parse(text);
        if (!(value instanceof List)) {
            throw new IllegalArgumentException("the document is not an array");
        }
        return (List<Object>) value;
    }

    private Object readValue(int depth) {
        if (depth > MAX_DEPTH) throw new IllegalArgumentException("the document is too deep");
        skipWhitespace();
        if (index >= text.length()) throw new IllegalArgumentException("the document ended early");
        char c = text.charAt(index);
        switch (c) {
            case '{':
                return readObject(depth);
            case '[':
                return readArray(depth);
            case '"':
                return readString();
            case 't':
                expect("true");
                return Boolean.TRUE;
            case 'f':
                expect("false");
                return Boolean.FALSE;
            case 'n':
                expect("null");
                return null;
            default:
                return readNumber();
        }
    }

    private Map<String, Object> readObject(int depth) {
        Map<String, Object> object = new LinkedHashMap<>();
        index++; // the opening brace
        skipWhitespace();
        if (index < text.length() && text.charAt(index) == '}') {
            index++;
            return object;
        }
        while (true) {
            skipWhitespace();
            if (index >= text.length() || text.charAt(index) != '"') {
                throw new IllegalArgumentException("the key of an object has to be a string");
            }
            String key = readString();
            if (object.containsKey(key)) {
                throw new IllegalArgumentException("the object has a duplicate key");
            }
            skipWhitespace();
            if (index >= text.length() || text.charAt(index) != ':') {
                throw new IllegalArgumentException("a key has to be followed by a colon");
            }
            index++;
            object.put(key, readValue(depth + 1));
            if (object.size() > MAX_CONTAINER_ENTRIES) {
                throw new IllegalArgumentException("the object has too many entries");
            }
            skipWhitespace();
            if (index >= text.length()) throw new IllegalArgumentException("the object is not closed");
            char c = text.charAt(index++);
            if (c == '}') return object;
            if (c != ',') throw new IllegalArgumentException("the object is not closed");
        }
    }

    private List<Object> readArray(int depth) {
        List<Object> array = new ArrayList<>();
        index++; // the opening bracket
        skipWhitespace();
        if (index < text.length() && text.charAt(index) == ']') {
            index++;
            return array;
        }
        while (true) {
            array.add(readValue(depth + 1));
            if (array.size() > MAX_CONTAINER_ENTRIES) {
                throw new IllegalArgumentException("the array has too many entries");
            }
            skipWhitespace();
            if (index >= text.length()) throw new IllegalArgumentException("the array is not closed");
            char c = text.charAt(index++);
            if (c == ']') return array;
            if (c != ',') throw new IllegalArgumentException("the array is not closed");
        }
    }

    private String readString() {
        index++; // the opening quote
        StringBuilder builder = new StringBuilder();
        while (true) {
            if (index >= text.length()) throw new IllegalArgumentException("the string is not closed");
            char c = text.charAt(index++);
            if (c == '"') return builder.toString();
            if (c == '\\') {
                if (index >= text.length()) throw new IllegalArgumentException("the escape is broken");
                char escape = text.charAt(index++);
                switch (escape) {
                    case '"': builder.append('"'); break;
                    case '\\': builder.append('\\'); break;
                    case '/': builder.append('/'); break;
                    case 'b': builder.append('\b'); break;
                    case 'f': builder.append('\f'); break;
                    case 'n': builder.append('\n'); break;
                    case 'r': builder.append('\r'); break;
                    case 't': builder.append('\t'); break;
                    case 'u':
                        if (index + 4 > text.length()) {
                            throw new IllegalArgumentException("the escape is broken");
                        }
                        String hex = text.substring(index, index + 4);
                        try {
                            builder.append((char) Integer.parseInt(hex, 16));
                        } catch (NumberFormatException refused) {
                            throw new IllegalArgumentException("the escape is broken");
                        }
                        index += 4;
                        break;
                    default:
                        throw new IllegalArgumentException("unknown escape");
                }
            } else if (c < 0x20) {
                throw new IllegalArgumentException("the string holds a control character");
            } else {
                builder.append(c);
            }
            if (builder.length() > MAX_STRING_CHARS) {
                throw new IllegalArgumentException("the string is too long");
            }
        }
    }

    private Object readNumber() {
        int start = index;
        if (index < text.length() && text.charAt(index) == '-') index++;
        while (index < text.length() && Character.isDigit(text.charAt(index))) index++;
        boolean floating = false;
        if (index < text.length() && text.charAt(index) == '.') {
            floating = true;
            index++;
            while (index < text.length() && Character.isDigit(text.charAt(index))) index++;
        }
        if (index < text.length() && (text.charAt(index) == 'e' || text.charAt(index) == 'E')) {
            floating = true;
            index++;
            if (index < text.length() && (text.charAt(index) == '+' || text.charAt(index) == '-')) {
                index++;
            }
            while (index < text.length() && Character.isDigit(text.charAt(index))) index++;
        }
        String number = text.substring(start, index);
        if (number.isEmpty() || number.equals("-")) {
            throw new IllegalArgumentException("the value is not a number");
        }
        try {
            if (floating) return Double.valueOf(number);
            return Long.valueOf(number);
        } catch (NumberFormatException refused) {
            throw new IllegalArgumentException("the value is not a number");
        }
    }

    private void expect(String literal) {
        if (!text.startsWith(literal, index)) {
            throw new IllegalArgumentException("the value is not " + literal);
        }
        index += literal.length();
    }

    private void skipWhitespace() {
        while (index < text.length()) {
            char c = text.charAt(index);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') index++;
            else break;
        }
    }

    // --- typed helpers -----------------------------------------------------

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asObject(Object value) {
        if (value instanceof Map) return (Map<String, Object>) value;
        return null;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> asArray(Object value) {
        if (value instanceof List) return (List<Object>) value;
        return null;
    }

    public static String asString(Object value) {
        if (value instanceof String) return (String) value;
        return null;
    }

    public static Long asLong(Object value) {
        if (value instanceof Long) return (Long) value;
        if (value instanceof Double) return ((Double) value).longValue();
        return null;
    }

    /** Reads a string entry, or null when it is missing or of another type. */
    public static String optString(Map<String, Object> object, String key) {
        if (object == null) return null;
        return asString(object.get(key));
    }

    /** Reads an object entry, or null when it is missing or of another type. */
    public static Map<String, Object> optObject(Map<String, Object> object, String key) {
        if (object == null) return null;
        return asObject(object.get(key));
    }

    /** Reads an array entry, or null when it is missing or of another type. */
    public static List<Object> optArray(Map<String, Object> object, String key) {
        if (object == null) return null;
        return asArray(object.get(key));
    }
}
