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

    // Fibonacci branches, so it's drawn as a tree of its 15 calls. At the end each box shows what
    // it gave back, and fib(1) and fib(0) are told apart though both return within one line.
    @Test
    void branchingRecursionIsATree() throws Exception {
        CompileResult program = new SourceCompiler().compile(ExampleCatalog.source(ExampleCatalog.find("Fibonacci")));
        Trace trace = new JdiTracer().trace(program);
        CallsScenes view = new CallsScenes(trace, new MomentFinder().find(trace, program.facts()));
        assertTrue(view.isTree());
        Scene end = view.sceneAt(trace.size() - 1);
        List<SceneNode> calls = end.getNodes().stream().filter(n -> n.kind() == SceneNode.Kind.OBJECT).toList();
        assertEquals(15, calls.size());
        SceneNode root = calls.stream().filter(n -> n.caption().equals("fib(5)")).findFirst().orElseThrow();
        assertEquals("= 5", end.getNode(root.key() + ".line:0").text());
        assertEquals(5, calls.stream().filter(n -> n.caption().equals("fib(1)")).count());
        assertEquals(3, calls.stream().filter(n -> n.caption().equals("fib(0)")).count());
        for (SceneNode call : calls) {
            String value = end.getNode(call.key() + ".line:0").text();
            if (call.caption().equals("fib(0)")) {
                assertEquals("= 0", value);
            } else if (call.caption().equals("fib(3)")) {
                assertEquals("= 2", value);
            }
        }
        Scene start = view.sceneAt(1);
        assertTrue(start.getNodes().stream().filter(n -> n.kind() == SceneNode.Kind.OBJECT).count() < 3, "calls appear as they're made");
    }

    // Factorial calls itself once each time: a stack, as before.
    @Test
    void straightRecursionStaysAStack() throws Exception {
        CompileResult program = new SourceCompiler().compile(ExampleCatalog.source(ExampleCatalog.find("Factorial")));
        Trace trace = new JdiTracer().trace(program);
        assertTrue(!new CallsScenes(trace, List.of()).isTree());
    }
}
