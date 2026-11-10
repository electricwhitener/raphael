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
        int tableW = (int) (markerW + INDEX_W + width);
        int tableH = Style.LABEL_SPACE + Style.CAPTION_H + rows.size() * Style.CELL_H + 4 + markerRoom;
        if (context.isGraph(grid.getId())) {
            Dimension drawn = addGraph(scene, x + tableW + Style.GRAPH_GAP, y + Style.LABEL_SPACE);
            return new Dimension(tableW + Style.GRAPH_GAP + drawn.width, Math.max(tableH, Style.LABEL_SPACE + drawn.height));
        }
        return new Dimension(tableW, tableH);
    }

    // The same table drawn as the graph it stands for, beside it: the vertices on a circle,
    // vertex 0 at the top, an edge as a line when it goes both ways and an arrow when it doesn't,
    // with its weight if the matrix holds weights. Visited vertices are grey, a vertex's distance
    // is under it (∞ for one not reached yet), and index markers (u, v) stand beside their vertex.
    private Dimension addGraph(Scene scene, double x, double y) {
        ArrayNode grid = structure.getGrid();
        List<ArrayNode> rows = structure.getRows();
        int n = Math.max(rows.size(), context.graphVertices(grid.getId()));
        double size = Style.CIRCLE;
        double radius = Math.max(Style.GRAPH_MIN_RADIUS, n * (size + 14) / (2 * Math.PI));
        double cx = x + radius + size / 2 + 30;
        double cy = y + radius + size / 2;
        String id = "graph:" + key(grid.getId());
        RenderContext.GraphState state = context.graphState(grid.getId());
        boolean matrix = context.isMatrixGraph(grid.getId());
        // the edges: from row r to each neighbour (a list), or to each column with a weight (a matrix)
        Map<String, String> edges = new java.util.LinkedHashMap<>(); // "a>b" → its weight, or ""
        boolean weighted = false;
        for (int r = 0; r < rows.size(); r++) {
            ArrayNode row = rows.get(r);
            if (row == null) {
                continue;
            }
            for (int c = 0; c < row.getElements().size(); c++) {
                String text = row.getElements().get(c).display();
                if (matrix) {
                    if (!text.equals("0") && !text.equals("false") && !infinite(text)) {
                        edges.put(r + ">" + c, text);
                        weighted |= !text.equals("1") && !text.equals("true");
                    }
                } else {
                    edges.put(r + ">" + text, "");
                }
            }
        }
        for (int v = 0; v < n; v++) {
            double angle = -Math.PI / 2 + 2 * Math.PI * v / n;
            double vx = cx + radius * Math.cos(angle);
            double vy = cy + radius * Math.sin(angle);
            boolean visited = state.visited() != null && v < state.visited().size()
                    && state.visited().get(v).display().equals("true");
            scene.add(new SceneNode(id + "[" + v + "]", visited ? SceneNode.Kind.VISITED_NODE : SceneNode.Kind.CIRCLE,
                    vx - size / 2, vy - size / 2, size, size, String.valueOf(v), null));
            if (state.dist() != null && v < state.dist().size()) {
                String d = state.dist().get(v).display();
                scene.add(new SceneNode(id + "[" + v + "]:d", SceneNode.Kind.TEXT, vx - size, vy + size / 2 + 1, 2 * size,
                        Style.CAPTION_H, "d=" + (infinite(d) ? "∞" : d), null));
            }
            int stacked = 0;
            for (RenderContext.Marker marker : context.markersFor(grid.getId())) {
                if (marker.axis() == RenderContext.Axis.ROW && marker.inRange() && marker.index() == v) {
                    double w = Style.textWidth(marker.name(), Style.MARKER_FONT) + 10;
                    boolean right = Math.cos(angle) >= -0.01; // on the outside of the circle
                    scene.add(new SceneNode(id + ":idx:" + marker.name(), SceneNode.Kind.MARKER,
                            right ? vx + size / 2 + 3 : vx - size / 2 - 3 - w, vy - Style.MARKER_H / 2.0 + stacked * Style.MARKER_H,
                            w, Style.MARKER_H, marker.name(), null));
                    stacked++;
                }
            }
        }
        for (Map.Entry<String, String> edge : edges.entrySet()) {
            String[] ends = edge.getKey().split(">");
            int a = Integer.parseInt(ends[0]);
            int b = Integer.parseInt(ends[1]);
            boolean both = edges.containsKey(b + ">" + a) && edges.get(b + ">" + a).equals(edge.getValue());
            if (both && a > b) {
                continue; // drawn once, as a line
            }
            scene.add(new SceneEdge(id + ":e" + a + "-" + b, id + "[" + a + "]", id + "[" + b + "]",
                    both ? SceneEdge.Kind.TREE : SceneEdge.Kind.ARROW));
            if (weighted) {
                double ax = cx + radius * Math.cos(-Math.PI / 2 + 2 * Math.PI * a / n);
                double ay = cy + radius * Math.sin(-Math.PI / 2 + 2 * Math.PI * a / n);
                double bx = cx + radius * Math.cos(-Math.PI / 2 + 2 * Math.PI * b / n);
                double by = cy + radius * Math.sin(-Math.PI / 2 + 2 * Math.PI * b / n);
                double w = Style.textWidth(edge.getValue(), Style.CAPTION_FONT) + 8;
                double length = Math.max(1, Math.hypot(bx - ax, by - ay));
                double nx = -(by - ay) / length * 9; // beside the line, not on it
                double ny = (bx - ax) / length * 9;
                scene.add(new SceneNode(id + ":w" + a + "-" + b, SceneNode.Kind.TEXT, (ax + bx) / 2 + nx - w / 2,
                        (ay + by) / 2 + ny - Style.CAPTION_H / 2.0, w, Style.CAPTION_H, edge.getValue(), null));
            }
        }
        return new Dimension((int) (2 * radius + size + 60), (int) (2 * radius + size + Style.CAPTION_H + 4));
    }

    // 999, Integer.MAX_VALUE and the like: "no edge" or "not reached yet".
    private static boolean infinite(String text) {
        try {
            long value = Long.parseLong(text);
            return value >= 999 && String.valueOf(value).chars().allMatch(c -> c == '9') || value >= Integer.MAX_VALUE;
        } catch (NumberFormatException notANumber) {
            return text.equals("Infinity");
        }
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
