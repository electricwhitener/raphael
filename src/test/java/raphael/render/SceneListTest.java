package raphael.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import raphael.compile.SourceCompiler;
import raphael.io.ExampleCatalog;
import raphael.model.Trace;
import raphael.trace.JdiTracer;

// The pictures are built when they're asked for, in whatever order the user moves. Each step
// must come out the same whatever was built before it: read forwards or backwards, kept in the
// cache or built again after the cache let it go.
class SceneListTest {

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"ReverseList", "BuggyList", "BSTInsert", "BubbleSort", "BTreeInsert", "GridPaths"})
    void everyStepIsTheSameInAnyOrder(String example) throws Exception {
        String source = ExampleCatalog.source(ExampleCatalog.find(example));
        Trace trace = new JdiTracer().trace(new SourceCompiler().compile(source), "");
        SceneList forwards = new SceneBuilder(source).build(trace);
        SceneList backwards = new SceneBuilder(source).build(trace);
        for (int step = trace.size() - 1; step >= 0; step--) {
            backwards.get(step);
        }
        for (int step = 0; step < trace.size(); step++) {
            assertSame(forwards.get(step), backwards.get(step), example + " step " + (step + 1));
        }
        assertSame(forwards.get(0), backwards.get(0), example + " step 1, built again"); // long out of the cache by now
        assertEquals(forwards.getRunSize(), backwards.getRunSize());
    }

    private static void assertSame(Scene expected, Scene actual, String where) {
        assertEquals(List.copyOf(expected.getNodes()), List.copyOf(actual.getNodes()), where + ": shapes");
        assertEquals(List.copyOf(expected.getEdges()), List.copyOf(actual.getEdges()), where + ": arrows");
        assertEquals(expected.getMoves(), actual.getMoves(), where + ": values moving");
    }
}
