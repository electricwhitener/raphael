package raphael.render;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Everything to draw for one step: the shapes, the arrows between them, and any values that the
// line which just ran copied from one cell to another (so the Animator can fly them across).
// OOP: encapsulation (the collections are private); overloading (three add methods).
public class Scene {

    // A value copied by the line that just ran, e.g. arr[j] = arr[j + 1]: from one cell to another.
    public record Move(String fromKey, String toKey) {
    }

    private final Map<String, SceneNode> nodes = new LinkedHashMap<>();
    private final Map<String, SceneEdge> edges = new LinkedHashMap<>();
    private final List<Move> moves = new ArrayList<>();

    public void add(SceneNode node) {
        nodes.put(node.key(), node);
    }

    public void add(SceneEdge edge) {
        edges.put(edge.key(), edge);
    }

    public void add(Move move) {
        moves.add(move);
    }

    public SceneNode getNode(String key) {
        return nodes.get(key);
    }

    public SceneEdge getEdge(String key) {
        return edges.get(key);
    }

    public Collection<SceneNode> getNodes() {
        return Collections.unmodifiableCollection(nodes.values());
    }

    public Collection<SceneEdge> getEdges() {
        return Collections.unmodifiableCollection(edges.values());
    }

    public List<Move> getMoves() {
        return Collections.unmodifiableList(moves);
    }

    // How much room the drawing needs, for the scroll bars.
    public Dimension getSize() {
        double width = 0;
        double height = 0;
        for (SceneNode node : nodes.values()) {
            width = Math.max(width, node.x() + node.w());
            height = Math.max(height, node.y() + node.h());
        }
        return new Dimension((int) width + Style.SIDE_MARGIN, (int) height + Style.MARGIN + Style.CURVE_SPACE);
    }
}
