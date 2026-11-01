package raphael.render;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// What the renderers need besides the structure itself:
//   - where linked-list nodes sit (ListOrder),
//   - how wide each array's cells and each kind of node are over the WHOLE run, so nothing jumps
//     when a value gets longer (9 → 10), and how long each array gets, so a growing list doesn't
//     push the structure beside it,
//   - how much room the arrays at the top ever need, so a new array doesn't push a tree down,
//   - which index markers (like i and j) to draw at the step being drawn, and how much room to
//     keep for them, so arrays don't move when a marker appears.
// OOP: encapsulation. The maps are private, and the getters give sensible defaults.
public class RenderContext {

    public enum Axis { ROW, COLUMN }

    // One index marker, e.g. "j" at index 3 of an array. Out of range means just past either end.
    public record Marker(String name, int index, Axis axis, boolean inRange) {
    }

    private final ListOrder order = new ListOrder();
    private final Map<Long, Double> cellWidths = new HashMap<>();   // array id → widest cell
    private final Map<String, Double> nodeWidths = new HashMap<>(); // class name → widest node
    private final Map<Long, Double> boxWidths = new HashMap<>();    // object id → widest box
    private final Map<Long, Integer> longest = new HashMap<>();     // array id → most cells it ever has
    private final Map<Long, Integer> markerRows = new HashMap<>();  // array id → rows of markers to leave room for
    private final Map<Long, Boolean> rowMarkerRoom = new HashMap<>(); // grid id → needs room for row markers
    private Map<Long, List<Marker>> markers = new HashMap<>();      // at the step being drawn
    private double arraysBottom;                                    // the most room the arrays ever need

    public ListOrder getOrder() {
        return order;
    }

    public void widenCells(long arrayId, double width) {
        cellWidths.merge(arrayId, width, Math::max);
    }

    public void growTo(long arrayId, int cells) {
        longest.merge(arrayId, cells, Math::max);
    }

    public int longestLength(long arrayId) {
        return longest.getOrDefault(arrayId, 0);
    }

    public void reserveArrays(double bottom) {
        arraysBottom = Math.max(arraysBottom, bottom);
    }

    public double getArraysBottom() {
        return arraysBottom;
    }

    public void widenNodes(String typeName, double width) {
        nodeWidths.merge(typeName, width, Math::max);
    }

    public void widenBox(long objectId, double width) {
        boxWidths.merge(objectId, width, Math::max);
    }

    // Widths are whole pixels, so thin lines stay sharp when shapes are standing still.
    public double cellWidth(long arrayId) {
        return Math.ceil(Math.max(Style.CELL_W, cellWidths.getOrDefault(arrayId, 0.0)));
    }

    public double nodeWidth(String typeName, double minimum) {
        return Math.ceil(Math.max(minimum, nodeWidths.getOrDefault(typeName, 0.0)));
    }

    public double boxWidth(long objectId, double minimum) {
        return Math.ceil(Math.max(minimum, boxWidths.getOrDefault(objectId, 0.0)));
    }

    // Grows the room kept for markers so these markers will fit (called for every step first).
    public void makeRoomFor(Map<Long, List<Marker>> stepMarkers) {
        for (Map.Entry<Long, List<Marker>> entry : stepMarkers.entrySet()) {
            Map<Integer, Integer> perIndex = new HashMap<>();
            for (Marker marker : entry.getValue()) {
                if (marker.axis() == Axis.ROW) {
                    rowMarkerRoom.put(entry.getKey(), true);
                }
                int stacked = perIndex.merge(marker.index() * 2 + marker.axis().ordinal(), 1, Integer::sum);
                markerRows.merge(entry.getKey(), stacked, Math::max);
            }
        }
    }

    public void setMarkers(Map<Long, List<Marker>> stepMarkers) {
        this.markers = stepMarkers;
    }

    public List<Marker> markersFor(long arrayId) {
        return markers.getOrDefault(arrayId, List.of());
    }

    public int markerRows(long arrayId) {
        return markerRows.getOrDefault(arrayId, 0);
    }

    public boolean needsRowMarkerRoom(long gridId) {
        return rowMarkerRoom.getOrDefault(gridId, false);
    }
}
