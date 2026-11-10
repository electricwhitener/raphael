package raphael.render;

import java.awt.Dimension;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.function.BiFunction;
import java.util.List;
import java.util.Map;
import raphael.detect.ArrayStructure;
import raphael.detect.BinaryTreeStructure;
import raphael.detect.GenericStructure;
import raphael.detect.GridStructure;
import raphael.detect.HashTableStructure;
import raphael.detect.LinkedListStructure;
import raphael.detect.MultiwayTreeStructure;
import raphael.detect.Structure;
import raphael.model.NullValue;
import raphael.model.PrimitiveValue;
import raphael.model.ReferenceValue;
import raphael.model.Value;

// Turns one Structure into shapes in a Scene, with its top-left corner at (x, y).
// OOP: abstract class + polymorphism. Each subclass lays out its own kind of structure, and
// SceneBuilder just calls render() without knowing which one it has.
// Factory pattern: forStructure() picks the right subclass from a registry, so no one else has to.
public abstract class StructureRenderer {

    // Adds this structure's shapes to the scene and returns the size it used.
    public abstract Dimension render(Scene scene, double x, double y);

    // Which renderer draws which kind of structure. A new kind of structure needs one line here.
    // Registry pattern: a lookup table instead of a chain of instanceof tests, so adding a kind of
    // structure doesn't mean editing a chain, and a forgotten one is a clear error, not a wrong cast.
    private static final Map<Class<? extends Structure>, BiFunction<Structure, RenderContext, StructureRenderer>> RENDERERS =
            new LinkedHashMap<>();

    static {
        RENDERERS.put(GridStructure.class, (s, c) -> new GridRenderer((GridStructure) s, c));
        RENDERERS.put(ArrayStructure.class, (s, c) -> new ArrayRenderer((ArrayStructure) s, c));
        RENDERERS.put(LinkedListStructure.class, (s, c) -> new LinkedListRenderer((LinkedListStructure) s, c));
        RENDERERS.put(BinaryTreeStructure.class, (s, c) -> new BinaryTreeRenderer((BinaryTreeStructure) s, c));
        RENDERERS.put(MultiwayTreeStructure.class, (s, c) -> new MultiwayTreeRenderer((MultiwayTreeStructure) s, c));
        RENDERERS.put(HashTableStructure.class, (s, c) -> new HashTableRenderer((HashTableStructure) s, c));
        RENDERERS.put(GenericStructure.class, (s, c) -> new BoxRenderer((GenericStructure) s, c));
    }

    public static StructureRenderer forStructure(Structure structure, RenderContext context) {
        BiFunction<Structure, RenderContext, StructureRenderer> make = RENDERERS.get(structure.getClass());
        if (make == null) {
            throw new IllegalArgumentException("No renderer for " + structure.getKind());
        }
        return make.apply(structure, context);
    }

    // The key of an object's main shape, e.g. "#12".
    protected static String key(long id) {
        return "#" + id;
    }

    // One cell that holds a value: the value as text, or a pointer slot with an arrow if it's a reference.
    protected static void addCell(Scene scene, String key, Value value, double x, double y, double w, String caption) {
        addCell(scene, key, value, x, y, w, caption, SceneNode.Look.STILL);
    }

    // The same, faded or not (a heap's cells past its size are faded).
    protected static void addCell(Scene scene, String key, Value value, double x, double y, double w, String caption,
                                  SceneNode.Look look) {
        if (value instanceof ReferenceValue) {
            scene.add(new SceneNode(key, SceneNode.Kind.SLOT, x, y, w, Style.CELL_H, "", caption, look));
            scene.add(new SceneEdge(key + "→", key, key(((ReferenceValue) value).id()), SceneEdge.Kind.ARROW));
        } else if (value instanceof NullValue) {
            scene.add(new SceneNode(key, SceneNode.Kind.NULL_SLOT, x, y, w, Style.CELL_H, "", caption, look));
        } else {
            scene.add(new SceneNode(key, SceneNode.Kind.CELL, x, y, w, Style.CELL_H, value.display(), caption, look));
        }
    }

    // How wide a cell must be for the longest of these values. References are drawn as arrows, so they don't count.
    public static double widestValue(List<Value> values) {
        double width = Style.CELL_W;
        for (Value value : values) {
            if (value instanceof PrimitiveValue) {
                width = Math.max(width, Style.textWidth(value.display(), Style.VALUE_FONT) + 14);
            }
        }
        return width;
    }

    // Index markers under a row of cells: each marker sits under its cell, and markers on the
    // same cell stack downwards. firstCellX and cellW place index 0; y is the first marker row.
    // An index just outside the array (-1 or the length, often why a loop stops) sits "outside"
    // pixels past that end, at full strength: it's a real value, not a mistake. No cell shows what
    // it is there, so the marker says it: i=-1.
    protected static void addMarkers(Scene scene, long arrayId, List<RenderContext.Marker> markers,
                                     RenderContext.Axis axis, double firstCellX, double cellW, double y, double outside) {
        Map<Integer, Integer> stacked = new HashMap<>();
        for (RenderContext.Marker marker : markers) {
            if (marker.axis() != axis) {
                continue;
            }
            int level = stacked.merge(marker.index(), 1, Integer::sum) - 1;
            String text = marker.inRange() ? marker.name() : marker.name() + "=" + marker.index();
            double w = Style.textWidth(text, Style.MARKER_FONT) + 10;
            double centerX = marker.inRange() ? firstCellX + (marker.index() + 0.5) * cellW
                    : marker.index() < 0 ? firstCellX - outside : firstCellX + marker.index() * cellW + outside;
            scene.add(new SceneNode("idx:" + arrayId + ":" + axis + ":" + marker.name(), SceneNode.Kind.MARKER,
                    centerX - w / 2, y + level * Style.MARKER_H, w, Style.MARKER_H, text, null));
        }
    }
}
