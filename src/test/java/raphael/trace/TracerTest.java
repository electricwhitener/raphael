package raphael.trace;

import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import raphael.compile.CompileResult;
import raphael.compile.SourceCompiler;
import raphael.model.ArrayNode;
import raphael.model.EndReason;
import raphael.model.HeapObject;
import raphael.model.ReferenceValue;
import raphael.model.Snapshot;
import raphael.model.Trace;
import raphael.model.Value;

class TracerTest {

    private final Tracer tracer = new JdiTracer();

    private Trace run(String source, String input) throws Exception {
        CompileResult program = new SourceCompiler().compile(source);
        assertTrue(program.success(), () -> "didn't compile: " + program.errors());
        return tracer.trace(program, input);
    }

    private static List<Integer> lines(Trace trace) {
        List<Integer> lines = new ArrayList<>();
        for (Snapshot s : trace.getSnapshots()) {
            lines.add(s.getLine());
        }
        return lines;
    }

    // The program runs in a folder of its own: a file it writes is recorded step by step, a file
    // the user added is there for it to read, and the folder is gone after the run.
    // Several programs starting at once (one per tab) each get their own connection. JDI keeps one
    // listener per set of arguments, and every run's are the same, so without a lock in launch()
    // one program's clean-up closed another's listener ("Not listening").
    @Test
    void programsStartedAtTheSameTimeAllRun() throws Exception {
        CompileResult program = new SourceCompiler().compile("""
                public class Hello {
                    public static void main(String[] args) {
                        System.out.println("hi");
                    }
                }
                """);
        List<Thread> threads = new ArrayList<>();
        List<String> outputs = java.util.Collections.synchronizedList(new ArrayList<>());
        List<Throwable> failures = java.util.Collections.synchronizedList(new ArrayList<>());
        for (int i = 0; i < 4; i++) {
            Thread thread = new Thread(() -> {
                try {
                    outputs.add(new JdiTracer().trace(program).getOutput().replace("\r\n", "\n")); // Windows ends lines with \r\n
                } catch (Throwable e) {
                    failures.add(e);
                }
            });
            threads.add(thread);
            thread.start();
        }
        for (Thread thread : threads) {
            thread.join(60_000);
        }
        assertEquals(List.of(), failures);
        assertEquals(List.of("hi\n", "hi\n", "hi\n", "hi\n"), outputs);
    }

    @Test
    void theProgramsFilesAreRecordedInItsOwnFolder(@org.junit.jupiter.api.io.TempDir Path somewhere) throws Exception {
        Path input = somewhere.resolve("names.txt");
        java.nio.file.Files.writeString(input, "Ada\nAlan\n");
        tracer.setInputFiles(List.of(input));
        try {
            Trace trace = run("""
                    import java.io.*;
                    import java.nio.file.*;
                    public class Copy {
                        public static void main(String[] args) throws IOException {
                            System.out.println(Path.of("").toAbsolutePath());
                            java.util.List<String> names = Files.readAllLines(Path.of("names.txt"));
                            try (PrintWriter out = new PrintWriter("out.txt")) {
                                out.println(names.size());
                            }
                            int done = 1;
                        }
                    }
                    """, "");
            assertEquals(EndReason.FINISHED, trace.getEndReason());
            Path folder = Path.of(trace.getOutput().lines().findFirst().orElseThrow());
            assertTrue(folder.getFileName().toString().startsWith("raphael-run-"), "runs in its own folder: " + folder);
            assertFalse(java.nio.file.Files.exists(folder), "the folder is deleted after the run");
            assertEquals(Map.of("names.txt", "Ada\nAlan\n"), trace.get(0).getFiles());
            assertEquals("2\n", trace.get(trace.size() - 1).getFiles().get("out.txt").replace("\r\n", "\n"));
            // Steps where nothing changed share one map.
            assertSame(trace.get(0).getFiles(), trace.get(1).getFiles());
        } finally {
            tracer.setInputFiles(List.of());
        }
    }

    @Test
    void followsALoopLineByLine() throws Exception {
        Trace trace = run("""
                public class Loop {
                    public static void main(String[] args) {
                        int sum = 0;
                        for (int i = 0; i < 3; i++) {
                            sum += i;
                        }
                        System.out.println(sum);
                    }
                }
                """, "");

        assertEquals(List.of(3, 4, 5, 4, 5, 4, 5, 4, 7, 8), lines(trace));
        assertEquals(EndReason.FINISHED, trace.getEndReason());
        assertEquals("3", trace.getOutput().trim());
        // Before line 7 runs, nothing has been printed yet.
        assertEquals("", trace.getOutputAt(lines(trace).indexOf(7)));
        // The last time line 4 runs, the loop has added 0 + 1 + 2.
        Snapshot lastLoopCheck = trace.get(lines(trace).lastIndexOf(4));
        assertEquals("3", lastLoopCheck.getFrames().get(0).getLocals().get("sum").display());
    }

