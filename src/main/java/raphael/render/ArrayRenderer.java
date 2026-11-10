package raphael.render;

import java.awt.Dimension;
import raphael.detect.ArrayStructure;
import raphael.model.ArrayNode;
import raphael.model.Names;

// Draws an array as a row of cells with each index underneath, and index markers (like "j")
// under that. Every cell has its own key ("#7[2]"), so a cell whose value changes can flash.
// An index just outside the array gets a faded empty ghost cell, showing where it stopped.
// OOP: inheritance (extends StructureRenderer) and overriding (render).
public class ArrayRenderer extends StructureRenderer {

    private final ArrayStructure structure;
    private final RenderContext context;

    public ArrayRenderer(ArrayStructure structure, RenderContext context) {
        this.structure = structure;
        this.context = context;
    }

    @Override
    public Dimension render(Scene scene, double x, double y) {
        if (context.isStack(structure.getArray().getId())) {
            return renderUpright(scene, x, y);
        }
        ArrayNode array = structure.getArray();
        int shown = array.getElements().size();
        double top = y + Style.LABEL_SPACE;
        double cellW = context.cellWidth(array.getId());
        double width = Math.max(shown, 1) * cellW;

        // An invisible outline of the whole array, for labels like "arr" to point at.
        scene.add(new SceneNode(key(array.getId()), SceneNode.Kind.FRAME, x, top, width, Style.CELL_H,
                "", Names.simple(array.getTypeName())));
        // A heap's cells past its size aren't in the heap (sorted, in heap sort, or unused): faded.
        int inHeap = context.isHeap(array.getId()) ? context.heapSize(array.getId(), shown) : shown;
        for (int i = 0; i < shown; i++) {
            addCell(scene, key(array.getId()) + "[" + i + "]", array.getElements().get(i),
                    x + i * cellW, top, cellW, String.valueOf(i), i < inHeap ? SceneNode.Look.STILL : SceneNode.Look.DIM);
        }
        if (array.getLength() == 0) {
            scene.add(new SceneNode(key(array.getId()) + ":empty", SceneNode.Kind.TEXT,
                    x, top, width, Style.CELL_H, "(empty)", null));
        } else if (shown < array.getLength()) {
            scene.add(new SceneNode(key(array.getId()) + ":more", SceneNode.Kind.TEXT,
                    x + width + 8, top, 120, Style.CELL_H, "… " + array.getLength() + " cells", null));
            width += 128;
        }
        addMarkers(scene, array.getId(), context.markersFor(array.getId()), RenderContext.Axis.ROW,
                x, cellW, top + Style.CELL_H + Style.CAPTION_H + 2, cellW / 2);
        for (RenderContext.Marker marker : context.markersFor(array.getId())) {
            if (!marker.inRange() && array.getLength() > 0 && shown == array.getLength()) {
                scene.add(new SceneNode(key(array.getId()) + ":ghost" + marker.index(), SceneNode.Kind.CELL,
                        x + marker.index() * cellW, top, cellW, Style.CELL_H, "", String.valueOf(marker.index()),
                        SceneNode.Look.DIM));
            }
        }
        int markerRoom = context.markerRows(array.getId()) * Style.MARKER_H;
        width = Math.max(width, context.longestLength(array.getId()) * cellW); // room to grow into
        int height = Style.LABEL_SPACE + Style.CELL_H + Style.CAPTION_H + markerRoom;
        if (context.isForest(array.getId())) {
            Dimension forest = addForest(scene, array, x, top + height - Style.LABEL_SPACE + Style.HEAP_GAP);
            width = Math.max(width, forest.width);
            height += Style.HEAP_GAP + forest.height;
        }
        if (context.isHeap(array.getId())) {
            Dimension tree = addHeapTree(scene, array, x, top + height - Style.LABEL_SPACE + Style.HEAP_GAP);
            width = Math.max(width, tree.width);
            height += Style.HEAP_GAP + tree.height;
        }
        return new Dimension((int) width, height);
    }

