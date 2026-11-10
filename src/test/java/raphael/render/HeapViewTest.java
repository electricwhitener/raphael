package raphael.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import raphael.compile.CompileResult;
import raphael.compile.SourceCompiler;
import raphael.io.ExampleCatalog;
import raphael.model.ArrayNode;
import raphael.model.HeapObject;
import raphael.model.Trace;
import raphael.trace.JdiTracer;

class HeapViewTest {

    // Heap sort: the array is also drawn as its tree, node i holding the same value as cell i,
    // and once the end is sorted those cells leave the tree and are faded in the array.
    @Test
    void aHeapArrayIsAlsoATree() throws Exception {
        String source = ExampleCatalog.source(ExampleCatalog.find("HeapSort"));
        CompileResult program = new SourceCompiler().compile(source);
        Trace trace = new JdiTracer().trace(program);
        SceneList scenes = new SceneBuilder(source).build(trace);
        boolean shrank = false;
        for (int s = 0; s < trace.size(); s++) {
            ArrayNode arr = null;
            for (HeapObject object : trace.get(s).getHeap().values()) {
                if (object instanceof ArrayNode && object.getTypeName().equals("int[]")) {
                    arr = (ArrayNode) object;
                }
            }
            if (arr == null) {
                continue;
            }
            Scene scene = scenes.get(s);
            String id = "#" + arr.getId();
            SceneNode root = scene.getNode("heap:" + id + "[0]");
            assertNotNull(root, "step " + s);
            assertEquals(arr.getElements().get(0).display(), root.text());
            SceneNode last = scene.getNode("heap:" + id + "[5]");
            if (last == null) {
                shrank = true;
                assertEquals(SceneNode.Look.DIM, scene.getNode(id + "[5]").look(), "a sorted cell is faded in the array");
            }
        }
        assertTrue(shrank, "the heap gets smaller as the end is sorted");
        Scene end = scenes.get(trace.size() - 1);
        assertTrue(end.getNodes().stream().anyMatch(n -> n.key().startsWith("heap:")));
    }

    // Bubble sort has no heap arithmetic: no tree.
    @Test
    void anOrdinaryArrayIsNot() throws Exception {
        String source = ExampleCatalog.source(ExampleCatalog.find("BubbleSort"));
        Trace trace = new JdiTracer().trace(new SourceCompiler().compile(source));
        Scene scene = new SceneBuilder(source).build(trace).get(trace.size() / 2);
        assertNull(scene.getNodes().stream().filter(n -> n.key().startsWith("heap:")).findFirst().orElse(null));
    }
}
