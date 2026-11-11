package raphael.trace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import raphael.compile.CCompiler;
import raphael.compile.CompileResult;
import raphael.lang.Languages;
import raphael.model.EndReason;
import raphael.model.Exit;
import raphael.model.FrameSnapshot;
import raphael.model.HeapObject;
import raphael.model.ObjectNode;
import raphael.model.ReferenceValue;
import raphael.model.Snapshot;
import raphael.model.SourceFile;
import raphael.model.Trace;

// C programs traced through gdb (skipped where C can't run: no gcc, or no gdb with Python).
class CTracerTest {

    @BeforeEach
    void needsGdb() {
        Assumptions.assumeTrue(Languages.byId("c").cantRun() == null, "C can't run on this computer");
    }

    private static Trace trace(String code, String input) throws Exception {
        return trace(code, input, RunLimits.DEFAULTS);
    }

    private static Trace trace(String code, String input, RunLimits limits) throws Exception {
        CompileResult program = new CCompiler().compile(code);
        assertTrue(program.success(), program.errors().toString());
        CTracer tracer = new CTracer();
        tracer.setLimits(limits);
        return tracer.trace(program, input);
    }

    private static List<Integer> lines(Trace trace) {
        List<Integer> lines = new ArrayList<>();
        trace.getSnapshots().forEach(s -> lines.add(s.getLine()));
        return lines;
    }

    private static List<String> returns(Trace trace, String method) {
        List<String> values = new ArrayList<>();
        for (Snapshot s : trace.getSnapshots()) {
            for (Exit exit : s.getExits()) {
                if (exit.method().equals(method)) {
                    values.add(exit.returned() == null ? null : exit.returned().display());
                }
            }
        }
        return values;
    }

    static final String LIST = """
            #include <stdio.h>
            #include <stdlib.h>

            struct Node {
                int val;
                struct Node *next;
            };

            struct Node *push(struct Node *head, int val) {
                struct Node *n = malloc(sizeof *n);
                n->val = val;
                n->next = head;
                return n;
            }

            int sum(struct Node *head) {
                if (head == NULL) {
                    return 0;
                }
                return head->val + sum(head->next);
            }

            int main(void) {
                struct Node *head = NULL;
                for (int i = 1; i <= 3; i++) {
                    head = push(head, i * 10);
                }
                printf("sum %d\\n", sum(head));
                return 0;
            }
            """;

    @Test
    void linkedList() throws Exception {
        Trace trace = trace(LIST, "");
        assertEquals(EndReason.FINISHED, trace.getEndReason(), trace.getEndMessage());
        assertEquals("sum 60\n", trace.getOutput().replace("\r\n", "\n"));
        assertEquals(24, trace.get(0).getLine()); // the first line inside main, as in Java
        assertFalse(lines(trace).contains(23), "no stop on main's own line");
        assertFalse(lines(trace).contains(14), "no stop on } after a return");
        assertEquals(List.of("0", "10", "30", "60"), returns(trace, "sum"));
        assertEquals(3, returns(trace, "push").size());

        Snapshot last = trace.get(trace.size() - 1);
        FrameSnapshot main = last.getFrames().get(0);
        assertEquals("main", main.getMethodName());
        assertEquals("main", main.getClassName()); // main.c
        ReferenceValue head = (ReferenceValue) main.getLocals().get("head");
        ObjectNode first = (ObjectNode) last.getObject(head.id());
        assertEquals("Node", first.getTypeName());
        assertEquals("30", first.getField("val").display());
        assertEquals("Node", first.getFieldType("next")); // so StructureDetector links the list
        assertTrue(first.describe(last).startsWith("struct Node {val=30, next→"), first.describe(last)); // as C writes it
        ObjectNode tail = (ObjectNode) last.getObject(((ReferenceValue) ((ObjectNode) last.getObject(
                ((ReferenceValue) first.getField("next")).id())).getField("next")).id());
        assertEquals("NULL", tail.getField("next").display()); // C's word
        int nodes = 0;
        for (HeapObject object : last.getHeap().values()) {
            nodes += object instanceof ObjectNode ? 1 : 0;
        }
        assertEquals(3, nodes);
        assertEquals(trace.getOutput().length(), last.getOutputLength()); // the last step, return 0, comes after printf
        assertEquals(0, trace.get(0).getOutputLength());
    }

