package raphael.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import raphael.compile.SourceCompiler;
import raphael.io.ExampleCatalog;
import raphael.model.Trace;
import raphael.trace.JdiTracer;

class GraphViewTest {

    // BFS: the adjacency list is also drawn as a graph of 6 vertices with 6 edges, each drawn once
    // as a line, and by the end every vertex is visited (grey).
    @Test
    void anAdjacencyListIsAlsoAGraph() throws Exception {
        String source = ExampleCatalog.source(ExampleCatalog.find("BFS"));
        Trace trace = new JdiTracer().trace(new SourceCompiler().compile(source));
        Scene end = new SceneBuilder(source).build(trace).get(trace.size() - 1);
        long vertices = end.getNodes().stream().filter(n -> n.key().matches("graph:#\\d+\\[\\d+\\]")).count();
        assertEquals(6, vertices);
        assertEquals(6, end.getEdges().stream().filter(e -> e.key().startsWith("graph:")).count());
        assertTrue(end.getEdges().stream().filter(e -> e.key().startsWith("graph:")).allMatch(e -> e.kind() == SceneEdge.Kind.TREE));
        assertTrue(end.getNodes().stream().filter(n -> n.key().matches("graph:#\\d+\\[\\d+\\]"))
                .allMatch(n -> n.kind() == SceneNode.Kind.VISITED_NODE));
    }

    // Dijkstra: a weight matrix, with each vertex's distance under it.
    @Test
    void aWeightMatrixShowsDistances() throws Exception {
        String source = ExampleCatalog.source(ExampleCatalog.find("Dijkstra"));
        Trace trace = new JdiTracer().trace(new SourceCompiler().compile(source));
        Scene end = new SceneBuilder(source).build(trace).get(trace.size() - 1);
        assertTrue(end.getNodes().stream().anyMatch(n -> n.key().matches("graph:#\\d+\\[0\\]:d") && n.text().equals("d=0")));
        assertTrue(end.getNodes().stream().anyMatch(n -> n.key().contains(":w") ), "weights are shown");
    }
}
