package ink.tuanzi.neomacros.core.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON reader/writer for script-facing convenience APIs: parses to
 * maps/lists/strings/numbers/booleans/null and serializes the same shapes.
 * Not a general-purpose JSON toolkit — just enough for config-style files
 * and HTTP bodies, with no external dependencies.
 */
public final class JsonLite {

    private JsonLite() {
    }

    /** Parses one JSON document into neutral Java values. */
    public static Object parse(String json) {
        Parser parser = new Parser(json);
        Object value = parser.parseValue();
        parser.skipWhitespace();
        if (parser.position < json.length()) {
            throw new IllegalArgumentException("trailing content at " + parser.position);
        }
        return value;
    }

    /** Serializes neutral Java values into a JSON document. */
    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(sb, value);
        return sb.toString();
    }

    private static void writeValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String string) {
            writeString(sb, string);
        } else if (value instanceof Boolean || value instanceof Integer
                || value instanceof Long) {
            sb.append(value);
        } else if (value instanceof Number number) {
            double asDouble = number.doubleValue();
            if (Double.isNaN(asDouble) || Double.isInfinite(asDouble)) {
                sb.append("null");
            } else if (asDouble == Math.rint(asDouble)
                    && number instanceof Double && Math.abs(asDouble) < 1E15) {
                sb.append((long) asDouble);
            } else {
                sb.append(number);
            }
        } else if (value instanceof Map<?, ?> map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeString(sb, String.valueOf(entry.getKey()));
                sb.append(':');
                writeValue(sb, entry.getValue());
            }
            sb.append('}');
        } else if (value instanceof List<?> list) {
            sb.append('[');
            boolean first = true;
            for (Object element : list) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeValue(sb, element);
            }
            sb.append(']');
        } else {
            writeString(sb, String.valueOf(value));
        }
    }

    private static void writeString(StringBuilder sb, String string) {
        sb.append('"');
        for (int i = 0; i < string.length(); i++) {
            char c = string.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    private static final class Parser {
        private final String source;
        private int position;

        private Parser(String source) {
            this.source = source;
        }

        private void skipWhitespace() {
            while (position < source.length()
                    && Character.isWhitespace(source.charAt(position))) {
                position++;
            }
        }

        private char peek() {
            if (position >= source.length()) {
                throw new IllegalArgumentException("unexpected end of JSON");
            }
            return source.charAt(position);
        }

        private void expect(char c) {
            if (peek() != c) {
                throw new IllegalArgumentException("expected '" + c + "' at " + position);
            }
            position++;
        }

        private Object parseValue() {
            skipWhitespace();
            char c = peek();
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> parseNumber();
            };
        }

        private Object literal(String text, Object value) {
            if (!source.startsWith(text, position)) {
                throw new IllegalArgumentException("invalid literal at " + position);
            }
            position += text.length();
            return value;
        }

        private Map<String, Object> parseObject() {
            expect('{');
            Map<String, Object> out = new LinkedHashMap<>();
            skipWhitespace();
            if (peek() == '}') {
                position++;
                return out;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                out.put(key, parseValue());
                skipWhitespace();
                char c = peek();
                if (c == ',') {
                    position++;
                    continue;
                }
                expect('}');
                return out;
            }
        }

        private List<Object> parseArray() {
            expect('[');
            List<Object> out = new ArrayList<>();
            skipWhitespace();
            if (peek() == ']') {
                position++;
                return out;
            }
            while (true) {
                out.add(parseValue());
                skipWhitespace();
                char c = peek();
                if (c == ',') {
                    position++;
                    continue;
                }
                expect(']');
                return out;
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (position >= source.length()) {
                    throw new IllegalArgumentException("unterminated string");
                }
                char c = source.charAt(position++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                char escape = source.charAt(position++);
                switch (escape) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        sb.append((char) Integer.parseInt(
                                source.substring(position, position + 4), 16));
                        position += 4;
                    }
                    default -> throw new IllegalArgumentException(
                            "bad escape \\" + escape);
                }
            }
        }

        private Object parseNumber() {
            int start = position;
            while (position < source.length()
                    && "+-0123456789.eE".indexOf(source.charAt(position)) >= 0) {
                position++;
            }
            String text = source.substring(start, position);
            if (text.isEmpty()) {
                throw new IllegalArgumentException("invalid number at " + start);
            }
            if (text.indexOf('.') < 0 && text.indexOf('e') < 0 && text.indexOf('E') < 0) {
                return Long.parseLong(text);
            }
            return Double.parseDouble(text);
        }
    }
}