    @Test
    void factorialReturnsInOrder() throws Exception {
        Trace trace = trace("""
                int factorial(int n) {
                    if (n <= 1) {
                        return 1;
                    }
                    return n * factorial(n - 1);
                }

                int main(void) {
                    int f = factorial(4);
                    return f - 24;
                }
                """, "");
        assertEquals(List.of("1", "2", "6", "24"), returns(trace, "factorial"));
        assertFalse(lines(trace).contains(6), "no stop on } in a function that returns a value");
        assertEquals("1", trace.getSnapshots().stream().filter(s -> s.getFrames().size() == 5).findFirst()
                .orElseThrow().getFrames().get(0).getLocals().get("n").display());
    }

    @Test
    void voidFunctionStopsOnItsClosingBraceUnlessItReturned() throws Exception {
        Trace trace = trace("""
                int seen = 0;

                void mark(int x) {
                    if (x < 0) {
                        return;
                    }
                    seen = x;
                }

                int main(void) {
                    mark(-1);
                    mark(5);
                    return seen - 5;
                }
                """, "");
        List<Integer> lines = lines(trace);
        assertEquals(1, lines.stream().filter(l -> l == 8).count(), "only the call that didn't return early: " + lines);
        assertEquals(2, returns(trace, "mark").size());
        assertEquals("5", trace.get(trace.size() - 1).getStatics().get("main.seen").display());
    }

    // ---- pointers and structs ----

    @Test
    void pointersToVariablesAreNamedAsCWritesThem() throws Exception {
        Trace trace = trace("""
                void swap(int *a, int *b) {
                    int t = *a;
                    *a = *b;
                    *b = t;
                }

                int main(void) {
                    int x = 1;
                    int arr[4] = {5, 6, 7, 8};
                    int *end = arr + 4;
                    swap(&x, &arr[2]);
                    return x + (int) (end - arr);
                }
                """, "");
        FrameSnapshot inSwap = trace.getSnapshots().stream().map(s -> s.getFrames().get(0))
                .filter(f -> f.getMethodName().equals("swap")).findFirst().orElseThrow();
        assertEquals("&x", inSwap.getLocals().get("a").display());
        assertEquals("&arr[2]", inSwap.getLocals().get("b").display());
        Snapshot last = trace.get(trace.size() - 1);
        assertEquals("&arr[4]", last.getFrames().get(0).getLocals().get("end").display()); // one past the end
        assertEquals("7", last.getFrames().get(0).getLocals().get("x").display());
    }

    @Test
    void pointerToAPointerBuildsAList() throws Exception {
        Trace trace = trace("""
                #include <stdlib.h>
                struct Node { int val; struct Node *next; };

                void push(struct Node **head_ref, int val) {
                    struct Node *n = malloc(sizeof *n);
                    n->val = val;
                    n->next = *head_ref;
                    *head_ref = n;
                }

                int main(void) {
                    struct Node *head = NULL;
                    push(&head, 1);
                    push(&head, 2);
                    return head->val;
                }
                """, "");
        FrameSnapshot inPush = trace.getSnapshots().stream().map(s -> s.getFrames().get(0))
                .filter(f -> f.getMethodName().equals("push")).findFirst().orElseThrow();
        assertEquals("&head", inPush.getLocals().get("head_ref").display());
        Snapshot last = trace.get(trace.size() - 1);
        ObjectNode head = (ObjectNode) last.getObject(((ReferenceValue) last.getFrames().get(0).getLocals().get("head")).id());
        assertEquals("2", head.getField("val").display());
        assertEquals("1", ((ObjectNode) last.getObject(((ReferenceValue) head.getField("next")).id())).getField("val").display());
    }

    @Test
    void structsOnTheStackAndInArrays() throws Exception {
        Trace trace = trace("""
                typedef struct {
                    char name[8];
                    int age;
                } Person;

                int main(void) {
                    Person people[2] = {{"Ann", 20}, {"Bo", 31}};
                    Person *older = &people[1];
                    char word[4] = "hi";
                    return older->age + word[0];
                }
                """, "");
        Snapshot last = trace.get(trace.size() - 1);
        java.util.Map<String, raphael.model.Value> locals = last.getFrames().get(0).getLocals();
        raphael.model.ArrayNode people = (raphael.model.ArrayNode) last.getObject(((ReferenceValue) locals.get("people")).id());
        assertEquals("Person[]", people.getTypeName());
        ObjectNode ann = (ObjectNode) last.getObject(((ReferenceValue) people.getElements().get(0)).id());
        assertEquals("Person", ann.getTypeName()); // the typedef's name: the struct has none
        assertTrue(ann.describe(last).startsWith("Person{"), ann.describe(last)); // so it isn't "struct Person"
        assertEquals("\"Ann\"", ann.getField("name").display()); // a char array in a struct reads as its text
        assertEquals(people.getElements().get(1), locals.get("older")); // the same box as the array's cell
        raphael.model.ArrayNode word = (raphael.model.ArrayNode) last.getObject(((ReferenceValue) locals.get("word")).id());
        assertEquals(List.of("'h'", "'i'", "'\\0'", "'\\0'"), word.getElements().stream().map(raphael.model.Value::display).toList());
    }

