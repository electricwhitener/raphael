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
    public static String labelOf(ObjectNode node) {
        StringBuilder label = new StringBuilder();
        for (Map.Entry<String, Value> field : node.getFields().entrySet()) {
            if (node.getTypeName().equals(node.getFieldType(field.getKey()))) {
                continue; // a link to another node, drawn as an arrow instead
            }
            if (label.length() > 0) {
                label.append(" ").append(field.getKey().charAt(0)).append("=");
            }
            label.append(field.getValue().display());
        }
        return label.toString();
    }
}
