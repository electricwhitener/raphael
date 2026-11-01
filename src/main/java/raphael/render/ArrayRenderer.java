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
        for (int i = 0; i < shown; i++) {
            addCell(scene, key(array.getId()) + "[" + i + "]", array.getElements().get(i),
                    x + i * cellW, top, cellW, String.valueOf(i));
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
        return new Dimension((int) width, Style.LABEL_SPACE + Style.CELL_H + Style.CAPTION_H + markerRoom);
    }
}
