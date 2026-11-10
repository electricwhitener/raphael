package raphael.render;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import raphael.detect.BinaryTreeStructure;

// Draws a binary tree. A node's x comes from its in-order position (left subtree, then the node,
// then the right subtree) and its y from its depth. That's why a BST reads in sorted order from
// left to right, and why every node has its own column.
// OOP: inheritance (extends StructureRenderer) and overriding (render).
public class BinaryTreeRenderer extends StructureRenderer {

    private final BinaryTreeStructure tree;
    private final RenderContext context;
    private final List<Long> inOrder = new ArrayList<>();
    private final Map<Long, Integer> depth = new HashMap<>();

    public BinaryTreeRenderer(BinaryTreeStructure tree, RenderContext context) {
        this.tree = tree;
        this.context = context;
    }

    @Override
    public Dimension render(Scene scene, double x, double y) {
        walk(tree.getRoot(), 0);
        for (long id : tree.getNodes().keySet()) {
            if (!depth.containsKey(id)) { // not reachable from the root (only in a broken tree)
                depth.put(id, 0);
                inOrder.add(id);
            }
        }

        double size = context.nodeWidth(tree.getNode(tree.getRoot()).getTypeName(), Style.CIRCLE);
        double top = y + Style.LABEL_SPACE;
        int maxDepth = 0;
        for (int i = 0; i < inOrder.size(); i++) {
            long id = inOrder.get(i);
            int d = depth.get(id);
            maxDepth = Math.max(maxDepth, d);
            raphael.model.ObjectNode node = tree.getNode(id);
            String tone = raphael.detect.LinkedStructure.toneOf(node);
            SceneNode.Kind kind = "red".equals(tone) ? SceneNode.Kind.RED_NODE : "black".equals(tone) ? SceneNode.Kind.BLACK_NODE
                    : SceneNode.Kind.CIRCLE;
            double nx = x + i * (size + Style.TREE_X_GAP);
            double ny = top + d * Style.TREE_LEVEL_H;
            scene.add(new SceneNode(key(id), kind, nx, ny, size, size, tree.getLabel(id), null));
            String caption = raphael.detect.LinkedStructure.captionOf(node); // an AVL node's height, under it
            if (caption != null) {
                scene.add(new SceneNode(key(id) + ":h", SceneNode.Kind.TEXT, nx - 6, ny + size + 1, size + 12, Style.CAPTION_H,
                        caption, null));
            }
        }
        for (long id : inOrder) {
            addLink(scene, id, "left", tree.getLeft(id));
            addLink(scene, id, "right", tree.getRight(id));
        }
        double width = inOrder.size() * (size + Style.TREE_X_GAP) - Style.TREE_X_GAP;
        return new Dimension((int) width, (int) (Style.LABEL_SPACE + maxDepth * Style.TREE_LEVEL_H + size));
    }

    // A child one level down is a normal tree line; anything else is a bug, so it's drawn as an arrow.
    private void addLink(Scene scene, long id, String side, long child) {
        if (child == -1) {
            return;
        }
        boolean normal = depth.get(child) == depth.get(id) + 1;
        scene.add(new SceneEdge(key(id) + "." + side + "→", key(id), key(child),
                normal ? SceneEdge.Kind.TREE : SceneEdge.Kind.ARROW));
    }

    // In-order walk that records each node's depth. A node already seen (a cycle) is skipped.
    private void walk(long id, int d) {
        if (id == -1 || depth.containsKey(id)) {
            return;
        }
        depth.put(id, d);
        walk(tree.getLeft(id), d + 1);
        inOrder.add(id);
        walk(tree.getRight(id), d + 1);
    }
}
