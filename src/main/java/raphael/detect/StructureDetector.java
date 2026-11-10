package raphael.detect;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import raphael.model.ArrayNode;
import raphael.model.HeapObject;
import raphael.model.NullValue;
import raphael.model.ObjectNode;
import raphael.model.ReferenceValue;
import raphael.model.Snapshot;
import raphael.model.Value;

// Looks at the objects in a Snapshot and decides how to draw them. The rules only look at
// the types and names of the fields:
//   an array → array; an array of arrays, or a list of lists → grid
//   1 field of the class's own type → linked list (prev + next → doubly linked list)
//   left + right of its own type (or any 2 such fields) → binary tree
//   an array of its own type (Node[] children) → multi-way tree, like a B-tree
//   anything else → a plain box
// OOP: encapsulation (helpers are private). It returns Structures of different subclasses,
// and the code that uses them treats them all as Structure (polymorphism).
public class StructureDetector {

    private enum Shape { LIST, DOUBLY_LIST, TREE, MULTIWAY, BOX }

    // Names a field holding "how many keys are in use" might have.
    private static final Set<String> COUNT_NAMES = Set.of("n", "size", "count", "numkeys", "keycount", "nkeys");

    // A class's shape and the names of the fields that matter for it.
    // (For a multi-way tree: the children array, the keys array and the count.)
    private record ClassShape(Shape shape, String firstLink, String secondLink, String third) {
        ClassShape(Shape shape, String firstLink, String secondLink) {
            this(shape, firstLink, secondLink, null);
        }
    }

    public List<Structure> detect(Snapshot snapshot) {
        Map<Long, HeapObject> heap = snapshot.getHeap();
        List<Structure> found = new ArrayList<>();
        Set<Long> used = new HashSet<>();

        // Objects first, grouped by class: a multi-way tree claims its nodes' arrays.
        Map<String, List<ObjectNode>> byClass = new LinkedHashMap<>();
        for (HeapObject object : heap.values()) {
            if (object instanceof ObjectNode) {
                byClass.computeIfAbsent(object.getTypeName(), k -> new ArrayList<>()).add((ObjectNode) object);
            }
        }
        // Hash tables with chaining first: an array of list nodes takes the chains hanging from it.
        for (HeapObject object : heap.values()) {
            if (object instanceof ArrayNode && object.getTypeName().endsWith("[]")) {
                String nodeType = object.getTypeName().substring(0, object.getTypeName().length() - 2);
                List<ObjectNode> nodes = byClass.get(nodeType);
                if (nodes == null || shapeOf(nodes.get(0)).shape() != Shape.LIST) {
                    continue;
                }
                String next = shapeOf(nodes.get(0)).firstLink();
                Map<Long, ObjectNode> all = byId(nodes);
                Set<Long> chained = HashTableStructure.reachable((ArrayNode) object, all, next);
                Map<Long, ObjectNode> mine = new LinkedHashMap<>();
                for (long id : chained) {
                    mine.put(id, all.get(id));
                }
                found.add(new HashTableStructure((ArrayNode) object, mine, next));
                used.add(object.getId());
                nodes.removeIf(node -> chained.contains(node.getId()));
            }
        }
        byClass.values().removeIf(List::isEmpty);
        for (List<ObjectNode> objects : byClass.values()) {
            ClassShape shape = shapeOf(objects.get(0));
            if (shape.shape() == Shape.LIST) {
                found.add(new LinkedListStructure(byId(objects), shape.firstLink(), null));
            } else if (shape.shape() == Shape.DOUBLY_LIST) {
                found.add(new LinkedListStructure(byId(objects), shape.firstLink(), shape.secondLink()));
            } else if (shape.shape() == Shape.TREE) {
                found.addAll(buildTrees(byId(objects), shape.firstLink(), shape.secondLink()));
            } else if (shape.shape() == Shape.MULTIWAY) {
                for (MultiwayTreeStructure tree : buildMultiwayTrees(byId(objects), heap, shape)) {
                    found.add(tree);
                    used.addAll(tree.getObjectIds());
                }
            } else {
                for (ObjectNode object : objects) {
                    found.add(new GenericStructure(object));
                }
            }
        }

        // Then grids (so their rows aren't drawn again as separate arrays), then the other arrays.
        for (HeapObject object : heap.values()) {
            if (object instanceof ArrayNode && !used.contains(object.getId())
                    && holdsOnlyArrays((ArrayNode) object, heap)) {
                GridStructure grid = buildGrid((ArrayNode) object, heap);
                found.add(grid);
                used.addAll(grid.getObjectIds());
            }
        }
        for (HeapObject object : heap.values()) {
            if (object instanceof ArrayNode && !used.contains(object.getId())) {
                found.add(new ArrayStructure((ArrayNode) object));
            }
        }

        // Arrays on top, because a tree below can grow without moving them. Within that, oldest
        // first, so structures keep their place from step to step.
        found.sort(Comparator.comparingInt((Structure s) -> s instanceof ArrayStructure || s instanceof GridStructure ? 0 : 1)
                .thenComparingLong(s -> Collections.min(s.getObjectIds())));
        return found;
    }

