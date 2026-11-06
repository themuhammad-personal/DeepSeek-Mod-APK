package com.themuhammad.deepseekmod.extension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the deliberately small, explicit SDS tool-call envelope.
 * This class only parses untrusted model text; it never executes a tool.
 */
public final class SdsToolCallParser {
    public static final int MAX_RESPONSE_CHARS = 256 * 1024;
    public static final int MAX_CALLS_PER_RESPONSE = 8;
    private static final int MAX_JSON_DEPTH = 24;

    private static final Pattern CALL = Pattern.compile(
            "<SDS:TOOL_CALL\\s+id=\"([A-Za-z0-9._:-]{1,128})\"\\s+name=\"([A-Za-z][A-Za-z0-9_.-]{0,63})\">(.*?)</SDS:TOOL_CALL>",
            Pattern.DOTALL);

    private SdsToolCallParser() { }

    public static List<SdsToolCall> parse(String response) {
        if (response == null || response.length() > MAX_RESPONSE_CHARS) {
            return Collections.emptyList();
        }

        List<SdsToolCall> calls = new ArrayList<>();
        Matcher matcher = CALL.matcher(response);
        while (matcher.find() && calls.size() < MAX_CALLS_PER_RESPONSE) {
            String id = matcher.group(1);
            String name = matcher.group(2);
            String arguments = matcher.group(3) == null ? "{}" : matcher.group(3).trim();
            if (arguments.length() > 64 * 1024 || !JsonObjectValidator.isValid(arguments)) {
                continue;
            }
            try {
                calls.add(new SdsToolCall(id, name, arguments));
            } catch (IllegalArgumentException ignored) {
                // Malformed model output is ignored, not promoted to executable state.
            }
        }
        return Collections.unmodifiableList(calls);
    }

    /** Small strict JSON validator: requires exactly one complete JSON object. */
    private static final class JsonObjectValidator {
        private final String source;
        private int index;

        private JsonObjectValidator(String source) {
            this.source = source;
        }

        static boolean isValid(String source) {
            if (source == null || source.isEmpty() || source.length() > 64 * 1024) return false;
            try {
                JsonObjectValidator parser = new JsonObjectValidator(source);
                parser.skipWhitespace();
                if (parser.peek() != '{') return false;
                parser.parseObject(0);
                parser.skipWhitespace();
                return parser.index == parser.source.length();
            } catch (IllegalArgumentException ignored) {
                return false;
            }
        }

        private void parseValue(int depth) {
            if (depth > MAX_JSON_DEPTH) fail();
            skipWhitespace();
            char c = peek();
            if (c == '{') parseObject(depth + 1);
            else if (c == '[') parseArray(depth + 1);
            else if (c == '"') parseString();
            else if (c == 't') parseLiteral("true");
            else if (c == 'f') parseLiteral("false");
            else if (c == 'n') parseLiteral("null");
            else parseNumber();
        }

        private void parseObject(int depth) {
            expect('{');
            skipWhitespace();
            if (consume('}')) return;
            while (true) {
                skipWhitespace();
                parseString();
                skipWhitespace();
                expect(':');
                parseValue(depth);
                skipWhitespace();
                if (consume('}')) return;
                expect(',');
            }
        }

        private void parseArray(int depth) {
            expect('[');
            skipWhitespace();
            if (consume(']')) return;
            while (true) {
                parseValue(depth);
                skipWhitespace();
                if (consume(']')) return;
                expect(',');
            }
        }

        private void parseString() {
            expect('"');
            while (index < source.length()) {
                char c = source.charAt(index++);
                if (c == '"') return;
                if (c < 0x20) fail();
                if (c == '\\') {
                    if (index >= source.length()) fail();
                    char escaped = source.charAt(index++);
                    if (escaped == 'u') {
                        for (int i = 0; i < 4; i++) {
                            if (index >= source.length() || Character.digit(source.charAt(index++), 16) < 0) fail();
                        }
                    } else if (escaped != '"' && escaped != '\\' && escaped != '/' && escaped != 'b'
                            && escaped != 'f' && escaped != 'n' && escaped != 'r' && escaped != 't') {
                        fail();
                    }
                }
            }
            fail();
        }

        private void parseNumber() {
            int start = index;
            consume('-');
            if (consume('0')) {
                if (isDigit(peek())) fail();
            } else {
                requireDigitOneToNine();
                while (isDigit(peek())) index++;
            }
            if (consume('.')) {
                requireDigit();
                while (isDigit(peek())) index++;
            }
            if (consume('e') || consume('E')) {
                if (!consume('+')) consume('-');
                requireDigit();
                while (isDigit(peek())) index++;
            }
            if (index == start) fail();
        }

        private void parseLiteral(String literal) {
            if (!source.startsWith(literal, index)) fail();
            index += literal.length();
        }

        private void requireDigitOneToNine() {
            char c = peek();
            if (c < '1' || c > '9') fail();
            index++;
        }

        private void requireDigit() {
            if (!isDigit(peek())) fail();
            index++;
        }

        private static boolean isDigit(char c) { return c >= '0' && c <= '9'; }
        private char peek() { return index < source.length() ? source.charAt(index) : '\0'; }
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
        private void expect(char c) { if (!consume(c)) fail(); }
        private static void fail() { throw new IllegalArgumentException("invalid JSON"); }
    }
}
