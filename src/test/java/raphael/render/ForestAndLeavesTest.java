package raphael.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import raphael.compile.SourceCompiler;
import raphael.io.ExampleCatalog;
import raphael.model.Trace;
import raphael.trace.JdiTracer;

class ForestAndLeavesTest {

    private static Scene last(String example) throws Exception {
        String source = ExampleCatalog.source(ExampleCatalog.find(example));
        Trace trace = new JdiTracer().trace(new SourceCompiler().compile(source));
        return new SceneBuilder(source).build(trace).get(trace.size() - 1);
    }

    // Union-find: the parent array is also a forest; at the end all seven are one tree.
    @Test
    void aParentArrayIsAForest() throws Exception {
        Scene end = last("UnionFind");
        assertEquals(7, end.getNodes().stream().filter(n -> n.key().startsWith("forest:")).count());
        assertEquals(6, end.getEdges().stream().filter(e -> e.key().startsWith("forest:")).count(), "one tree: n - 1 lines");
    }

    // B+ tree: it stays a tree (not a linked list) and its leaves link left to right.
    @Test
    void bPlusLeavesAreLinked() throws Exception {
        Scene end = last("BPlusTree");
        long links = end.getEdges().stream().filter(e -> e.key().contains(".leafnext")).count();
        assertEquals(4, links, "five leaves, four links between them");
        assertTrue(end.getNodes().stream().noneMatch(n -> n.kind() == SceneNode.Kind.BOX), "not drawn as a list");
    }
}
