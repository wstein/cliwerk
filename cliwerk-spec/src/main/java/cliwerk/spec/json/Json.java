package cliwerk.spec.json;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A minimal, dependency-free JSON reader and writer.
 * <p>Parses into (and writes from) plain {@code java.util} types only:
 * {@link Map}{@code <String, Object>} for objects, {@link List}{@code <Object>} for arrays,
 * {@link String}, {@link Long}, {@link Double}, {@link Boolean}, and {@code null}.</p>
 * <p>This is intentionally not a general-purpose JSON library: it supports exactly the JSON
 * subset needed to read and write picocli command specs, kept dependency-free to match core
 * picocli's zero-dependency design.</p>
 */
public final class Json {

    private Json() {}

    /** Parses the given JSON text into a tree of {@code Map}/{@code List}/{@code String}/{@code Long}/{@code Double}/{@code Boolean}/{@code null}. */
    public static Object parse(String text) {
        Parser parser = new Parser(text);
        Object result = parser.parseValue();
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw new JsonParseException("Unexpected trailing content at position " + parser.pos);
        }
        return result;
    }

    /** Serializes the given value tree to pretty-printed JSON text (2-space indent). */
    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(value, sb, 0);
        return sb.toString();
    }

    private static void writeValue(Object value, StringBuilder sb, int indent) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String) {
            writeString((String) value, sb);
        } else if (value instanceof Boolean || value instanceof Number) {
            sb.append(value);
        } else if (value instanceof Map) {
            writeObject((Map<?, ?>) value, sb, indent);
        } else if (value instanceof List) {
            writeArray((List<?>) value, sb, indent);
        } else {
            throw new IllegalArgumentException("Cannot write value of type " + value.getClass().getName() + " as JSON");
        }
    }

    private static void writeObject(Map<?, ?> map, StringBuilder sb, int indent) {
        if (map.isEmpty()) {
            sb.append("{}");
            return;
        }
        sb.append("{\n");
        int i = 0;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            indent(sb, indent + 1);
            writeString(String.valueOf(entry.getKey()), sb);
            sb.append(": ");
            writeValue(entry.getValue(), sb, indent + 1);
            if (++i < map.size()) { sb.append(','); }
            sb.append('\n');
        }
        indent(sb, indent);
        sb.append('}');
    }

    private static void writeArray(List<?> list, StringBuilder sb, int indent) {
        if (list.isEmpty()) {
            sb.append("[]");
            return;
        }
        sb.append("[\n");
        for (int i = 0; i < list.size(); i++) {
            indent(sb, indent + 1);
            writeValue(list.get(i), sb, indent + 1);
            if (i < list.size() - 1) { sb.append(','); }
            sb.append('\n');
        }
        indent(sb, indent);
        sb.append(']');
    }

    private static void indent(StringBuilder sb, int level) {
        for (int i = 0; i < level; i++) { sb.append("  "); }
    }

    private static void writeString(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    private static final class Parser {
        private final String text;
        private int pos;

        Parser(String text) {
            this.text = text;
            this.pos = 0;
        }

        boolean atEnd() { return pos >= text.length(); }

        char peek() {
            if (atEnd()) { throw new JsonParseException("Unexpected end of input"); }
            return text.charAt(pos);
        }

        void skipWhitespace() {
            while (!atEnd() && Character.isWhitespace(text.charAt(pos))) { pos++; }
        }

        Object parseValue() {
            skipWhitespace();
            char c = peek();
            switch (c) {
                case '{': return parseObject();
                case '[': return parseArray();
                case '"': return parseString();
                case 't': return parseLiteral("true", Boolean.TRUE);
                case 'f': return parseLiteral("false", Boolean.FALSE);
                case 'n': return parseLiteral("null", null);
                default:
                    if (c == '-' || Character.isDigit(c)) { return parseNumber(); }
                    throw new JsonParseException("Unexpected character '" + c + "' at position " + pos);
            }
        }

        Map<String, Object> parseObject() {
            expect('{');
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            skipWhitespace();
            if (peekChar() == '}') { pos++; return result; }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                Object value = parseValue();
                result.put(key, value);
                skipWhitespace();
                char next = nextChar();
                if (next == '}') { break; }
                if (next != ',') { throw new JsonParseException("Expected ',' or '}' at position " + (pos - 1)); }
            }
            return result;
        }

        List<Object> parseArray() {
            expect('[');
            java.util.ArrayList<Object> result = new java.util.ArrayList<Object>();
            skipWhitespace();
            if (peekChar() == ']') { pos++; return result; }
            while (true) {
                Object value = parseValue();
                result.add(value);
                skipWhitespace();
                char next = nextChar();
                if (next == ']') { break; }
                if (next != ',') { throw new JsonParseException("Expected ',' or ']' at position " + (pos - 1)); }
            }
            return result;
        }

        String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = nextChar();
                if (c == '"') { break; }
                if (c == '\\') {
                    char esc = nextChar();
                    switch (esc) {
                        case '"':  sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/':  sb.append('/'); break;
                        case 'n':  sb.append('\n'); break;
                        case 'r':  sb.append('\r'); break;
                        case 't':  sb.append('\t'); break;
                        case 'b':  sb.append('\b'); break;
                        case 'f':  sb.append('\f'); break;
                        case 'u':
                            if (pos + 4 > text.length()) { throw new JsonParseException("Truncated unicode escape at position " + pos); }
                            String hex = text.substring(pos, pos + 4);
                            pos += 4;
                            sb.append((char) Integer.parseInt(hex, 16));
                            break;
                        default:
                            throw new JsonParseException("Invalid escape sequence '\\" + esc + "' at position " + (pos - 1));
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        Object parseNumber() {
            int start = pos;
            if (peekChar() == '-') { pos++; }
            while (!atEnd() && Character.isDigit(peekChar())) { pos++; }
            boolean isFloatingPoint = false;
            if (!atEnd() && peekChar() == '.') {
                isFloatingPoint = true;
                pos++;
                while (!atEnd() && Character.isDigit(peekChar())) { pos++; }
            }
            if (!atEnd() && (peekChar() == 'e' || peekChar() == 'E')) {
                isFloatingPoint = true;
                pos++;
                if (!atEnd() && (peekChar() == '+' || peekChar() == '-')) { pos++; }
                while (!atEnd() && Character.isDigit(peekChar())) { pos++; }
            }
            String number = text.substring(start, pos);
            if (number.isEmpty() || "-".equals(number)) {
                throw new JsonParseException("Invalid number at position " + start);
            }
            return isFloatingPoint ? (Object) Double.parseDouble(number) : (Object) Long.parseLong(number);
        }

        Object parseLiteral(String literal, Object value) {
            if (pos + literal.length() > text.length() || !text.startsWith(literal, pos)) {
                throw new JsonParseException("Expected '" + literal + "' at position " + pos);
            }
            pos += literal.length();
            return value;
        }

        char peekChar() { return peek(); }

        char nextChar() {
            char c = peek();
            pos++;
            return c;
        }

        void expect(char expected) {
            char actual = nextChar();
            if (actual != expected) {
                throw new JsonParseException("Expected '" + expected + "' but found '" + actual + "' at position " + (pos - 1));
            }
        }
    }
}