    @Test
    void infiniteLoopStopsAtTheLimit() throws Exception {
        Trace trace = run("""
                public class Forever {
                    public static void main(String[] args) {
                        int x = 0;
                        while (true) {
                            x++;
                        }
                    }
                }
                """, "");

        assertEquals(TraceLimits.MAX_STEPS, trace.size());
        assertEquals(EndReason.STEP_LIMIT, trace.getEndReason());
    }

    @Test
    void uncaughtExceptionIsTheLastStep() throws Exception {
        Trace trace = run("""
                public class Crash {
                    public static void main(String[] args) {
                        int[] arr = new int[3];
                        arr[5] = 1;
                    }
                }
                """, "");

        Snapshot last = trace.get(trace.size() - 1);
        assertEquals(EndReason.EXCEPTION, trace.getEndReason());
        assertEquals(4, last.getLine());
        assertEquals("ArrayIndexOutOfBoundsException: Index 5 out of bounds for length 3", last.getException());
    }

    @Test
    void scannerReadsTheInputBox() throws Exception {
        Trace trace = run("""
                import java.util.Scanner;
                public class Echo {
                    public static void main(String[] args) {
                        Scanner sc = new Scanner(System.in);
                        int n = sc.nextInt();
                        System.out.println(n * 2);
                    }
                }
                """, "21\n");

        assertEquals(EndReason.FINISHED, trace.getEndReason());
        assertEquals("42", trace.getOutput().trim());
        Snapshot last = trace.get(trace.size() - 1);
        assertEquals("21", last.getFrames().get(0).getLocals().get("n").display());
        assertEquals("Scanner", last.getFrames().get(0).getLocals().get("sc").display());
    }

    @Test
    void capturesALinkedList() throws Exception {
        Trace trace = run("""
                public class ListDemo {
                    static class Node {
                        int val;
                        Node next;
                        Node(int val) { this.val = val; }
                    }
                    public static void main(String[] args) {
                        Node head = new Node(5);
                        head.next = new Node(7);
                        System.out.println(head.val);
                    }
                }
                """, "");

        // The constructor runs as its own method, on top of main.
        assertTrue(trace.getSnapshots().stream()
                .anyMatch(s -> s.getFrames().get(0).getMethodName().equals("Node")));

        Snapshot last = trace.get(trace.size() - 1);
        Value head = last.getFrames().get(0).getLocals().get("head");
        HeapObject node = last.getObject(((ReferenceValue) head).id());
        assertEquals("Node{val=5, next→7}", node.describe(last));
    }

    private ArrayNode listAtTheEnd(String statements) throws Exception {
        Trace trace = run("""
                import java.util.ArrayList;
                public class Main {
                    public static void main(String[] args) {
                        ArrayList<Integer> list = new ArrayList<>();
                        %s
                        System.out.println(list.size());
                    }
                }
                """.formatted(statements), "");
        Snapshot last = trace.get(trace.size() - 1);
        Value list = last.getFrames().get(0).getLocals().get("list");
        return (ArrayNode) last.getObject(((ReferenceValue) list).id());
    }

    @Test
    void anArrayListIsCapturedAsAnArrayOfItsItems() throws Exception {
        ArrayNode list = listAtTheEnd("list.add(4); list.add(7); list.add(9);");
        assertEquals(3, list.getLength());
        assertEquals(List.of("4", "7", "9"), list.getElements().stream().map(Value::display).toList());
        assertEquals("ArrayList(3) [4, 7, 9]", list.describe(new Snapshot(List.of(), Map.of(), Map.of(), 0, null)));
    }

    private String shownAtTheEnd(String declaration) throws Exception {
        Trace trace = run("""
                import java.util.*;
                public class Main {
                    public static void main(String[] args) {
                        %s
                        System.out.println(1);
                    }
                }
                """.formatted(declaration), "");
        Snapshot last = trace.get(trace.size() - 1);
        Value value = last.getFrames().get(0).getLocals().get("c");
        return last.getObject(((ReferenceValue) value).id()).describe(last);
    }

