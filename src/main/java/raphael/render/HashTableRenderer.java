package raphael.render;

import java.awt.Dimension;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import raphael.detect.HashTableStructure;
import raphael.model.ArrayNode;

// Draws a hash table with chaining as textbooks do: the buckets in a column, each bucket's
// index on its left, and its chain of nodes hanging to its right, [ key | • ]──▶ [ key | / ].
// An index variable on the table (b = bucketOf(key)) stands to the left of its bucket.
// OOP: inheritance (extends StructureRenderer) and overriding (render).
public class HashTableRenderer extends StructureRenderer {

    private static final double INDEX_W = 22;
    private static final double CHAIN_GAP = 34;   // from a bucket to the first node of its chain
    private static final double ROW_GAP = 10;

    private final HashTableStructure table;
    private final RenderContext context;

    public HashTableRenderer(HashTableStructure table, RenderContext context) {
        this.table = table;
        this.context = context;
    }

    @Override
    public Dimension render(Scene scene, double x, double y) {
        ArrayNode buckets = table.getTable();
        int rows = buckets.getElements().size();
        double markerW = 0;
        for (RenderContext.Marker marker : context.markersFor(buckets.getId())) {
            markerW = Math.max(markerW, Style.textWidth(marker.name(), Style.MARKER_FONT) + 20);
        }
        double left = x + markerW;
        double top = y + Style.LABEL_SPACE;
        double rowH = Math.max(Style.CELL_H, Style.NODE_H) + ROW_GAP;
        double cellW = Style.CELL_W;
        double cellX = left + INDEX_W;
        String id = key(buckets.getId());
        scene.add(new SceneNode(id, SceneNode.Kind.FRAME, cellX, top, cellW, Math.max(1, rows) * rowH - ROW_GAP, "",
                raphael.model.Names.simple(buckets.getTypeName())));
        double valueW = table.getNodes().isEmpty() ? Style.NODE_W
                : context.nodeWidth(table.getNodes().values().iterator().next().getTypeName(), Style.NODE_W);
        double step = valueW + Style.SLOT_W + Style.LIST_GAP;
        double width = INDEX_W + cellW;
        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < rows; i++) {
            double rowY = top + i * rowH;
            scene.add(new SceneNode(id + "[" + i + "]:index", SceneNode.Kind.TEXT, left, rowY, INDEX_W - 4, Style.CELL_H,
                    String.valueOf(i), null));
            addCell(scene, id + "[" + i + "]", buckets.getElements().get(i), cellX, rowY, cellW, null);
            List<Long> chain = table.chain(i, seen);
            double nodeX = cellX + cellW + CHAIN_GAP;
            for (long node : chain) {
                scene.add(new SceneNode(key(node), SceneNode.Kind.BOX, nodeX, rowY, valueW, Style.NODE_H, table.getLabel(node), null));
                String slot = key(node) + ".next";
                long next = table.getNext(node);
                if (next == -1) {
                    scene.add(new SceneNode(slot, SceneNode.Kind.NULL_SLOT, nodeX + valueW, rowY, Style.SLOT_W, Style.NODE_H, "", null));
                } else {
                    scene.add(new SceneNode(slot, SceneNode.Kind.SLOT, nodeX + valueW, rowY, Style.SLOT_W, Style.NODE_H, "", null));
                    scene.add(new SceneEdge(slot + "→", slot, key(next), SceneEdge.Kind.ARROW));
                }
                nodeX += step;
            }
            width = Math.max(width, nodeX - Style.LIST_GAP - left);
        }
        for (RenderContext.Marker marker : context.markersFor(buckets.getId())) {
            if (marker.axis() == RenderContext.Axis.ROW && marker.inRange()) {
                double w = Style.textWidth(marker.name(), Style.MARKER_FONT) + 16;
                scene.add(new SceneNode("idx:" + buckets.getId() + ":ROW:" + marker.name(), SceneNode.Kind.SIDE_MARKER,
                        left - w - 2, top + marker.index() * rowH + (Style.CELL_H - Style.MARKER_H) / 2.0, w, Style.MARKER_H,
                        marker.name(), null));
            }
        }
        return new Dimension((int) (width + markerW), (int) (Style.LABEL_SPACE + Math.max(1, rows) * rowH));
    }
}
