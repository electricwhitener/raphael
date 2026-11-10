package raphael.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import raphael.compile.CompileResult;
import raphael.compile.SourceCompiler;
import raphael.explain.Moment;
import raphael.explain.MomentFinder;
import raphael.io.ExampleCatalog;
import raphael.model.Trace;
import raphael.trace.JdiTracer;

class CallsScenesTest {

    // At factorial's base case: five boxes (main and four calls), the running one on top and
    // outlined, its variable listed, and the note says it's the base case.
    @Test
    void theStackAtTheBaseCase() throws Exception {
        CompileResult program = new SourceCompiler().compile(ExampleCatalog.source(ExampleCatalog.find("Factorial")));
        Trace trace = new JdiTracer().trace(program);
        List<Moment> moments = new MomentFinder().find(trace, program.facts());
        Moment base = moments.stream().filter(m -> m.kind().equals("base-case")).findFirst().orElseThrow();
        Scene scene = new CallsScenes(trace, moments).sceneAt(base.step());
        long boxes = scene.getNodes().stream().filter(n -> n.kind() == SceneNode.Kind.OBJECT).count();
        assertEquals(5, boxes);
        SceneNode top = scene.getNode("call:4");
        assertTrue(top.caption().startsWith("factorial()"), top.caption());
        assertEquals("n = 1", scene.getNode("call:4.line:0").text());
        assertNotNull(scene.getNode("call:running"));
        assertTrue(scene.getNode("call:note").text().startsWith("base case"));
        assertTrue(scene.getNode("call:0").caption().startsWith("main()"), "main stays at the bottom, with the same key");
    }
}