    @Test
    void javasOtherCollectionsAreCapturedToo() throws Exception {
        assertEquals("Stack(2) [1, 2]", shownAtTheEnd("Stack<Integer> c = new Stack<>(); c.push(1); c.push(2);"));
        assertEquals("LinkedList(2) [1, 2]", shownAtTheEnd("Queue<Integer> c = new LinkedList<>(); c.add(1); c.add(2);"));
        assertEquals("ArrayDeque(1) [5]", shownAtTheEnd("Deque<Integer> c = new ArrayDeque<>(); c.add(5);"));
        assertEquals("TreeSet(2) [3, 4]", shownAtTheEnd("Set<Integer> c = new TreeSet<>(); c.add(4); c.add(3);"));
        assertEquals("HashMap(1) [\"a\"=1]", shownAtTheEnd("Map<String, Integer> c = new HashMap<>(); c.put(\"a\", 1);"));
        assertEquals("TreeMap(1) [\"a\"=1]", shownAtTheEnd("Map<String, Integer> c = new TreeMap<>(); c.put(\"a\", 1);"));
        assertEquals("LinkedHashMap(1) [\"a\"=1]", shownAtTheEnd("Map<String, Integer> c = new LinkedHashMap<>(); c.put(\"a\", 1);"));
        assertEquals("List(2) [1, 2]", shownAtTheEnd("List<Integer> c = List.of(1, 2);"));
    }

