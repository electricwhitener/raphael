package raphael;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import raphael.compile.CompileResult;
import raphael.compile.SourceCompiler;
import raphael.model.Snapshot;
import raphael.model.Trace;
import raphael.trace.JdiTracer;

// Runs every example many times (100 unless -Dsoak.runs says otherwise), and each run must take
// exactly the same steps. A timing bug shows up only now and then: in version 1, about 1 run in
// 60 recorded no steps at all. Too slow for every "mvn test", so it's tagged "soak" and runs in
// its own CI job (.github/workflows/soak.yml).
@Tag("soak")
class SoakTest {

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"BubbleSort", "ReverseList", "BSTInsert", "FibDP", "GridPaths", "BuggyList", "OffByOne",
            "BTreeInsert", "BankAccounts", "Exceptions"})
    void everyRunTakesTheSameSteps(String name) throws Exception {
        String source = new String(getClass().getResourceAsStream("/examples/" + name + ".java").readAllBytes(),
                StandardCharsets.UTF_8);
        CompileResult program = new SourceCompiler().compile(source);
        int runs = Integer.getInteger("soak.runs", 100);
        Trace first = new JdiTracer().trace(program, "");
        List<Integer> expected = linesOf(first);
        assertTrue(expected.size() > 0, name + " recorded no steps");
        for (int run = 2; run <= runs; run++) {
            Trace trace = new JdiTracer().trace(program, "");
            assertEquals(expected, linesOf(trace), name + ", run " + run + " of " + runs);
            assertEquals(first.getOutput(), trace.getOutput(), name + ", run " + run + " of " + runs);
            assertEquals(first.getEndReason(), trace.getEndReason(), name + ", run " + run + " of " + runs);
        }
    }

    private static List<Integer> linesOf(Trace trace) {
        List<Integer> lines = new ArrayList<>();
        for (Snapshot step : trace.getSnapshots()) {
            lines.add(step.getLine());
        }
        return lines;
    }
}
