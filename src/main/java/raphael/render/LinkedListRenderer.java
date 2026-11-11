package raphael.render;

import java.awt.Dimension;
import java.util.List;
import raphael.detect.LinkedListStructure;
import raphael.detect.LinkedStructure;

// Draws a linked list as a row of nodes: [ value | • ]──▶ [ value | / ]
// The arrow leaves the node's "next" slot; a null next is a slot with a slash through it.
// A doubly linked list gets a "prev" slot on the left too: [ • | value | • ]
// Nodes keep their place across steps (see ListOrder), so reversing a list flips the arrows
// while the nodes stay where they are.
// OOP: inheritance (extends StructureRenderer) and overriding (render).
public class LinkedListRenderer extends StructureRenderer {

    private final LinkedListStructure list;
    private final RenderContext context;

    public LinkedListRenderer(LinkedListStructure list, RenderContext context) {
        this.list = list;
        this.context = context;
    }

    @Override
    public Dimension render(Scene scene, double x, double y) {
        List<Long> ids = context.getOrder().arrange(list.getNodes().keySet());
        double top = y + Style.LABEL_SPACE;
        String typeName = list.getNode(ids.get(0)).getTypeName();
        double valueW = context.nodeWidth(typeName, Style.NODE_W); // the same for the whole run
        double prevW = list.isDoublyLinked() ? Style.SLOT_W : 0;
        double step = prevW + valueW + Style.SLOT_W + Style.LIST_GAP;

        for (int i = 0; i < ids.size(); i++) {
            long id = ids.get(i);
            double left = x + i * step;
            if (list.isDoublyLinked()) {
                long prev = list.getPrev(id);
                addSlot(scene, id, "prev", prev, key(prev), left, top, SceneEdge.Kind.PREV_ARROW);
            }
            scene.add(new SceneNode(key(id), SceneNode.Kind.BOX, left + prevW, top, valueW, Style.NODE_H,
                    list.getLabel(id), null));
            // In a doubly linked list, next arrows end at the node's left edge (its prev slot),
            // so the arrowhead isn't hidden under that slot.
            long next = list.getNext(id);
            String nextTarget = list.isDoublyLinked() ? key(next) + ".prev" : key(next);
            addSlot(scene, id, "next", next, nextTarget, left + prevW + valueW, top, SceneEdge.Kind.ARROW);
        }
        double width = Math.max(ids.size() * step - Style.LIST_GAP, 0);
        return new Dimension((int) width, Style.LABEL_SPACE + Style.NODE_H + Style.CURVE_SPACE);
    }

    // Whether the node's link field (prev-like if back) holds memory nobody has set (shown "?").
    private boolean unset(long id, boolean back) {
        raphael.model.ObjectNode node = list.getNode(id);
        for (java.util.Map.Entry<String, raphael.model.Value> field : node.getFields().entrySet()) {
            if (node.getTypeName().equals(node.getFieldType(field.getKey())) && LinkedStructure.isBackLink(field.getKey()) == back
                    && "?".equals(field.getValue().display())) {
                return true;
            }
        }
        return false;
    }

    private void addSlot(Scene scene, long id, String name, long target, String targetKey,
                         double x, double y, SceneEdge.Kind arrow) {
        String slotKey = key(id) + "." + name;
        if (target == -1 && unset(id, name.equals("prev"))) {
            // C: malloc'd and not set yet. A slash would say NULL, as if malloc cleared memory; it doesn't.
            scene.add(new SceneNode(slotKey, SceneNode.Kind.CELL, x, y, Style.SLOT_W, Style.NODE_H, "?", null));
        } else if (target == -1) {
            scene.add(new SceneNode(slotKey, SceneNode.Kind.NULL_SLOT, x, y, Style.SLOT_W, Style.NODE_H, "", null));
        } else {
            scene.add(new SceneNode(slotKey, SceneNode.Kind.SLOT, x, y, Style.SLOT_W, Style.NODE_H, "", null));
            scene.add(new SceneEdge(slotKey + "→", slotKey, targetKey, arrow));
        }
    }
}
