package raphael.render;

import java.awt.Dimension;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import raphael.detect.GridStructure;
import raphael.model.ArrayNode;
import raphael.model.Names;

// Draws a 2D array as a table: row numbers on the left, column numbers on top. Row markers
// (like "r") sit to the left of the row numbers, column markers (like "c") under the table.
// Each cell's key uses its row array's id ("#9[3]"), so a cell that changes can flash.
// OOP: inheritance (extends StructureRenderer) and overriding (render).
public class GridRenderer extends StructureRenderer {

    private static final int INDEX_W = 26;

    private final GridStructure structure;
    private final RenderContext context;

    public GridRenderer(GridStructure structure, RenderContext context) {
        this.structure = structure;
        this.context = context;
    }

    @Override
    public Dimension render(Scene scene, double x, double y) {
        ArrayNode grid = structure.getGrid();
        List<ArrayNode> rows = structure.getRows();
        String gridKey = key(grid.getId());

        int columns = 0;
        double cellW = Style.CELL_W;
        for (ArrayNode row : rows) {
            if (row != null) {
                columns = Math.max(columns, row.getElements().size());
                cellW = Math.max(cellW, context.cellWidth(row.getId()));
            }
        }
        double markerW = context.needsRowMarkerRoom(grid.getId()) ? Style.ROW_MARKER_W : 0;
        double top = y + Style.LABEL_SPACE + Style.CAPTION_H;
        double left = x + markerW + INDEX_W;
        double width = Math.max(columns, 1) * cellW;
        double bottom = top + rows.size() * Style.CELL_H;

        // The outline includes the column numbers, so a label like "dp" sits above them.
        scene.add(new SceneNode(gridKey, SceneNode.Kind.FRAME, left, top - Style.CAPTION_H, width,
                Style.CAPTION_H + Math.max(rows.size(), 1) * Style.CELL_H, "", Names.simple(grid.getTypeName())));
        for (int c = 0; c < columns; c++) {
            scene.add(new SceneNode(gridKey + ":c" + c, SceneNode.Kind.TEXT, left + c * cellW,
                    top - Style.CAPTION_H, cellW, Style.CAPTION_H, String.valueOf(c), null));
        }
        for (int r = 0; r < rows.size(); r++) {
            double rowY = top + r * Style.CELL_H;
            scene.add(new SceneNode(gridKey + ":r" + r, SceneNode.Kind.TEXT, left - INDEX_W, rowY, INDEX_W - 6,
                    Style.CELL_H, String.valueOf(r), null));
            ArrayNode row = rows.get(r);
            if (row == null) {
                scene.add(new SceneNode(gridKey + "[" + r + "]", SceneNode.Kind.NULL_SLOT, left, rowY,
                        Style.SLOT_W, Style.CELL_H, "", null));
                continue;
            }
            String rowKey = key(row.getId());
            scene.add(new SceneNode(rowKey, SceneNode.Kind.FRAME, left, rowY,
                    row.getElements().size() * cellW, Style.CELL_H, "", null));
            if (row.getLength() == 0) { // an empty list still exists, so its row says so
                scene.add(new SceneNode(rowKey + ":empty", SceneNode.Kind.TEXT, left, rowY, cellW, Style.CELL_H, "(empty)", null));
            }
            for (int c = 0; c < row.getElements().size(); c++) {
                addCell(scene, rowKey + "[" + c + "]", row.getElements().get(c), left + c * cellW, rowY, cellW, null);
            }
        }

        addRowMarkers(scene, grid.getId(), x + markerW, top);
        addMarkers(scene, grid.getId(), context.markersFor(grid.getId()), RenderContext.Axis.COLUMN,
                left, cellW, bottom + 4, cellW / 2);
        int markerRoom = context.markerRows(grid.getId()) * Style.MARKER_H;
        return new Dimension((int) (markerW + INDEX_W + width),
                Style.LABEL_SPACE + Style.CAPTION_H + rows.size() * Style.CELL_H + 4 + markerRoom);
    }

    // Row markers, right-aligned just left of the row numbers. Markers on the same row line up leftwards.
    private void addRowMarkers(Scene scene, long gridId, double rightEdge, double top) {
        Map<Integer, Double> used = new HashMap<>();
        for (RenderContext.Marker marker : context.markersFor(gridId)) {
            if (marker.axis() != RenderContext.Axis.ROW) {
                continue;
            }
            double w = Style.textWidth(marker.name(), Style.MARKER_FONT) + 16;
            double right = rightEdge - used.getOrDefault(marker.index(), 0.0);
            used.merge(marker.index(), w + 2, Double::sum);
            double rowY = top + marker.index() * Style.CELL_H;
            scene.add(new SceneNode("idx:" + gridId + ":ROW:" + marker.name(), SceneNode.Kind.SIDE_MARKER,
                    right - w, rowY + (Style.CELL_H - Style.MARKER_H) / 2.0, w, Style.MARKER_H, marker.name(), null,
                    marker.inRange() ? SceneNode.Look.STILL : SceneNode.Look.DIM));
        }
    }
}
