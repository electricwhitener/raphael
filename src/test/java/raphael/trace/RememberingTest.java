package raphael.trace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import raphael.compile.CompileResult;
import raphael.compile.SourceCompiler;
import raphael.io.ExampleCatalog;
import raphael.model.ArrayNode;
import raphael.model.FrameSnapshot;
import raphael.model.HeapObject;
import raphael.model.ObjectNode;
import raphael.model.ReferenceValue;
import raphael.model.Snapshot;
import raphael.model.Trace;
import raphael.model.Value;

// HeapReader remembers objects between steps and only reads again the ones whose fields were
// written. That must never change what is recorded: every program here is traced both ways,
// and every step must be the same.
class RememberingTest {

    // Things that could trip up remembering: a node unlinked, changed while nothing points to it,
    // then linked back; two variables on one object; an object changed by another's method;
    // objects inside an array; a field set to null and back.
    private static final String TRICKY = """
            class Node {
                int val;
                Node next;
                Node(int val) { this.val = val; }
                void bump(Node other) { other.val += 100; }
            }
            public class Main {
                public static void main(String[] args) {
                    Node a = new Node(1);
                    Node b = new Node(2);
                    Node c = new Node(3);
                    a.next = b;
                    b.next = c;
                    Node alias = b;
                    a.next = c;
                    b.val = 20;
                    alias.next = null;
                    a.next = b;
                    b.next = c;
                    a.bump(c);
                    Node[] slots = new Node[3];
                    slots[0] = a;
                    slots[1] = c;
                    slots[0].val = 10;
                    slots[2] = new Node(4);
                    c.next = slots[2];
                    slots[2].val = 40;
                }
            }
            """;

    static Stream<String> programs() throws Exception {
        List<String> names = new ArrayList<>(List.of("tricky"));
        for (ExampleCatalog.Example example : ExampleCatalog.all()) {
            if (!example.name().equals("MissingBaseCase")) { // 2,000 steps of the same call, twice: slow and adds nothing
                names.add("/examples/" + example.path() + ".java");
            }
        }
        try (Stream<Path> files = Files.list(folder("/edge"))) {
            // Left out: programs that sleep, don't compile, or run two threads (those interleave
            // differently on every run, and remembering is off for them anyway).
            files.map(p -> "/edge/" + p.getFileName()).filter(n -> !n.contains("Sleep") && !n.contains("Outlives")
                    && !n.contains("CompileError") && !n.contains("Threads")).sorted().forEach(names::add);
        }
        return names.stream();
    }

    private static Path folder(String resource) throws URISyntaxException {
        return Path.of(RememberingTest.class.getResource(resource).toURI());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("programs")
    void rememberingChangesNothing(String name) throws Exception {
        String source = name.equals("tricky") ? TRICKY
                : new String(getClass().getResourceAsStream(name).readAllBytes(), StandardCharsets.UTF_8);
        CompileResult program = new SourceCompiler().compile(source);
        String input = name.contains("Scanner") ? "1 2" : "";
        JdiTracer rereads = new JdiTracer();
        rereads.remember = false;
        List<String> expected = dump(rereads.trace(program, input));
        List<String> actual = dump(new JdiTracer().trace(program, input));
        assertEquals(expected.size(), actual.size(), "number of steps");
        for (int step = 0; step < expected.size(); step++) {
            assertEquals(expected.get(step), actual.get(step), "step " + (step + 1));
        }
    }

    // The point of remembering: building a 500-node list reads each node about once, not every
    // node at every step (that was about 280 reads per step, and 20 seconds for the run).
    @Test
    void aLongListIsReadAboutOnceANode() throws Exception {
        CompileResult program = new SourceCompiler().compile("""
                class Node {
                    int val;
                    Node next;
                    Node(int val) { this.val = val; }
                }
                public class Main {
                    public static void main(String[] args) {
                        Node head = null;
                        for (int i = 0; i < 500; i++) {
                            Node n = new Node(i);
                            n.next = head;
                            head = n;
                        }
                    }
                }
                """);
        JdiTracer tracer = new JdiTracer();
        Trace trace = tracer.trace(program, "");
        assertTrue(tracer.lastObjectsRead < 2 * trace.size(),
                tracer.lastObjectsRead + " objects read in " + trace.size() + " steps");
    }

    // Each step as text. Object ids are renumbered by first appearance, so two runs compare
    // equal even if JDI numbered their objects differently.
    private static List<String> dump(Trace trace) {
        Map<Long, Integer> ids = new HashMap<>();
        List<String> steps = new ArrayList<>();
        for (Snapshot step : trace.getSnapshots()) {
            StringBuilder text = new StringBuilder("line " + step.getLine() + "\n");
            for (FrameSnapshot frame : step.getFrames()) {
                text.append(frame.getMethodName()).append(':').append(frame.getLine());
                frame.getLocals().forEach((k, v) -> text.append(' ').append(k).append('=').append(show(v, ids)));
                text.append('\n');
            }
            step.getStatics().forEach((k, v) -> text.append(k).append('=').append(show(v, ids)).append('\n'));
            for (HeapObject object : step.getHeap().values()) {
                text.append('#').append(ids.computeIfAbsent(object.getId(), k -> ids.size())).append(' ').append(object.getTypeName());
                if (object instanceof ObjectNode) {
                    ((ObjectNode) object).getFields().forEach((k, v) -> text.append(' ').append(k).append('=').append(show(v, ids)));
                } else if (object instanceof ArrayNode) {
                    for (Value cell : ((ArrayNode) object).getElements()) {
                        text.append(' ').append(show(cell, ids));
                    }
                }
                text.append('\n');
            }
            steps.add(text.toString().replaceAll("/0x[0-9a-f]+", "")); // a lambda's class has a new address each run
        }
        return steps;
    }

    private static String show(Value value, Map<Long, Integer> ids) {
        return value instanceof ReferenceValue
                ? "#" + ids.computeIfAbsent(((ReferenceValue) value).id(), k -> ids.size())
                : value.display();
    }
}
