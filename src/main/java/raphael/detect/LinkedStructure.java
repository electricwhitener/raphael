package raphael.detect;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import raphael.model.ObjectNode;
import raphael.model.ReferenceValue;
import raphael.model.Value;

// Objects of one class joined by fields that point at that same class: linked lists and trees.
// This holds what both have in common: the nodes, following a link, and the text inside a node.
// OOP: an abstract class in the middle of a multilevel hierarchy:
// Structure → LinkedStructure → LinkedListStructure / BinaryTreeStructure.
public abstract class LinkedStructure extends Structure {

    private final Map<Long, ObjectNode> nodes; // id → node

    protected LinkedStructure(Map<Long, ObjectNode> nodes) {
        this.nodes = Collections.unmodifiableMap(new LinkedHashMap<>(nodes));
    }

    public Map<Long, ObjectNode> getNodes() {
        return nodes;
    }

    public ObjectNode getNode(long id) {
        return nodes.get(id);
    }

    @Override
    public Set<Long> getObjectIds() {
        return nodes.keySet();
    }

    // The id that this node's link field points at, or -1 if it's null (or points outside).
    protected long follow(long id, String field) {
        Value value = nodes.get(id).getField(field);
        if (value instanceof ReferenceValue) {
            long target = ((ReferenceValue) value).id();
            if (nodes.containsKey(target)) {
                return target;
            }
        }
        return -1;
    }

    // The data inside a node: every field that isn't a link. The first is bare, and the rest are
    // named by their first letter, because a number needs a name to mean anything: "20 h=3".
    public String getLabel(long id) {
        return labelOf(nodes.get(id));
    }

    // The same for any object: the fields that don't point at its own class.
    // A link back up the tree (x.parent, CLRS's x.p): it doesn't make the shape, and isn't drawn,
    // since the tree's own lines already show who is whose parent.
    private static final java.util.Set<String> BACK_LINKS = java.util.Set.of("parent", "p", "up", "par");
    private static final java.util.Set<String> COLOUR_NAMES = java.util.Set.of("color", "colour", "red", "isred", "black", "isblack");
    private static final java.util.Set<String> HEIGHT_NAMES = java.util.Set.of("height", "h", "ht");

    public static boolean isBackLink(String field) {
        return BACK_LINKS.contains(field.toLowerCase(java.util.Locale.ROOT));
    }

    private static boolean isColour(String field) {
        return COLOUR_NAMES.contains(field.toLowerCase(java.util.Locale.ROOT));
    }

    // A red-black node's colour: "red", "black", or null for a node that has none. Read from a
    // field named color (RED/BLACK, 'R'/'B', "red", or a boolean that's true for red, as in
    // Sedgewick's RED = true), red / isRed (true is red), or black / isBlack (true is black).
    public static String toneOf(ObjectNode node) {
        for (Map.Entry<String, Value> field : node.getFields().entrySet()) {
            String name = field.getKey().toLowerCase(java.util.Locale.ROOT);
            if (!isColour(name)) {
                continue;
            }
            String text = field.getValue().display().replace("'", "").replace("\"", "").toUpperCase(java.util.Locale.ROOT);
            boolean says = text.equals("TRUE");
            if (text.equals("FALSE") || text.equals("TRUE")) {
                return name.contains("black") == says ? "black" : "red";
            }
            if (text.contains("RED") || text.equals("R")) {
                return "red";
            }
            if (text.contains("BLACK") || text.equals("B")) {
                return "black";
            }
        }
        return null;
    }

    // A small caption for a node: an AVL node's height ("h=2"), or null.
    public static String captionOf(ObjectNode node) {
        for (Map.Entry<String, Value> field : node.getFields().entrySet()) {
            if (HEIGHT_NAMES.contains(field.getKey().toLowerCase(java.util.Locale.ROOT))) {
                return "h=" + field.getValue().display();
            }
        }
        return null;
    }

    public static String labelOf(ObjectNode node) {
        int links = 0;
        for (String field : node.getFields().keySet()) {
            if (node.getTypeName().equals(node.getFieldType(field)) && !isBackLink(field)) {
                links++;
            }
        }
        boolean tree = links >= 2; // a binary tree's node (left and right)
        StringBuilder label = new StringBuilder();
        for (Map.Entry<String, Value> field : node.getFields().entrySet()) {
            if (node.getTypeName().equals(node.getFieldType(field.getKey()))) {
                continue; // a link to another node, drawn as an arrow instead
            }
            if (tree && (isColour(field.getKey()) || HEIGHT_NAMES.contains(field.getKey().toLowerCase(java.util.Locale.ROOT)))) {
                continue; // a tree shows it as the node's colour, or as its caption
            }
            if (label.length() > 0) {
                label.append(" ").append(field.getKey().charAt(0)).append("=");
            }
            label.append(field.getValue().display());
        }
        return label.toString();
    }
}
