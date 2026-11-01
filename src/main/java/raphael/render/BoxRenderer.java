package raphael.render;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import raphael.detect.GenericStructure;
import raphael.model.Names;
import raphael.model.ObjectNode;
import raphael.model.ReferenceValue;
import raphael.model.Value;

// Draws any other object as a box: its class name on top (and its parent class, if it has one of
// the student's), then one line per field. Each field is a shape of its own with its own key, so
// only the field that changed flashes.
// A field holding a reference gets a slot on the right with an arrow to that object.
// OOP: inheritance (extends StructureRenderer) and overriding (render).
public class BoxRenderer extends StructureRenderer {

    private final GenericStructure structure;
    private final RenderContext context;

    public BoxRenderer(GenericStructure structure, RenderContext context) {
        this.structure = structure;
        this.context = context;
    }

    // One line per field: "size = 3", or just the name for a reference (the arrow shows the rest).
    public static List<String> linesOf(ObjectNode object) {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, Value> field : object.getFields().entrySet()) {
            lines.add(field.getValue() instanceof ReferenceValue
                    ? field.getKey()
                    : field.getKey() + " = " + field.getValue().display());
        }
        return lines;
    }

    // The title bar's text, e.g. "Savings extends Account".
    private static String titleOf(ObjectNode object) {
        String name = Names.simple(object.getTypeName());
        return object.getParentName() == null ? name : name + " extends " + Names.simple(object.getParentName());
    }

    // How wide the box must be for its title and its fields.
    public static double widthOf(ObjectNode object) {
        double textW = Style.textWidth(titleOf(object), Style.TITLE_FONT);
        for (String line : linesOf(object)) {
            textW = Math.max(textW, Style.textWidth(line, Style.FIELD_FONT));
        }
        return textW + 2 * Style.BOX_PAD + Style.SLOT_W;
    }

    @Override
    public Dimension render(Scene scene, double x, double y) {
        ObjectNode object = structure.getObject();
        List<String> lines = linesOf(object);
        double top = y + Style.LABEL_SPACE;
        double width = context.boxWidth(object.getId(), widthOf(object)); // the same for the whole run
        double height = Style.TITLE_H + Math.max(lines.size(), 1) * Style.LINE_H + 4;
        scene.add(new SceneNode(key(object.getId()), SceneNode.Kind.OBJECT, x, top, width, height,
                "", titleOf(object)));

        int row = 0;
        for (Map.Entry<String, Value> field : object.getFields().entrySet()) {
            scene.add(new SceneNode(key(object.getId()) + ".line:" + field.getKey(), SceneNode.Kind.FIELD,
                    x + 1, top + Style.TITLE_H + row * Style.LINE_H + 2, width - 1, Style.LINE_H, lines.get(row), null));
            if (field.getValue() instanceof ReferenceValue) {
                String slotKey = key(object.getId()) + "." + field.getKey();
                scene.add(new SceneNode(slotKey, SceneNode.Kind.SLOT, x + width - Style.SLOT_W,
                        top + Style.TITLE_H + row * Style.LINE_H + 2, Style.SLOT_W, Style.LINE_H - 4, "", null));
                scene.add(new SceneEdge(slotKey + "→", slotKey, key(((ReferenceValue) field.getValue()).id()),
                        SceneEdge.Kind.ARROW));
            }
            row++;
        }
        return new Dimension((int) width, (int) (Style.LABEL_SPACE + height));
    }
}