    @Test
    void recursionWithNoBaseCaseEndsQuickly() {
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(60), () -> {
            Trace trace = run("""
                    public class Main {
                        static int f(int n) {
                            return f(n + 1) + 1;
                        }
                        public static void main(String[] args) {
                            System.out.println(f(0));
                        }
                    }
                    """, "");
            assertEquals(TraceLimits.MAX_STEPS, trace.size());
            assertTrue(trace.getEndMessage().contains("f() calling itself"), trace.getEndMessage());
            assertTrue(trace.get(trace.size() - 1).getFrames().size() <= TraceLimits.MAX_FRAMES);
        });
    }

    @Test
    void stopEndsARunAndKeepsItsSteps() throws Exception {
        Thread stopper = new Thread(() -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                return;
            }
            tracer.stop();
        });
        stopper.start();
        Trace trace = run("""
                public class Main {
                    public static void main(String[] args) throws Exception {
                        while (true) {
                            Thread.sleep(20);
                        }
                    }
                }
                """, "");
        stopper.join();
        assertEquals(EndReason.STOPPED, trace.getEndReason());
        assertTrue(trace.size() > 0);
        assertEquals(0, tracer.stepsSoFar());
    }

    @Test
    void theClassHeaderDoesNotRunWhenABridgeMethodIsCalled() throws Exception {
        Trace trace = run("""
                import java.util.*;
                public class Main {
                    static class S implements Comparable<S> {
                        int v;
                        S(int v) {
                            this.v = v;
                        }
                        public int compareTo(S o) {
                            return v - o.v;
                        }
                    }
                    public static void main(String[] args) {
                        List<S> l = new ArrayList<>(List.of(new S(2), new S(1)));
                        Collections.sort(l);
                        System.out.println(l.get(0).v);
                    }
                }
                """, "");
        assertFalse(lines(trace).contains(3), "line 3 is the class header");
        assertTrue(lines(trace).contains(9), "compareTo itself still runs");
    }

    @Test
    void aSubclassObjectKnowsItsParent() throws Exception {
        Trace trace = run("""
                public class Main {
                    static class A { int x = 1; }
                    static class B extends A { int y = 2; }
                    public static void main(String[] args) {
                        B b = new B();
                        A a = new A();
                        System.out.println(b.x);
                    }
                }
                """, "");
        Snapshot last = trace.get(trace.size() - 1);
        raphael.model.ObjectNode b = (raphael.model.ObjectNode) last.getObject(((ReferenceValue) last.getFrames().get(0).getLocals().get("b")).id());
        raphael.model.ObjectNode a = (raphael.model.ObjectNode) last.getObject(((ReferenceValue) last.getFrames().get(0).getLocals().get("a")).id());
        assertEquals("Main$A", b.getParentName());
        assertNull(a.getParentName());
    }

    @Test
    void anEnumConstantIsShownAsItsName() throws Exception {
        Trace trace = run("""
                public class Main {
                    enum Color { RED, GREEN }
                    public static void main(String[] args) {
                        Color c = Color.GREEN;
                        System.out.println(c);
                    }
                }
                """, "");
        assertEquals("GREEN", trace.get(trace.size() - 1).getFrames().get(0).getLocals().get("c").display());
    }

    @Test
    void aStudentsOwnCollectionIsDrawnFromItsFields() throws Exception {
        Trace trace = run("""
                import java.util.*;
                public class Main {
                    static class Bag extends ArrayList<Integer> { int extra = 3; }
                    public static void main(String[] args) {
                        Bag c = new Bag();
                        System.out.println(1);
                    }
                }
                """, "");
        Snapshot last = trace.get(trace.size() - 1);
        Value value = last.getFrames().get(0).getLocals().get("c");
        assertEquals("Bag{extra=3}", last.getObject(((ReferenceValue) value).id()).describe(last));
    }

    @Test
    void anEmptyArrayListHasNoCells() throws Exception {
        ArrayNode list = listAtTheEnd("list.add(4); list.remove(0);");
        assertEquals(0, list.getLength());
        assertTrue(list.getElements().isEmpty());
    }

    // A caught exception is shown on the first step of its catch, with where it was thrown (the
    // student's line that called Integer.parseInt) and the methods it left.
    @Test
    void aCaughtExceptionIsRecordedWithItsJump() throws Exception {
        Trace trace = run("""
                public class Main {
                    static int parse(String s) {
                        return Integer.parseInt(s);
                    }
                    public static void main(String[] args) {
                        try {
                            int x = parse("abc");
                        } catch (NumberFormatException e) {
                            System.out.println("bad");
                        }
                    }
                }
                """, "");
        raphael.model.ThrownException thrown = trace.getSnapshots().stream()
                .map(Snapshot::getThrown).filter(java.util.Objects::nonNull).findFirst().orElseThrow();
        assertEquals("NumberFormatException", thrown.type());
        assertEquals(3, thrown.throwLine());
        assertEquals(8, thrown.catchLine());
        assertEquals(List.of("parse"), thrown.leftMethods());
    }

    // Java throws and catches exceptions inside itself all the time; none of those is shown.
    @Test
    void javasOwnExceptionsAreNotShown() throws Exception {
        Trace trace = run("""
                import java.util.*;
                public class Main {
                    public static void main(String[] args) {
                        Scanner in = new Scanner(System.in);
                        Map<String, Integer> counts = new HashMap<>();
                        while (in.hasNext()) {
                            counts.merge(in.next(), 1, Integer::sum);
                        }
                        System.out.println(String.format("%s %.2f", counts, Math.PI));
                    }
                }
                """, "a b a c");
        assertTrue(trace.getSnapshots().stream().allMatch(step -> step.getThrown() == null));
        assertEquals(EndReason.FINISHED, trace.getEndReason());
    }

    @Test
    void theStepLimitFromSettingsIsUsed() throws Exception {
        CompileResult program = new SourceCompiler().compile("""
                public class Main {
                    public static void main(String[] args) {
                        int x = 0;
                        while (true) { x++; }
                    }
                }
                """);
        JdiTracer tracer = new JdiTracer();
        tracer.setLimits(new RunLimits(150, 5, 128));
        Trace trace = tracer.trace(program, "");
        assertEquals(150, trace.size());
        assertTrue(trace.getEndMessage().startsWith("Stopped after 150 steps"), trace.getEndMessage());
    }

    // The program must never outlive Raphael, however Raphael ends. TraceAndWait plays Raphael.
    @Test
    void theProgramEndsWhenRaphaelIsKilled() throws Exception {
        assertNothingLeftBehind("kill"); // like Task Manager's "End task": no code of Raphael's runs
    }

    @Test
    void theProgramEndsWhenRaphaelQuits() throws Exception {
        assertNothingLeftBehind("exit"); // a normal quit, in the middle of a run
    }

    private void assertNothingLeftBehind(String how) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process raphael = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
                TraceAndWait.class.getName(), how).redirectErrorStream(true).start();
        long pid = assertTimeoutPreemptively(Duration.ofSeconds(30), () -> {
            BufferedReader out = new BufferedReader(new InputStreamReader(raphael.getInputStream(), StandardCharsets.UTF_8));
            for (String line = out.readLine(); line != null; line = out.readLine()) {
                if (line.startsWith("program ")) {
                    return Long.parseLong(line.substring("program ".length()).trim());
                }
            }
            return fail("the stand-in for Raphael ended without starting the program");
        });
        if (how.equals("kill")) {
            raphael.destroyForcibly();
        }
        assertTrue(raphael.waitFor(10, TimeUnit.SECONDS));
        ProcessHandle program = ProcessHandle.of(pid).orElse(null);
        if (program != null) {
            // Polled: onExit() on a process that isn't our own child checks less and less often
            // on macOS, and can report an ended program seconds late (the same reason ParentWatch polls).
            // 15 s: GitHub's macOS machines are sometimes slow enough to miss 6 s (2026-11-11).
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            while (program.isAlive() && System.nanoTime() < until) {
                Thread.sleep(100);
            }
            if (program.isAlive()) {
                program.destroyForcibly();
                fail("the traced program was still running after Raphael ended (" + how + ")");
            }
        }
    }
}
