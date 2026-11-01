package raphael.render;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import raphael.detect.MultiwayTreeStructure;
import raphael.model.ArrayNode;
import raphael.model.NullValue;
import raphael.model.Value;

// Draws a multi-way tree, like a B-tree, the way textbooks do:
//
//                 [ 10 |    |    ]
//                 [ • | • | / | / ]
//                  ╱      ╲
//     [ 3 | 5 |   ]        [ 20 | 25 |   ]
//
// Each node is a row of key cells; keys past the count (n) are faded. Under a node that has
// children, a row of small slots holds the child pointers. Leaves sit side by side at the
// bottom and each parent is centred above its children.
// The key cells use their array's keys ("#7[2]"), so markers, read/write outlines, flashes and
// flying values work on them just like on a plain array.
// An object exists before its fields are set, so a node whose keys array isn't made yet is drawn as
// a faded "new" cell. A node with no keys at all (a trie) shows its other fields, like end=true. When
// there are many child slots or no keys, only the used slots are drawn, each with its index.
// OOP: inheritance (extends StructureRenderer) and overriding (render).
public class MultiwayTreeRenderer extends StructureRenderer {

    private static final int MAX_ALL_SLOTS = 8;

    private final MultiwayTreeStructure tree;
    private final RenderContext context;
    private final Map<Long, Double> xs = new HashMap<>();
    private final Map<Long, Integer> depths = new HashMap<>();
    private double cursor; // where the next leaf goes
    private double cellW;

    public MultiwayTreeRenderer(MultiwayTreeStructure tree, RenderContext context) {
        this.tree = tree;
        this.context = context;
    }

    @Override
    public Dimension render(Scene scene, double x, double y) {
        cellW = Style.CELL_W;
        for (long id : tree.getNodes().keySet()) {
            ArrayNode keys = tree.getKeysArray(id);
            if (keys != null) {
                cellW = Math.max(cellW, context.cellWidth(keys.getId()));
            }
        }
        cursor = x;
        Set<Long> visited = new HashSet<>();
        place(tree.getRoot(), 0, visited);
        for (long id : tree.getNodes().keySet()) {
            if (!visited.contains(id)) { // not reachable from the root (only in a broken tree)
                place(id, 0, visited);
            }
        }

        double top = y + Style.LABEL_SPACE;
        double levelH = Style.CELL_H + Style.CHILD_SLOT_H + Style.LEVEL_GAP;
        int maxDepth = 0;
        for (long id : tree.getNodes().keySet()) {
            maxDepth = Math.max(maxDepth, depths.get(id));
            drawNode(scene, id, xs.get(id), top + depths.get(id) * levelH);
        }
        double width = Math.max(cursor - x - Style.SUBTREE_GAP, cellW);
        double height = Style.LABEL_SPACE + maxDepth * levelH + Style.CELL_H + Style.CHILD_SLOT_H + Style.MARKER_H;
        return new Dimension((int) width, (int) height);
    }

    // Places a subtree: leaves go left to right, and a parent is centred over its children.
    // If a parent is wider than its children, the whole subtree moves right so nothing overlaps.
    // Returns the ids in the subtree.
    private List<Long> place(long id, int depth, Set<Long> visited) {
        List<Long> subtree = new ArrayList<>();
        subtree.add(id);
        visited.add(id);
        depths.put(id, depth);
        double start = cursor;
        double width = nodeWidth(id);
        List<Long> children = new ArrayList<>();
        for (long child : tree.getChildren(id)) {
            if (child != -1 && !visited.contains(child)) {
                children.add(child);
            }
        }
        if (children.isEmpty()) {
            xs.put(id, cursor);
            cursor += width + Style.SUBTREE_GAP;
            return subtree;
        }
        for (long child : children) {
            subtree.addAll(place(child, depth + 1, visited));
        }
        long first = children.get(0);
        long last = children.get(children.size() - 1);
        double x = (xs.get(first) + xs.get(last) + nodeWidth(last)) / 2 - width / 2;
        if (x < start) {
            double shift = start - x;
            for (long below : subtree) {
                if (below != id) {
                    xs.merge(below, shift, Double::sum);
                }
            }
            cursor += shift;
            x = start;
        }
        xs.put(id, x);
        cursor = Math.max(cursor, x + width + Style.SUBTREE_GAP);
        return subtree;
    }

