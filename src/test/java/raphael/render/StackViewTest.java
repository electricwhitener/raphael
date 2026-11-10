package raphael.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import raphael.compile.SourceCompiler;
import raphael.io.ExampleCatalog;
import raphael.model.Trace;
import raphael.trace.JdiTracer;

class StackViewTest {

    // ArrayStack: an array indexed by top stands upright, index 0 at the bottom.
    @Test
    void anArrayStackStandsUp() throws Exception {
        String source = ExampleCatalog.source(ExampleCatalog.find("ArrayStack"));
        Trace trace = new JdiTracer().trace(new SourceCompiler().compile(source));
        Scene scene = new SceneBuilder(source).build(trace).get(trace.size() / 2);
        SceneNode bottom = scene.getNodes().stream().filter(n -> n.key().matches("#\\d+\\[0\\]")).findFirst().orElseThrow();
        SceneNode next = scene.getNode(bottom.key().replace("[0]", "[1]"));
        assertEquals(bottom.x(), next.x(), 0.01, "one column");
        assertTrue(next.y() < bottom.y(), "index 1 sits above index 0");
    }
}
