package raphael.trace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

// The small JSON reader the C tracer uses for gdb's lines.
class JsonTest {

    @Test
    void readsWhatPythonWrites() {
        Object read = Json.read("{\"kind\": \"step\", \"n\": -12, \"x\": 2.5e1, \"ok\": true, \"no\": false, \"none\": null,"
                + " \"list\": [1, [], {}], \"text\": \"a \\\"q\\\" \\\\ \\n \\u00e9\"}");
        Map<String, Object> expected = new java.util.LinkedHashMap<>();
        expected.put("kind", "step");
        expected.put("n", -12L);
        expected.put("x", 25.0);
        expected.put("ok", true);
        expected.put("no", false);
        expected.put("none", null);
        expected.put("list", List.of(1L, List.of(), Map.of()));
        expected.put("text", "a \"q\" \\ \n é");
        assertEquals(expected, read);
        assertEquals(List.copyOf(expected.keySet()), List.copyOf(((Map<?, ?>) read).keySet())); // in order
    }

    @Test
    void badTextIsAnError() {
        for (String bad : Arrays.asList("{\"a\": }", "[1, 2", "\"open", "{} x")) {
            assertThrows(IllegalArgumentException.class, () -> Json.read(bad), bad);
        }
    }
}
