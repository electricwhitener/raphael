package raphael.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import raphael.compile.CompileResult;
import raphael.compile.SourceCompiler;
import raphael.io.ExampleCatalog;
import raphael.model.Trace;
import raphael.trace.JdiTracer;

class ObjectsScenesTest {

    // BankAccounts: the interface on top, the abstract class under it, the two accounts under
    // that; a dashed line to the interface, solid ones to the parent; main's class left out.
    @Test
    void theClassesAreLaidOutAsInUml() throws Exception {
        CompileResult program = new SourceCompiler().compile(ExampleCatalog.source(ExampleCatalog.find("BankAccounts")));
        Trace trace = new JdiTracer().trace(program);
        ObjectsScenes view = new ObjectsScenes(trace, program.facts());
        Scene scene = view.sceneAt(trace.size() - 1);
        SceneNode printable = scene.getNode("class:Printable");
        SceneNode account = scene.getNode("class:Account");
        SceneNode savings = scene.getNode("class:Savings");
        SceneNode current = scene.getNode("class:Current");
        assertNull(scene.getNode("class:BankAccounts"), "a class with only main isn't part of the design");
        assertTrue(printable.bottom() < account.y() && account.bottom() < savings.y(), "parents above children");
        assertEquals(savings.y(), current.y(), 0.01, "brothers share a row");
        assertEquals(SceneEdge.Kind.IMPLEMENTS, edge(scene, "implements:Account:Printable").kind());
        assertEquals(SceneEdge.Kind.EXTENDS, edge(scene, "extends:Savings").kind());
        assertTrue(view.getRunSize().width < 4 * savings.w(), "rows wrap instead of running off to the right");
        assertNotNull(scene.getNode("class:Account.line:0"));
    }

    private static SceneEdge edge(Scene scene, String key) {
        return scene.getEdges().stream().filter(e -> e.key().equals(key)).findFirst().orElseThrow(() -> new AssertionError(key));
    }
}
