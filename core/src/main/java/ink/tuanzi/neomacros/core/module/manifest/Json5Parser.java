package ink.tuanzi.neomacros.core.module.manifest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal recursive-descent JSON5 reader producing plain Java values (maps,
 * lists, strings, numbers, booleans, null). Supports the JSON5 surface that
 * matters for hand-written manifests: line and block comments, single-quoted
 * strings, unquoted identifier keys, trailing commas, hexadecimal numbers,
 * leading/trailing decimal points, and the {@code Infinity}/{@code NaN}
 * literals. Escaped identifier keys are not supported.
 */
public final class Json5Parser {

    /** Thrown on malformed input; the message carries the line and column. */
    public static final class Json5SyntaxException extends RuntimeException {
        Json5SyntaxException(String message, int position) {
            super(message + " (at " + (position + 1) + ")");
        }
    }

    private final String text;
    private int pos;

    private Json5Parser(String text) {
        this.text = text;
    }

    /** Parses a JSON5 document whose top-level value is an object. */
    public static Map<String, Object> parseObject(String text) {
        Json5Parser parser = new Json5Parser(text);
        Object value = parser.parseValue();
        parser.skipWhitespace();
        if (parser.pos < parser.text.length()) {
            throw new Json5SyntaxException("unexpected trailing input", parser.pos);
        }
        if (!(value instanceof Map)) {
            throw new Json5SyntaxException("top-level value must be an object", 0);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> object = (Map<String, Object>) value;
        return object;
    }

    /** Parses any JSON5 value (scalars included); exposed for tests. */
    public static Object parseValue(String text) {
        Json5Parser parser = new Json5Parser(text);
        Object value = parser.parseValue();
        parser.skipWhitespace();
        if (parser.pos < parser.text.length()) {
            throw new Json5SyntaxException("unexpected trailing input", parser.pos);
        }
        return value;
    }

    private Object parseValue() {
        skipWhitespaceAndComments();
        if (pos >= text.length()) {
            throw new Json5SyntaxException("unexpected end of input", pos);
        }
        char c = text.charAt(pos);
        switch (c) {
            case '{':
                return parseObject();
            case '[':
                return parseArray();
            case '"':
            case '\'':
                return parseString();
            default:
                return parseLiteral();
        }
    }

    private Map<String, Object> parseObject() {
        expect('{');
        Map<String, Object> object = new LinkedHashMap<>();
        skipWhitespaceAndComments();
        if (peek() == '}') {
            pos++;
            return object;
        }
        while (true) {
            skipWhitespaceAndComments();
            String key = parseKey();
            skipWhitespaceAndComments();
            expect(':');
            object.put(key, parseValue());
            skipWhitespaceAndComments();
            char c = peek();
            if (c == ',') {
                pos++;
                // A trailing comma may be followed directly by the closing brace.
                skipWhitespaceAndComments();
                if (peek() == '}') {
                    pos++;
                    return object;
                }
                continue;
            }
            if (c == '}') {
                pos++;
                return object;
            }
            throw new Json5SyntaxException("expected ',' or '}' in object", pos);
        }
    }

    private List<Object> parseArray() {
        expect('[');
        List<Object> array = new ArrayList<>();
        skipWhitespaceAndComments();
        if (peek() == ']') {
            pos++;
            return array;
        }
        while (true) {
            array.add(parseValue());
            skipWhitespaceAndComments();
            char c = peek();
            if (c == ',') {
                pos++;
                skipWhitespaceAndComments();
                if (peek() == ']') {
                    pos++;
                    return array;
                }
                continue;
            }
            if (c == ']') {
                pos++;
                return array;
            }
            throw new Json5SyntaxException("expected ',' or ']' in array", pos);
        }
    }

    private String parseKey() {
        char c = peek();
        if (c == '"' || c == '\'') {
            return parseString();
        }
        if (!Character.isJavaIdentifierStart(c)) {
            throw new Json5SyntaxException("invalid object key", pos);
        }
        int start = pos;
        while (pos < text.length() && Character.isJavaIdentifierPart(text.charAt(pos))) {
            pos++;
        }
        return text.substring(start, pos);
    }

    private String parseString() {
        char quote = text.charAt(pos);
        pos++;
        StringBuilder out = new StringBuilder();
        while (true) {
            if (pos >= text.length()) {
                throw new Json5SyntaxException("unterminated string", pos);
            }
            char c = text.charAt(pos++);
            if (c == quote) {
                return out.toString();
            }
            if (c != '\\') {
                out.append(c);
                continue;
            }
            if (pos >= text.length()) {
                throw new Json5SyntaxException("unterminated escape", pos);
            }
            char escaped = text.charAt(pos++);
            switch (escaped) {
                case 'b':
                    out.append('\b');
                    break;
                case 'f':
                    out.append('\f');
                    break;
                case 'n':
                    out.append('\n');
                    break;
                case 'r':
                    out.append('\r');
                    break;
                case 't':
                    out.append('\t');
                    break;
                case 'v':
                    out.append('\u000B');
                    break;
                case '0':
                    out.append('\u0000');
                    break;
                case 'x':
                    out.append(hexEscape(2));
                    break;
                case 'u':
                    out.append(hexEscape(4));
                    break;
                case '\n':
                    // Line continuation: the newline and following indentation vanish.
                    while (pos < text.length() && isWhitespace(text.charAt(pos))) {
                        pos++;
                    }
                    break;
                case '\r':
                    if (pos < text.length() && text.charAt(pos) == '\n') {
                        pos++;
                    }
                    while (pos < text.length() && isWhitespace(text.charAt(pos))) {
                        pos++;
                    }
                    break;
                default:
                    out.append(escaped);
            }
        }
    }

    private char hexEscape(int digits) {
        if (pos + digits > text.length()) {
            throw new Json5SyntaxException("truncated \\x/\\u escape", pos);
        }
        int value = 0;
        for (int i = 0; i < digits; i++) {
            char c = text.charAt(pos++);
            int digit = Character.digit(c, 16);
            if (digit < 0) {
                throw new Json5SyntaxException("invalid hex digit '" + c + "'", pos - 1);
            }
            value = (value << 4) | digit;
        }
        return (char) value;
    }

    private Object parseLiteral() {
        int start = pos;
        if (match("true")) {
            return Boolean.TRUE;
        }
        if (match("false")) {
            return Boolean.FALSE;
        }
        if (match("null")) {
            return null;
        }
        if (match("Infinity")) {
            return Double.POSITIVE_INFINITY;
        }
        if (match("NaN")) {
            return Double.NaN;
        }
        return parseNumber(start);
    }

    private Object parseNumber(int start) {
        if (peek() == '0' && pos + 1 < text.length()
                && (text.charAt(pos + 1) == 'x' || text.charAt(pos + 1) == 'X')) {
            pos += 2;
            int hexStart = pos;
            while (pos < text.length() && Character.digit(text.charAt(pos), 16) >= 0) {
                pos++;
            }
            if (pos == hexStart) {
                throw new Json5SyntaxException("invalid hexadecimal number", start);
            }
            return (double) Long.parseLong(text.substring(hexStart, pos), 16);
        }
        if (peek() == '+' || peek() == '-') {
            boolean negative = text.charAt(pos) == '-';
            pos++;
            if (match("Infinity")) {
                return negative ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
            }
        }
        int end = pos;
        boolean sawDigit = false;
        boolean sawDot = false;
        boolean sawExponent = false;
        if (end < text.length() && text.charAt(end) == '.') {
            sawDot = true; // leading-dot form (.5)
            end++;
        }
        while (end < text.length()) {
            char c = text.charAt(end);
            if (Character.isDigit(c)) {
                sawDigit = true;
            } else if (c == '.') {
                if (sawDot || sawExponent) {
                    break;
                }
                sawDot = true;
            } else if (c == '_') {
                // digit separator; dropped before parsing
            } else if (c == 'e' || c == 'E') {
                if (sawExponent) {
                    break;
                }
                sawExponent = true;
            } else if (c == '+' || c == '-') {
                char previous = end > 0 ? text.charAt(end - 1) : '\0';
                if (previous != 'e' && previous != 'E') {
                    break;
                }
            } else {
                break;
            }
            end++;
        }
        if (!sawDigit) {
            throw new Json5SyntaxException("invalid literal", start);
        }
        pos = end;
        String literal = text.substring(start, pos).replace("_", "");
        try {
            return Double.parseDouble(literal);
        } catch (NumberFormatException e) {
            throw new Json5SyntaxException("invalid number '" + literal + "'", start);
        }
    }

    private void skipWhitespaceAndComments() {
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (isWhitespace(c)) {
                pos++;
            } else if (c == '/' && pos + 1 < text.length() && text.charAt(pos + 1) == '/') {
                while (pos < text.length() && text.charAt(pos) != '\n') {
                    pos++;
                }
            } else if (c == '/' && pos + 1 < text.length() && text.charAt(pos + 1) == '*') {
                int end = text.indexOf("*/", pos + 2);
                if (end < 0) {
                    throw new Json5SyntaxException("unterminated block comment", pos);
                }
                pos = end + 2;
            } else {
                return;
            }
        }
    }

    private void skipWhitespace() {
        while (pos < text.length() && isWhitespace(text.charAt(pos))) {
            pos++;
        }
    }

    private static boolean isWhitespace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\u00A0' || c == '\uFEFF';
    }

    private boolean match(String word) {
        if (text.startsWith(word, pos)) {
            // Guard against a longer identifier absorbing a keyword prefix.
            int end = pos + word.length();
            if (end >= text.length() || !Character.isJavaIdentifierPart(text.charAt(end))) {
                pos = end;
                return true;
            }
        }
        return false;
    }

    private void expect(char c) {
        skipWhitespaceAndComments();
        if (pos >= text.length() || text.charAt(pos) != c) {
            throw new Json5SyntaxException("expected '" + c + "'", pos);
        }
        pos++;
    }

    private char peek() {
        if (pos >= text.length()) {
            throw new Json5SyntaxException("unexpected end of input", pos);
        }
        return text.charAt(pos);
    }
}
