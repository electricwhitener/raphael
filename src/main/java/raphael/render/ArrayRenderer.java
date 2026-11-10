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
        if (context.isHeap(array.getId())) {
            Dimension tree = addHeapTree(scene, array, x, top + height - Style.LABEL_SPACE + Style.HEAP_GAP);
            width = Math.max(width, tree.width);
            height += Style.HEAP_GAP + tree.height;
        }
        return new Dimension((int) width, height);
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
