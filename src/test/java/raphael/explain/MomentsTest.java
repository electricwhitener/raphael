package raphael.explain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import raphael.compile.CompileResult;
import raphael.compile.SourceCompiler;
import raphael.io.ExampleCatalog;
import raphael.model.Trace;
import raphael.trace.JdiTracer;

// The key moments found in real runs: each sentence is checked against what the run did.
class MomentsTest {

    private static List<Moment> moments(String source) throws Exception {
        CompileResult program = new SourceCompiler().compile(source);
        assertTrue(program.success(), () -> program.errors().toString());
        Trace trace = new JdiTracer().trace(program);
        return new MomentFinder().find(trace, program.facts());
    }

    private static List<Moment> example(String name) throws Exception {
        return moments(ExampleCatalog.source(ExampleCatalog.find(name)));
    }

    private static Moment first(List<Moment> moments, String kind) {
        return moments.stream().filter(m -> m.kind().equals(kind)).findFirst()
                .orElseThrow(() -> new AssertionError("no " + kind + " in " + moments));
    }

    @Test
    void recursionCallsItselfReachesItsBaseCaseAndUnwinds() throws Exception {
        List<Moment> found = example("Factorial");
        assertTrue(first(found, "calls-itself").text().startsWith("factorial(n = 3) starts, while factorial(n = 4) waits"));
        Moment base = first(found, "base-case");
        assertTrue(base.pauses());
        assertEquals("factorial(n = 1) is a base case: it returns 1 without calling itself. 3 calls are still waiting for an answer.",
                base.text());
        assertEquals("factorial(n = 1) returns 1, then factorial(n = 2) returns 2 * 1 = 2, then factorial(n = 3) returns 3 * 2 = 6, "
                + "then factorial(n = 4) returns 4 * 6 = 24. The answer, 24, goes back to main().", first(found, "returns").text());
        assertTrue(base.step() < first(found, "returns").step());
    }

    // Two recursive calls in one return (fib(n - 1) + fib(n - 2)): no value is put in for either,
    // since which one it came from can't be told; and the third base case on a line is only noted.
    @Test
    void branchingRecursionStaysHonestAndQuiet() throws Exception {
        List<Moment> found = example("Fibonacci");
        for (Moment moment : found) {
            assertFalse(moment.text().contains("+ fib("), moment.text());
        }
        long pausedBaseCases = found.stream().filter(m -> m.kind().equals("base-case") && m.pauses()).count();
        assertTrue(pausedBaseCases <= 2 * 2, "at most two pauses for each line's base case: " + pausedBaseCases);
    }

    @Test
    void aSetterThatRefusesAValueIsAKeyMoment() throws Exception {
        Moment refused = first(example("Encapsulation"), "refused");
        assertTrue(refused.pauses());
        assertTrue(refused.text().startsWith("setTarget(90) was refused by the check on line 12, so target stays 24."), refused.text());
    }

    @Test
    void aCaughtExceptionSaysWhichCatchAndWhy() throws Exception {
        List<Moment> found = example("BankAccounts");
        Moment caught = first(found, "caught");
        assertTrue(caught.text().contains("catch (IllegalArgumentException …) at line 60 takes it: the types match."), caught.text());
        assertTrue(caught.text().contains("It left deposit() in the middle"), caught.text());
        long pausedPolymorphism = found.stream().filter(m -> m.kind().equals("polymorphism") && m.pauses()).count();
        assertEquals(2, pausedPolymorphism, "the first two pause, the rest are noted");
    }

    @Test
    void aMissingFileIsExplainedAsAFile() throws Exception {
        List<Moment> found = example("MissingFile");
        Moment missing = first(found, "file-missing");
        assertTrue(missing.text().startsWith("The program tried to open \"settings.txt\", but it isn't in the program's folder"));
        assertTrue(found.stream().noneMatch(m -> m.kind().equals("caught") && m.text().contains("FileNotFoundException")),
                "said once, as a file, not twice");
    }

    // The author's own moment, with its {placeholders} filled from the step.
    @Test
    void anAuthorsMomentIsFilledIn() throws Exception {
        List<Moment> found = moments("""
                public class Main {
                    static class Node {
                        int val;
                        Node(int v) {
                            val = v;
                        }
                    }

                    public static void main(String[] args) {
                        int[] a = {4, 7, 9};
                        Node node = new Node(5);
                        for (int i = 0; i < 2; i++) {
                            int x = a[i] + node.val; // moment: a[i] is {a[i]} and node.val is {node.val}, at i = {i}
                        }
                    }
                }
                """);
        List<String> texts = found.stream().filter(m -> m.kind().equals("author")).map(Moment::text).toList();
        assertEquals(List.of("a[i] is 4 and node.val is 5, at i = 0", "a[i] is 7 and node.val is 5, at i = 1"), texts);
    }
}