    @Test
    void segfaultEndsTheRunAndKeepsTheOutput() throws Exception {
        Trace trace = trace("""
                #include <stdio.h>

                int main(void) {
                    int *p = NULL;
                    printf("before\\n");
                    *p = 1;
                    return 0;
                }
                """, "");
        assertEquals(EndReason.EXCEPTION, trace.getEndReason());
        assertEquals("Segmentation fault: p was NULL when the line used *p (line 6)", trace.getEndMessage());
        assertEquals("Segmentation fault: p was NULL when the line used *p", trace.get(trace.size() - 1).getException());
        assertTrue(trace.getOutput().startsWith("before"));
    }

    // ---- memory ----

    @Test
    void mallocdMemoryIsAnArrayOfItsLengthAndUnsetCellsAreQuestionMarks() throws Exception {
        Trace trace = trace("""
                #include <stdlib.h>

                int main(void) {
                    int *a = malloc(4 * sizeof(int));
                    a[0] = 7;
                    int *p = a + 2;
                    *p = 9;
                    free(a);
                    return 0;
                }
                """, "");
        Snapshot beforeFree = trace.getSnapshots().stream().filter(s -> s.getLine() == 8).findFirst().orElseThrow();
        java.util.Map<String, raphael.model.Value> locals = beforeFree.getFrames().get(0).getLocals();
        raphael.model.ArrayNode a = (raphael.model.ArrayNode) beforeFree.getObject(((ReferenceValue) locals.get("a")).id());
        assertEquals("int[]", a.getTypeName());
        assertEquals(List.of("7", "?", "9", "?"), a.getElements().stream().map(raphael.model.Value::display).toList());
        assertEquals("&a[2]", locals.get("p").display()); // a marker on a's cell 2
        assertFalse(a.isFreed());
        Snapshot afterFree = trace.get(trace.size() - 1);
        assertTrue(afterFree.getObject(a.getId()).isFreed());
    }

    @Test
    void aFreedNodeStaysDrawnAndUsingItIsCaught() throws Exception {
        Trace trace = trace("""
                #include <stdlib.h>
                struct Node { int val; struct Node *next; };

                int main(void) {
                    struct Node *n = malloc(sizeof *n);
                    n->val = 5;
                    n->next = NULL;
                    free(n);
                    int stale = n->val;
                    free(n);
                    return stale;
                }
                """, "");
        Snapshot afterFree = trace.getSnapshots().stream().filter(s -> s.getLine() == 9).findFirst().orElseThrow();
        ObjectNode node = (ObjectNode) afterFree.getObject(((ReferenceValue) afterFree.getFrames().get(0).getLocals().get("n")).id());
        assertTrue(node.isFreed());
        assertEquals("5", node.getField("val").display()); // what it last held
        assertEquals(EndReason.EXCEPTION, trace.getEndReason()); // the second free
        assertEquals("Aborted: free() was called twice on the same block (line 10)", trace.getEndMessage());
    }

    @Test
    void crashesAreExplainedFromTheLine() throws Exception {
        Trace chain = trace("""
                #include <stdlib.h>
                struct Node { int val; struct Node *next; };

                int main(void) {
                    struct Node *head = malloc(sizeof *head);
                    head->val = 1;
                    head->next = NULL;
                    return head->next->val;
                }
                """, "");
        assertEquals("Segmentation fault: head->next was NULL when the line used head->next->val (line 8)", chain.getEndMessage());
        Trace divide = trace("""
                int main(void) {
                    int zero = 0;
                    return 10 / zero;
                }
                """, "");
        if (System.getProperty("os.arch").matches("aarch64|arm64")) {
            assertEquals(EndReason.FINISHED, divide.getEndReason()); // ARM processors don't stop on 10 / 0: it gives 0
        } else {
            assertEquals("Arithmetic error: zero was 0 when the line divided by it (line 3)", divide.getEndMessage());
        }
        Trace assertion = trace("""
                #include <assert.h>

                int main(void) {
                    int n = -1;
                    assert(n >= 0);
                    return n;
                }
                """, "");
        assertTrue(assertion.getEndMessage().startsWith("Aborted: assert(n >= 0) failed"), assertion.getEndMessage());
    }

