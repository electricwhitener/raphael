package raphael.explain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import raphael.compile.CompileResult;
import raphael.compile.SourceCompiler;
import raphael.lang.Language;
import raphael.lang.Languages;
import raphael.model.Trace;
import raphael.trace.JdiTracer;

// The key moments of C runs (skipped where C can't run), and the input moments in both languages.
class CMomentsTest {

    private static final Language C = Languages.byId("c");

    @BeforeEach
    void needsC(org.junit.jupiter.api.TestInfo test) {
        if (!test.getDisplayName().startsWith("java")) {
            Assumptions.assumeTrue(C.cantRun() == null, "C can't run on this computer");
        }
    }

    private record Run(Trace trace, List<Moment> moments, Reads reads) {
    }

    private static Run c(String code, String input) throws Exception {
        CompileResult program = C.compile(code);
        assertTrue(program.success(), () -> program.errors().toString());
        Trace trace = C.newTracer().trace(program, input);
        return new Run(trace, new MomentFinder().find(trace, program.facts(), input), new Reads(trace, program.facts(), input));
    }

    private static Moment first(List<Moment> moments, String kind) {
        return moments.stream().filter(m -> m.kind().equals(kind)).findFirst()
                .orElseThrow(() -> new AssertionError("no " + kind + " in " + moments));
    }

    @Test
    void recursionInC() throws Exception {
        Run run = c("""
                int sum(int n) {
                    if (n == 0) {
                        return 0;
                    }
                    return n + sum(n - 1);
                }

                int main(void) {
                    return sum(3) - 6;
                }
                """, "");
        assertTrue(first(run.moments(), "calls-itself").text().startsWith("sum(n = 2) starts, while sum(n = 3) waits"));
        assertTrue(first(run.moments(), "base-case").text().startsWith("sum(n = 0) is a base case"));
        assertTrue(first(run.moments(), "returns").text().contains("goes back to main()"));
    }

    @Test
    void mallocFreeAndADanglingPointer() throws Exception {
        Run run = c("""
                #include <stdlib.h>
                struct Node { int val; struct Node *next; };

                int main(void) {
                    struct Node *head = malloc(sizeof *head);
                    head->val = 1;
                    head->next = malloc(sizeof *head->next);
                    head->next->val = 2;
                    struct Node *second = head->next;
                    free(head->next);
                    return 0;
                }
                """, "");
        Moment made = first(run.moments(), "malloc");
        assertEquals("malloc made a new Node, and head points to it. Its memory isn't set yet, so it shows ?: whatever was there before.",
                made.text());
        assertTrue(run.moments().stream().anyMatch(m -> m.text().startsWith("malloc made a new Node, and head->next points to it")));
        Moment freed = first(run.moments(), "free");
        assertTrue(freed.pauses());
        assertTrue(freed.text().startsWith("free(head->next) gave the Node back"), freed.text());
        assertTrue(freed.text().contains("second and head->next still hold its address: dangling pointers"), freed.text());
    }

    @Test
    void aFunctionChangesItsCallersVariableThroughAPointer() throws Exception {
        Run run = c("""
                void swap(int *a, int *b) {
                    int t = *a;
                    *a = *b;
                    *b = t;
                }

                int main(void) {
                    int x = 1, y = 2;
                    swap(&x, &y);
                    return x;
                }
                """, "");
        List<Moment> through = run.moments().stream().filter(m -> m.kind().equals("through-pointer")).toList();
        assertEquals(2, through.size());
        assertEquals("swap() changed main()'s x through a, which holds &x. That's how a C function changes its caller's "
                + "variables: it's given their addresses.", through.get(0).text());
        assertTrue(through.get(0).pauses());
        assertFalse(through.get(1).pauses()); // y: the same idea, only noted
    }

    @Test
    void scanfReadsAsCDoes() throws Exception {
        Run run = c("""
                #include <stdio.h>

                int main(void) {
                    int age;
                    char initial;
                    char rest[20];
                    scanf("%d", &age);
                    scanf("%c", &initial);
                    scanf(" %c", &initial);
                    fgets(rest, 20, stdin);
                    return 0;
                }
                """, "19\nK Kim\n");
        List<Reads.Spot> spots = new java.util.ArrayList<>();
        for (int s = 0; s < run.trace().size(); s++) {
            spots.addAll(run.reads().justRead(s));
        }
        assertEquals(List.of(new Reads.Spot("stdin", 0, 2, "age"), new Reads.Spot("stdin", 2, 3, "initial"),
                new Reads.Spot("stdin", 3, 4, "initial"), new Reads.Spot("stdin", 4, 9, "rest")), spots);
        Moment newline = first(run.moments(), "read-newline");
        assertTrue(newline.text().startsWith("initial got the newline (Enter) that the scanf before it left"), newline.text());
        assertEquals(8, newline.line());
    }

    @Test
    void filesInC() throws Exception {
        Run run = c("""
                #include <stdio.h>

                int main(void) {
                    FILE *out = fopen("out.txt", "w");
                    fprintf(out, "hi\\n");
                    fclose(out);
                    FILE *in = fopen("missing.txt", "r");
                    if (in == NULL) {
                        return 1;
                    }
                    return 0;
                }
                """, "");
        Moment written = first(run.moments(), "file-written");
        assertTrue(written.text().contains("C keeps what's written to a FILE in a buffer") && written.text().contains("fclose() on line 6"),
                written.text());
        assertTrue(first(run.moments(), "file-missing").text().startsWith("fopen(\"missing.txt\") gave NULL"));
    }

    @Test
    void javaNextLineAfterNextIntReadsAnEmptyLine() throws Exception {
        CompileResult program = new SourceCompiler().compile("""
                import java.util.Scanner;

                public class Main {
                    public static void main(String[] args) {
                        Scanner in = new Scanner(System.in);
                        int age = in.nextInt();
                        String name = in.nextLine();
                        System.out.println(age + name);
                    }
                }
                """);
        Trace trace = new JdiTracer().trace(program, "19\nKim\n");
        Moment newline = first(new MomentFinder().find(trace, program.facts(), "19\nKim\n"), "read-newline");
        assertTrue(newline.text().startsWith("name got an empty line"), newline.text());
    }

    // A recursion of many calls is explained once, not at every call: one of each kind of moment
    // per place in the code (the C BST used to have 35).
    @Test
    void repeatsAreLeftOut() throws Exception {
        Run run = c(raphael.io.ExampleCatalog.source(raphael.io.ExampleCatalog.find("bst_insert")), "");
        java.util.Map<String, Long> kinds = new java.util.TreeMap<>();
        for (Moment moment : run.moments()) {
            kinds.merge(moment.kind() + "@" + moment.line(), 1L, Long::sum);
        }
        assertTrue(kinds.values().stream().allMatch(n -> n == 1), "a repeat at the same place: " + kinds);
        assertTrue(run.moments().size() <= 10, run.moments().size() + " moments: " + kinds);
    }
}
