package raphael.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import raphael.compile.SourceCompiler;
import raphael.io.ExampleCatalog;
import raphael.model.Trace;
import raphael.trace.JdiTracer;

class HashTableViewTest {

    // HashChaining: the buckets stand in a column, and each chain's nodes sit on their bucket's
    // row, to its right, in the order the chain links them.
    @Test
    void chainsHangFromTheirBuckets() throws Exception {
        String source = ExampleCatalog.source(ExampleCatalog.find("HashChaining"));
        Trace trace = new JdiTracer().trace(new SourceCompiler().compile(source));
        Scene scene = new SceneBuilder(source).build(trace).get(trace.size() - 1);
        List<SceneNode> buckets = scene.getNodes().stream().filter(n -> n.key().matches("#\\d+\\[\\d\\]")).toList();
        assertEquals(4, buckets.size());
        for (int i = 1; i < buckets.size(); i++) {
            assertEquals(buckets.get(0).x(), buckets.get(i).x(), 0.01, "one column");
            assertTrue(buckets.get(i).y() > buckets.get(i - 1).y());
        }
        SceneNode owl = scene.getNodes().stream().filter(n -> n.kind() == SceneNode.Kind.BOX && n.text().startsWith("\"owl\"")).findFirst().orElseThrow();
        SceneNode dog = scene.getNodes().stream().filter(n -> n.kind() == SceneNode.Kind.BOX && n.text().startsWith("\"dog\"")).findFirst().orElseThrow();
        assertEquals(owl.y(), dog.y(), 0.01, "owl and dog share a bucket");
        assertTrue(dog.x() > owl.x(), "dog comes after owl in the chain");
        assertTrue(owl.x() > buckets.get(0).x());
    }
}