    // True for a 2D array, or an array or list whose items are all lists or arrays (rows may be
    // uneven). An array whose type says it holds lists, like ArrayList<Integer>[], is a table from
    // the start, even while its rows are still null, just as an int[][] is.
    private static boolean holdsOnlyArrays(ArrayNode array, Map<Long, HeapObject> heap) {
        if (array.getElementType().endsWith("[]")) {
            return true;
        }
        boolean typedForRows = array.getTypeName().endsWith("[]") && array.getElementType().startsWith("java.util.");
        for (Value item : array.getElements()) {
            if (!(typedForRows && item instanceof NullValue) && !(arrayOf(item, heap) instanceof ArrayNode)) {
                return false;
            }
        }
        return !array.getElements().isEmpty();
    }

    private ClassShape shapeOf(ObjectNode node) {
        ClassShape multiway = multiwayShapeOf(node);
        if (multiway.shape() == Shape.MULTIWAY) {
            return multiway; // a children array decides, even beside a next link (a B+ tree's leaves)
        }
        List<String> links = new ArrayList<>();
        for (String field : node.getFields().keySet()) {
            if (node.getTypeName().equals(node.getFieldType(field)) && !LinkedStructure.isBackLink(field)) {
                links.add(field);
            }
        }
        if (links.isEmpty()) {
            return multiwayShapeOf(node);
        }
        if (links.size() == 1) {
            return new ClassShape(Shape.LIST, links.get(0), null);
        }
        if (links.contains("left") && links.contains("right")) {
            return new ClassShape(Shape.TREE, "left", "right");
        }
        if (links.size() == 2) {
            String a = links.get(0);
            String b = links.get(1);
            if (a.toLowerCase(Locale.ROOT).contains("prev") && b.toLowerCase(Locale.ROOT).contains("next")) {
                return new ClassShape(Shape.DOUBLY_LIST, b, a);
            }
            if (a.toLowerCase(Locale.ROOT).contains("next") && b.toLowerCase(Locale.ROOT).contains("prev")) {
                return new ClassShape(Shape.DOUBLY_LIST, a, b);
            }
            return new ClassShape(Shape.TREE, a, b);
        }
        return new ClassShape(Shape.BOX, null, null);
    }

    // A class with an array of its own type (Node[] children) is a multi-way tree. Its keys are
    // the first other array field, and its count is an int field called n, size, count…
    private ClassShape multiwayShapeOf(ObjectNode node) {
        String children = null;
        String keys = null;
        String count = null;
        for (String field : node.getFields().keySet()) {
            String type = node.getFieldType(field);
            if (type.equals(node.getTypeName() + "[]")) {
                children = children == null ? field : children;
            } else if (type.endsWith("[]") && !type.endsWith("[][]")) {
                keys = keys == null ? field : keys;
            } else if (type.equals("int") && COUNT_NAMES.contains(field.toLowerCase(Locale.ROOT))) {
                count = count == null ? field : count;
            }
        }
        return children == null ? new ClassShape(Shape.BOX, null, null) : new ClassShape(Shape.MULTIWAY, children, keys, count);
    }