    // A stack: the same cells standing in a column, index 0 at the bottom, so push and pop happen
    // at the top, as in a textbook. The index is on the left, and top stands on the right of its cell.
    private Dimension renderUpright(Scene scene, double x, double y) {
        ArrayNode array = structure.getArray();
        int length = Math.max(1, context.longestLength(array.getId()));
        double cellW = context.cellWidth(array.getId());
        double indexW = 22;
        double markerW = 60; // room on the left for "top", pointing at its cell
        for (RenderContext.Marker marker : context.markersFor(array.getId())) {
            markerW = Math.max(markerW, Style.textWidth(marker.name() + "=" + marker.index(), Style.MARKER_FONT) + 22);
        }
        double cellX = x + markerW;
        double top = y + Style.LABEL_SPACE;
        String id = key(array.getId());
        scene.add(new SceneNode(id, SceneNode.Kind.FRAME, cellX, top, cellW, length * Style.CELL_H, "",
                Names.simple(array.getTypeName())));
        for (int i = 0; i < array.getElements().size(); i++) {
            double cellY = top + (length - 1 - i) * Style.CELL_H;
            addCell(scene, id + "[" + i + "]", array.getElements().get(i), cellX, cellY, cellW, null);
            scene.add(new SceneNode(id + "[" + i + "]:index", SceneNode.Kind.TEXT, cellX + cellW + 4, cellY, indexW - 4, Style.CELL_H,
                    String.valueOf(i), null));
        }
        java.util.Map<Integer, Double> used = new java.util.HashMap<>();
        for (RenderContext.Marker marker : context.markersFor(array.getId())) {
            if (marker.axis() != RenderContext.Axis.ROW) {
                continue;
            }
            String text = marker.inRange() ? marker.name() : marker.name() + "=" + marker.index();
            double w = Style.textWidth(text, Style.MARKER_FONT) + 16;
            int row = Math.max(-1, Math.min(length, marker.index())); // past either end: just outside the column
            double right = cellX - 4 - used.getOrDefault(row, 0.0);
            used.merge(row, w + 2, Double::sum);
            scene.add(new SceneNode("idx:" + array.getId() + ":ROW:" + marker.name(), SceneNode.Kind.SIDE_MARKER, right - w,
                    top + (length - 1 - row) * Style.CELL_H + (Style.CELL_H - Style.MARKER_H) / 2.0, w, Style.MARKER_H, text, null));
        }
        // a top past the last cell (a full stack) or at -1 (an empty one) sits a row outside the column
        return new Dimension((int) (markerW + cellW + indexW + 4), (int) (Style.LABEL_SPACE + (length + 1) * Style.CELL_H));
    }