    private double nodeWidth(long id) {
        ArrayNode keys = tree.getKeysArray(id);
        if (keys != null) {
            return Math.max(keys.getElements().size(), 1) * cellW;
        }
        double text = Style.textWidth(tree.getPlainFields(id), Style.VALUE_FONT) + 14;
        return Math.ceil(Math.max(Math.max(slotsToShow(id).size(), 1) * cellW, tree.hasKeys() ? 0 : text));
    }

    // The child slots to draw: all of them, unless there are many or the node has no keys. Then
    // only the used ones, because a row of 26 empty slots says nothing.
    private List<Integer> slotsToShow(long id) {
        List<Long> children = tree.getChildren(id);
        boolean sparse = children.size() > MAX_ALL_SLOTS || tree.getKeysArray(id) == null;
        List<Integer> shown = new ArrayList<>();
        for (int i = 0; i < children.size(); i++) {
            if (!sparse || children.get(i) != -1) {
                shown.add(i);
            }
        }
        return shown;
    }

    private void drawNode(Scene scene, long id, double x, double y) {
        double width = nodeWidth(id);
        // An invisible outline of the node, for labels (like "root") and parent arrows to point at.
        scene.add(new SceneNode(key(id), SceneNode.Kind.FRAME, x, y, width, Style.CELL_H, "", null));
        ArrayNode keys = tree.getKeysArray(id);
        if (keys != null) {
            int used = tree.getUsedKeys(id);
            for (int i = 0; i < keys.getElements().size(); i++) {
                String cellKey = key(keys.getId()) + "[" + i + "]";
                Value value = keys.getElements().get(i);
                if (i < used) {
                    addCell(scene, cellKey, value, x + i * cellW, y, cellW, null);
                } else { // past the count: an empty-looking, faded cell
                    String text = value instanceof NullValue || "0".equals(value.display()) ? "" : value.display();
                    scene.add(new SceneNode(cellKey, SceneNode.Kind.CELL, x + i * cellW, y, cellW, Style.CELL_H,
                            text, null, SceneNode.Look.DIM));
                }
            }
            // Just outside a node there's only a small gap before its neighbour, so stay inside the gap.
            addMarkers(scene, keys.getId(), context.markersFor(keys.getId()), RenderContext.Axis.ROW,
                    x, cellW, y + Style.CELL_H + Style.CHILD_SLOT_H + 2, Style.SUBTREE_GAP / 2.0);
        } else if (tree.hasKeys()) {
            scene.add(new SceneNode(key(id) + ":new", SceneNode.Kind.CELL, x, y, width, Style.CELL_H, "new", null,
                    SceneNode.Look.DIM));
        } else {
            scene.add(new SceneNode(key(id) + ":data", SceneNode.Kind.CELL, x, y, width, Style.CELL_H,
                    tree.getPlainFields(id), null));
        }

        // The child pointers, only under a node that has at least one child.
        List<Long> children = tree.getChildren(id);
        ArrayNode slots = tree.getChildrenArray(id);
        if (slots == null || children.stream().allMatch(child -> child == -1)) {
            return;
        }
        List<Integer> shown = slotsToShow(id);
        boolean allShown = shown.size() == children.size();
        double slotW = width / shown.size();
        for (int place = 0; place < shown.size(); place++) {
            int i = shown.get(place);
            String slotKey = key(slots.getId()) + "[" + i + "]";
            double slotX = x + place * slotW;
            long child = children.get(i);
            if (child == -1) {
                scene.add(new SceneNode(slotKey, SceneNode.Kind.NULL_SLOT, slotX, y + Style.CELL_H, slotW,
                        Style.CHILD_SLOT_H, "", null));
            } else {
                scene.add(new SceneNode(slotKey, SceneNode.Kind.SLOT, slotX, y + Style.CELL_H, slotW,
                        Style.CHILD_SLOT_H, "", allShown ? null : String.valueOf(i)));
                scene.add(new SceneEdge(slotKey + "→", slotKey, key(child), SceneEdge.Kind.ARROW));
            }
        }
    }
}