    @Test
    void oneLineInfiniteLoopIsStoppedByTheWatchdog() throws Exception {
        long start = System.currentTimeMillis();
        Trace trace = trace("""
                int main(void) {
                    int x = 1;
                    while (x) ;
                    return 0;
                }
                """, "", new RunLimits(2000, 1, 256));
        assertEquals(EndReason.TIMEOUT, trace.getEndReason());
        assertTrue(trace.getEndMessage().contains("line 3"), trace.getEndMessage());
        assertTrue(System.currentTimeMillis() - start < 10_000);
    }

    @Test
    void runawayRecursionHitsTheStepLimit() throws Exception {
        Trace trace = trace("""
                int f(int n) {
                    return f(n + 1) + 1;
                }

                int main(void) {
                    return f(0);
                }
                """, "", new RunLimits(500, 5, 256));
        assertEquals(EndReason.STEP_LIMIT, trace.getEndReason());
        assertEquals(500, trace.size());
        assertTrue(trace.getEndMessage().contains("f() calling itself"), trace.getEndMessage());
    }

    @Test
    void inputAndFiles() throws Exception {
        Trace trace = trace("""
                #include <stdio.h>

                int main(void) {
                    int a, b;
                    scanf("%d %d", &a, &b);
                    FILE *f = fopen("sum.txt", "w");
                    fprintf(f, "%d\\n", a + b);
                    fclose(f);
                    printf("%d\\n", a * b);
                    return 0;
                }
                """, "4 9\n");
        assertEquals("36\n", trace.getOutput().replace("\r\n", "\n"));
        Snapshot last = trace.get(trace.size() - 1);
        assertEquals("13\n", last.getFiles().get("sum.txt").replace("\r\n", "\n"));
        assertEquals("FILE", last.getFrames().get(0).getLocals().get("f").display()); // not the C library's insides
        Snapshot beforeClose = trace.getSnapshots().stream().filter(s -> s.getLine() == 8).findFirst().orElseThrow();
        assertEquals("", beforeClose.getFiles().get("sum.txt")); // written to the buffer, not the file, until fclose
    }

    @Test
    void stopEndsARun() throws Exception {
        CompileResult program = new CCompiler().compile("""
                int main(void) {
                    long total = 0;
                    for (long i = 0; i < 100000000; i++) {
                        total += i;
                    }
                    return (int) total;
                }
                """);
        CTracer tracer = new CTracer();
        tracer.setLimits(new RunLimits(20_000, 30, 256));
        Thread stopper = new Thread(() -> {
            while (tracer.stepsSoFar() < 50) {
                Thread.onSpinWait();
            }
            tracer.stop();
        });
        stopper.start();
        Trace trace = tracer.trace(program, "");
        assertEquals(EndReason.STOPPED, trace.getEndReason());
        assertTrue(trace.size() >= 50 && trace.size() < 20_000);
    }

    @Test
    void severalFilesNameTheirFile() throws Exception {
        SourceFile header = new SourceFile("twice.h", "int twice(int x);\n");
        SourceFile twice = new SourceFile("lib/twice.c", """
                #include "../twice.h"

                int twice(int x) {
                    return 2 * x;
                }
                """);
        SourceFile main = new SourceFile("main.c", """
                #include "twice.h"

                int main(void) {
                    int y = twice(21);
                    return y - 42;
                }
                """);
        CompileResult program = new CCompiler().compileFolder(List.of(header, twice, main), null);
        assertTrue(program.success(), program.errors().toString());
        Trace trace = new CTracer().trace(program, "");
        FrameSnapshot inTwice = trace.getSnapshots().stream().map(s -> s.getFrames().get(0))
                .filter(f -> f.getMethodName().equals("twice")).findFirst().orElseThrow();
        assertEquals("lib/twice.c", inTwice.getFile());
        assertEquals("twice", inTwice.getClassName());
        assertEquals("main.c", trace.get(0).getFrames().get(0).getFile());
        assertEquals(List.of("42"), returns(trace, "twice"));
    }
}