    // A parent array (union-find) as the forest it stands for: a cell that is its own parent is a
    // root, and every other cell hangs under its parent. Each tree's leaves take the next slots
    // along, and a parent sits over its children, so path compression shows as nodes moving up.
    private Dimension addForest(Scene scene, ArrayNode array, double x, double top) {
        int n = array.getElements().size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            try {
                parent[i] = Integer.parseInt(array.getElements().get(i).display());
            } catch (NumberFormatException notAnIndex) {
                parent[i] = i;
            }
            if (parent[i] < 0 || parent[i] >= n) {
                parent[i] = i;
            }
        }
        java.util.List<java.util.List<Integer>> children = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            children.add(new java.util.ArrayList<>());
        }
        for (int i = 0; i < n; i++) {
            if (parent[i] != i) {
                children.get(parent[i]).add(i);
            }
        }
        double size = Style.CIRCLE;
        double slot = size + Style.TREE_X_GAP;
        double[] at = new double[n];
        int[] depth = new int[n];
        double[] next = {0};
        int deepest = 0;
        boolean[] placed = new boolean[n];
        for (int i = 0; i < n; i++) {
            if (parent[i] == i) {
                deepest = Math.max(deepest, place(i, 0, children, at, depth, next, slot, placed));
            }
        }
        String id = key(array.getId());
        for (int i = 0; i < n; i++) {
            if (!placed[i]) {
                continue; // in a loop of parents (a bug in the program): not part of any tree
            }
            scene.add(new SceneNode("forest:" + id + "[" + i + "]", SceneNode.Kind.CIRCLE, x + at[i], top + depth[i] * Style.HEAP_LEVEL_H,
                    size, size, String.valueOf(i), null));
            if (parent[i] != i) {
                scene.add(new SceneEdge("forest:" + id + "~" + i, "forest:" + id + "[" + parent[i] + "]", "forest:" + id + "[" + i + "]",
                        SceneEdge.Kind.TREE));
            }
        }
        return new Dimension((int) Math.max(next[0], slot), (int) (deepest * Style.HEAP_LEVEL_H + size));
    }

    private static int place(int node, int level, java.util.List<java.util.List<Integer>> children, double[] at, int[] depth,
                             double[] next, double slot, boolean[] placed) {
        placed[node] = true;
        depth[node] = level;
        int deepest = level;
        java.util.List<Integer> kids = children.get(node);
        if (kids.isEmpty()) {
            at[node] = next[0];
            next[0] += slot;
            return level;
        }
        for (int kid : kids) {
            deepest = Math.max(deepest, place(kid, level + 1, children, at, depth, next, slot, placed));
        }
        at[node] = (at[kids.get(0)] + at[kids.get(kids.size() - 1)]) / 2;
        return deepest;
    }

    // The same cells as the tree they stand for: cell i's children are cells 2i+1 and 2i+2, so
    // level d holds cells 2^d - 1 to 2^(d+1) - 2. Only the cells still in the heap are in the tree
    // (in heap sort the sorted end leaves it). Each node has its own key ("heap:#7[2]") and the
    // same value as its cell, so a swap flashes in both. The array's index markers (i, largest)
    // stand beside their nodes too.
    private Dimension addHeapTree(Scene scene, ArrayNode array, double x, double top) {
        int depth = context.heapDepth(array.getId());
        double size = Math.max(Style.CIRCLE, context.cellWidth(array.getId()) - 6);
        double slot = size + Style.TREE_X_GAP;
        double treeW = (1 << depth) * slot - Style.TREE_X_GAP;
        int inHeap = context.heapSize(array.getId(), array.getElements().size());
        String id = key(array.getId());
        for (int i = 0; i < inHeap; i++) {
            int level = 31 - Integer.numberOfLeadingZeros(i + 1);
            int across = i + 1 - (1 << level);
            double cx = x + (across + 0.5) * treeW / (1 << level);
            double cy = top + level * Style.HEAP_LEVEL_H + size / 2;
            scene.add(new SceneNode("heap:" + id + "[" + i + "]", SceneNode.Kind.CIRCLE, cx - size / 2, cy - size / 2, size, size,
                    array.getElements().get(i).display(), null));
            if (i > 0) {
                scene.add(new SceneEdge("heap:" + id + "~" + i, "heap:" + id + "[" + ((i - 1) / 2) + "]", "heap:" + id + "[" + i + "]",
                        SceneEdge.Kind.TREE));
            }
            int stacked = 0;
            for (RenderContext.Marker marker : context.markersFor(array.getId())) {
                if (marker.axis() == RenderContext.Axis.ROW && marker.inRange() && marker.index() == i) {
                    double w = Style.textWidth(marker.name(), Style.MARKER_FONT) + 10;
                    scene.add(new SceneNode("heapidx:" + array.getId() + ":" + marker.name(), SceneNode.Kind.MARKER,
                            cx + size / 2 + 3, cy - Style.MARKER_H / 2.0 + stacked * Style.MARKER_H, w, Style.MARKER_H, marker.name(), null));
                    stacked++;
                }
            }
        }
        return new Dimension((int) (treeW + 40), (int) (depth * Style.HEAP_LEVEL_H + size));
    }
}