    // One multi-way tree per root (a node that is nobody's child). Each tree also takes its
    // nodes' keys and children arrays. Like buildTrees, a node is never visited twice.
    private List<MultiwayTreeStructure> buildMultiwayTrees(Map<Long, ObjectNode> all, Map<Long, HeapObject> heap,
                                                          ClassShape shape) {
        Map<Long, List<Long>> childrenOf = new LinkedHashMap<>();
        Set<Long> hasParent = new HashSet<>();
        for (ObjectNode node : all.values()) {
            List<Long> children = new ArrayList<>();
            HeapObject array = arrayOf(node.getField(shape.firstLink()), heap);
            if (array instanceof ArrayNode) {
                for (Value slot : ((ArrayNode) array).getElements()) {
                    long child = target(slot, all);
                    if (child != -1 && child != node.getId()) {
                        children.add(child);
                        hasParent.add(child);
                    }
                }
            }
            childrenOf.put(node.getId(), children);
        }
        List<Long> starts = new ArrayList<>();
        for (long id : all.keySet()) {
            if (!hasParent.contains(id)) {
                starts.add(id);
            }
        }
        starts.addAll(all.keySet());

        List<MultiwayTreeStructure> trees = new ArrayList<>();
        Set<Long> placed = new HashSet<>();
        for (long root : starts) {
            if (placed.contains(root)) {
                continue;
            }
            Map<Long, ObjectNode> nodes = new LinkedHashMap<>();
            Map<Long, ArrayNode> arrays = new LinkedHashMap<>();
            Deque<Long> stack = new ArrayDeque<>();
            stack.push(root);
            while (!stack.isEmpty()) {
                long id = stack.pop();
                if (!placed.add(id)) {
                    continue;
                }
                ObjectNode node = all.get(id);
                nodes.put(id, node);
                for (String field : new String[] {shape.firstLink(), shape.secondLink()}) {
                    HeapObject array = field == null ? null : arrayOf(node.getField(field), heap);
                    if (array instanceof ArrayNode) {
                        arrays.put(array.getId(), (ArrayNode) array);
                    }
                }
                List<Long> children = childrenOf.get(id);
                for (int i = children.size() - 1; i >= 0; i--) {
                    stack.push(children.get(i));
                }
            }
            MultiwayTreeStructure tree = new MultiwayTreeStructure(nodes, arrays, root, shape.firstLink(), shape.secondLink(), shape.third());
            ObjectNode any = nodes.values().iterator().next();
            for (String field : any.getFields().keySet()) { // a B+ tree's leaves link to the next leaf
                if (any.getTypeName().equals(any.getFieldType(field)) && !LinkedStructure.isBackLink(field)) {
                    tree.setLeafLink(field);
                }
            }
            trees.add(tree);
        }
        return trees;
    }

    private static HeapObject arrayOf(Value value, Map<Long, HeapObject> heap) {
        return value instanceof ReferenceValue ? heap.get(((ReferenceValue) value).id()) : null;
    }

    private GridStructure buildGrid(ArrayNode grid, Map<Long, HeapObject> heap) {
        List<ArrayNode> rows = new ArrayList<>();
        for (Value cell : grid.getElements()) {
            HeapObject row = cell instanceof ReferenceValue ? heap.get(((ReferenceValue) cell).id()) : null;
            rows.add(row instanceof ArrayNode ? (ArrayNode) row : null);
        }
        return new GridStructure(grid, rows);
    }

    // One tree per root. A root is a node no other node points at. Nodes left over (only
    // possible in a cycle) start a tree of their own, so nothing is lost.
    private List<BinaryTreeStructure> buildTrees(Map<Long, ObjectNode> all, String left, String right) {
        Set<Long> hasParent = new HashSet<>();
        for (ObjectNode node : all.values()) {
            for (String link : List.of(left, right)) {
                long child = target(node.getField(link), all);
                if (child != -1 && child != node.getId()) {
                    hasParent.add(child);
                }
            }
        }
        List<Long> starts = new ArrayList<>();
        for (long id : all.keySet()) {
            if (!hasParent.contains(id)) {
                starts.add(id);
            }
        }
        starts.addAll(all.keySet());

        List<BinaryTreeStructure> trees = new ArrayList<>();
        Set<Long> placed = new HashSet<>();
        for (long root : starts) {
            if (placed.contains(root)) {
                continue;
            }
            // Depth-first walk from the root, never visiting a node twice.
            Map<Long, ObjectNode> nodes = new LinkedHashMap<>();
            Deque<Long> stack = new ArrayDeque<>();
            stack.push(root);
            while (!stack.isEmpty()) {
                long id = stack.pop();
                if (!placed.add(id)) {
                    continue;
                }
                nodes.put(id, all.get(id));
                for (String link : List.of(right, left)) {
                    long child = target(all.get(id).getField(link), all);
                    if (child != -1 && !placed.contains(child)) {
                        stack.push(child);
                    }
                }
            }
            trees.add(new BinaryTreeStructure(nodes, root, left, right));
        }
        return trees;
    }

    // The id a field points at, if that object is one of these nodes; otherwise -1.
    private static long target(Value value, Map<Long, ObjectNode> nodes) {
        if (value instanceof ReferenceValue && nodes.containsKey(((ReferenceValue) value).id())) {
            return ((ReferenceValue) value).id();
        }
        return -1;
    }

    private static Map<Long, ObjectNode> byId(List<ObjectNode> objects) {
        List<ObjectNode> sorted = new ArrayList<>(objects);
        sorted.sort(Comparator.comparingLong(ObjectNode::getId));
        Map<Long, ObjectNode> nodes = new LinkedHashMap<>();
        for (ObjectNode node : sorted) {
            nodes.put(node.getId(), node);
        }
        return nodes;
    }
}
