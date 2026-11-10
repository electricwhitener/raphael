package raphael.detect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import raphael.model.ArrayNode;
import raphael.model.ObjectNode;
import raphael.model.PrimitiveValue;
import raphael.model.ReferenceValue;
import raphael.model.Value;

// A tree whose nodes keep their children in an array, like a B-tree:
//     class Node { int[] keys; Node[] children; int n; }
// Each node has an array of keys, an array of children, and (optionally) a count saying how many
// keys are in use. The node's arrays belong to the tree, so they aren't drawn separately.
// OOP: inheritance (LinkedStructure → MultiwayTreeStructure: multilevel) and overriding.
public class MultiwayTreeStructure extends LinkedStructure {

    private final long root;
    private final String childrenField;
    private final String keysField; // null if the nodes have no keys array
    private final String countField; // null if the nodes don't say how many keys are in use
    private final Map<Long, ArrayNode> arrays; // the nodes' keys and children arrays, by id

    public MultiwayTreeStructure(Map<Long, ObjectNode> nodes, Map<Long, ArrayNode> arrays, long root,
                                 String childrenField, String keysField, String countField) {
        super(nodes);
        this.arrays = Collections.unmodifiableMap(new LinkedHashMap<>(arrays));
        this.root = root;
        this.childrenField = childrenField;
        this.keysField = keysField;
        this.countField = countField;
    }

    private String leafLink; // a B+ tree's link from each leaf to the next, or null

    public void setLeafLink(String field) {
        this.leafLink = field;
    }

    // The node this one links to as the next leaf (B+ tree), or -1.
    public long getLeafNext(long id) {
        return leafLink == null ? -1 : follow(id, leafLink);
    }

    public long getRoot() {
        return root;
    }

    // The node's children array, or null.
    public ArrayNode getChildrenArray(long id) {
        return arrayIn(id, childrenField);
    }

    // The node's keys array, or null.
    public ArrayNode getKeysArray(long id) {
        return keysField == null ? null : arrayIn(id, keysField);
    }

    // False for a node type with no keys array at all, like a trie.
    public boolean hasKeys() {
        return keysField != null;
    }

    // The node's plain fields, e.g. "end=true".
    public String getPlainFields(long id) {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, Value> field : getNode(id).getFields().entrySet()) {
            if (field.getValue() instanceof PrimitiveValue) {
                text.append(text.length() > 0 ? " " : "").append(field.getKey()).append("=").append(field.getValue().display());
            }
        }
        return text.toString();
    }

    // The children in order: a node id, or -1 for an empty slot.
    public List<Long> getChildren(long id) {
        List<Long> children = new ArrayList<>();
        ArrayNode array = getChildrenArray(id);
        if (array != null) {
            for (Value slot : array.getElements()) {
                long child = slot instanceof ReferenceValue ? ((ReferenceValue) slot).id() : -1;
                children.add(getNodes().containsKey(child) ? child : -1);
            }
        }
        return children;
    }

    // How many keys are in use (the count field, like n), or all of them if there's no count.
    public int getUsedKeys(long id) {
        ArrayNode keys = getKeysArray(id);
        int all = keys == null ? 0 : keys.getElements().size();
        if (countField != null && getNode(id).getField(countField) instanceof PrimitiveValue) {
            try {
                return Math.min(all, Integer.parseInt(getNode(id).getField(countField).display()));
            } catch (NumberFormatException e) {
                return all;
            }
        }
        return all;
    }

    // The nodes, plus their keys and children arrays.
    @Override
    public Set<Long> getObjectIds() {
        Set<Long> ids = new LinkedHashSet<>(getNodes().keySet());
        ids.addAll(arrays.keySet());
        return ids;
    }

    @Override
    public String getKind() {
        return "multi-way tree";
    }

    private ArrayNode arrayIn(long id, String field) {
        Value value = getNode(id).getField(field);
        return value instanceof ReferenceValue ? arrays.get(((ReferenceValue) value).id()) : null;
    }
}
