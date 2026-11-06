package raphael;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import raphael.compile.CompileResult;
import raphael.compile.SourceCompiler;
import raphael.model.EndReason;
import raphael.model.Trace;
import raphael.render.SceneBuilder;
import raphael.trace.JdiTracer;

class ExamplesTest {

    private Trace run(String name) throws Exception {
        String source = new String(getClass().getResourceAsStream("/examples/" + name + ".java").readAllBytes(),
                StandardCharsets.UTF_8);
        CompileResult program = new SourceCompiler().compile(source);
        assertTrue(program.success(), () -> name + " didn't compile: " + program.errors());
        Trace trace = new JdiTracer().trace(program, "");
        assertEquals(trace.size(), new SceneBuilder(source).build(trace).size());
        assertTrue(trace.size() > 0, name + " recorded no steps");
        return trace;
    }

    @Test
    void everyExampleInTheMenuRunsToItsExpectedEnd() throws Exception {
        for (String name : new String[] {"BubbleSort", "ReverseList", "BSTInsert", "FibDP", "GridPaths", "BuggyList",
                "BTreeInsert", "BankAccounts", "Exceptions"}) {
            assertEquals(EndReason.FINISHED, run(name).getEndReason(), name);
        }
        assertEquals(EndReason.EXCEPTION, run("OffByOne").getEndReason(), "OffByOne crashes on purpose");
    }

    @Test
    void bankAccountsPrintsWhatItShould() throws Exception {
        assertEquals("> 1 earns 5.5\n> 2 earns 0.0\n> amount must be positive",
                run("BankAccounts").getOutput().trim().replace("\r\n", "\n"));
    }

    // The exceptions example shows every caught exception on the first step of its catch.
    @Test
    void exceptionsShowsEachCaughtException() throws Exception {
        Trace trace = run("Exceptions");
        assertEquals("popped 20\npopped 10\nstopped: pop on an empty stack\nfinally runs either way\nage 19\n"
                + "not an age: twenty, because For input string: \"twenty\"\nfull after 4 pushes",
                trace.getOutput().trim().replace("\r\n", "\n"));
        java.util.List<String> caught = new java.util.ArrayList<>();
        for (raphael.model.Snapshot step : trace.getSnapshots()) {
            if (step.getThrown() != null) {
                caught.add(step.getThrown().type());
            }
        }
        assertEquals(java.util.List.of("StackEmptyException", "NumberFormatException", "IllegalArgumentException",
                "IllegalStateException"), caught);
    }
}
