package raphael.trace;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Reads one JSON text, as the tracers written in Python (the gdb helper for C) send them, into
// plain Java: a Map (keys in order), a List, a String, a Long or Double, a Boolean, or null.
// Small on purpose: the helpers' JSON comes from Python's own json module, so it's always well
// formed, and a library isn't worth adding for it.
// OOP: encapsulation (the text and the position are private; read() is the one way in).
public final class Json {

    private final String text;
    private int at;

    private Json(String text) {
        this.text = text;
    }

    public static Object read(String text) {
        Json json = new Json(text);
        Object value = json.value();
        json.space();
        if (json.at != text.length()) {
            throw json.error("more text after the end");
        }
        return value;
    }

    private Object value() {
        space();
        if (at >= text.length()) {
            throw error("the text ended early");
        }
        char c = text.charAt(at);
        switch (c) {
            case '{':
                return object();
            case '[':
                return array();
            case '"':
                return string();
            case 't':
                return word("true", Boolean.TRUE);
            case 'f':
                return word("false", Boolean.FALSE);
            case 'n':
                return word("null", null);
            default:
                return number();
        }
    }

    private Map<String, Object> object() {
        Map<String, Object> map = new LinkedHashMap<>();
        at++; // {
        space();
        if (peek() == '}') {
            at++;
            return map;
        }
        while (true) {
            space();
            String key = string();
            space();
            expect(':');
            map.put(key, value());
            space();
            if (peek() == ',') {
                at++;
            } else {
                expect('}');
                return map;
            }
        }
    }

    private List<Object> array() {
        List<Object> list = new ArrayList<>();
        at++; // [
        space();
        if (peek() == ']') {
            at++;
            return list;
        }
        while (true) {
            list.add(value());
            space();
            if (peek() == ',') {
                at++;
            } else {
                expect(']');
                return list;
            }
        }
    }

    private String string() {
        expect('"');
        StringBuilder s = new StringBuilder();
        while (true) {
            if (at >= text.length()) {
                throw error("a string didn't end");
            }
            char c = text.charAt(at++);
            if (c == '"') {
                return s.toString();
            }
            if (c != '\\') {
                s.append(c);
                continue;
            }
            char e = text.charAt(at++);
            switch (e) {
                case 'n' -> s.append('\n');
                case 't' -> s.append('\t');
                case 'r' -> s.append('\r');
                case 'b' -> s.append('\b');
                case 'f' -> s.append('\f');
                case 'u' -> {
                    s.append((char) Integer.parseInt(text.substring(at, at + 4), 16));
                    at += 4;
                }
                default -> s.append(e); // \" \\ \/
            }
        }
    }

    private Object number() {
        int start = at;
        while (at < text.length() && "+-0123456789.eE".indexOf(text.charAt(at)) >= 0) {
            at++;
        }
        String number = text.substring(start, at);
        if (number.isEmpty()) {
            throw error("unexpected '" + text.charAt(at) + "'");
        }
        return number.contains(".") || number.contains("e") || number.contains("E")
                ? (Object) Double.parseDouble(number) : (Object) Long.parseLong(number);
    }

    private Object word(String word, Object value) {
        if (!text.startsWith(word, at)) {
            throw error("unexpected '" + text.charAt(at) + "'");
        }
        at += word.length();
        return value;
    }

    private void space() {
        while (at < text.length() && Character.isWhitespace(text.charAt(at))) {
            at++;
        }
    }

    private char peek() {
        return at < text.length() ? text.charAt(at) : '\0';
    }

    private void expect(char c) {
        if (peek() != c) {
            throw error("expected '" + c + "'");
        }
        at++;
    }

    private IllegalArgumentException error(String what) {
        return new IllegalArgumentException("Bad JSON at " + at + ": " + what);
    }
}
