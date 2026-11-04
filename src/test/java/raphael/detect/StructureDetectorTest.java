package raphael.detect;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import raphael.model.ArrayNode;
import raphael.model.HeapObject;
import raphael.model.NullValue;
import raphael.model.ObjectNode;
import raphael.model.PrimitiveValue;
import raphael.model.ReferenceValue;
import raphael.model.Snapshot;
import raphael.model.Value;

class StructureDetectorTest {

    private static final String NODE = "Main$Node";
    private final StructureDetector detector = new StructureDetector();

    // --- helpers that build objects by hand (no JVM needed) ---

    private static Value ref(long id) {
        return id == 0 ? NullValue.INSTANCE : new ReferenceValue(id);
    }

    // An object of type NODE with an int "val" and the given links (name → id, 0 = null).
    private static ObjectNode node(long id, int val, Object... links) {
        Map<String, Value> fields = new LinkedHashMap<>();
        Map<String, String> types = new LinkedHashMap<>();
        fields.put("val", new PrimitiveValue(String.valueOf(val)));
        types.put("val", "int");
        for (int i = 0; i < links.length; i += 2) {
            fields.put((String) links[i], ref(((Number) links[i + 1]).longValue()));
            types.put((String) links[i], NODE);
        }
        return new ObjectNode(id, NODE, fields, types);
    }

    private static ArrayNode intArray(long id, int... values) {
        List<Value> cells = new java.util.ArrayList<>();
        for (int v : values) {
            cells.add(new PrimitiveValue(String.valueOf(v)));
        }
        return new ArrayNode(id, "int[]", "int", cells, values.length);
    }

    private static Snapshot snapshot(HeapObject... objects) {
        Map<Long, HeapObject> heap = new LinkedHashMap<>();
        for (HeapObject o : objects) {
            heap.put(o.getId(), o);
        }
        return new Snapshot(List.of(), heap, Map.of(), 0, null);
    }

    // --- tests ---

    @Test
    void arrayIsAnArray() {
        List<Structure> found = detector.detect(snapshot(intArray(1, 5, 2, 9)));

        assertEquals(1, found.size());
        assertInstanceOf(ArrayStructure.class, found.get(0));
    }

    @Test
    void arrayOfArraysIsOneGrid() {
        ArrayNode row0 = intArray(2, 1, 1);
        ArrayNode row1 = intArray(3, 1, 2);
        ArrayNode grid = new ArrayNode(1, "int[][]", "int[]", List.of(ref(2), ref(3)), 2);

        List<Structure> found = detector.detect(snapshot(grid, row0, row1));

        assertEquals(1, found.size()); // the rows are part of the grid, not separate arrays
        GridStructure g = assertInstanceOf(GridStructure.class, found.get(0));
        assertEquals(2, g.getRows().size());
    }

    @Test
    void oneSelfLinkIsALinkedList() {
        List<Structure> found = detector.detect(snapshot(
                node(1, 10, "next", 2), node(2, 20, "next", 3), node(3, 30, "next", 0)));

        LinkedListStructure list = assertInstanceOf(LinkedListStructure.class, found.get(0));
        assertEquals(1, found.size());
        assertEquals(List.of(List.of(1L, 2L, 3L)), list.getChains());
        assertEquals("20", list.getLabel(2));
        assertFalse(list.isDoublyLinked());
    }

    @Test
    void halfReversedListHasTwoChains() {
        // prev: 2 → 1 → null and curr: 3 → 4 → null, in the middle of a reversal
        List<Structure> found = detector.detect(snapshot(
                node(1, 1, "next", 0), node(2, 2, "next", 1), node(3, 3, "next", 4), node(4, 4, "next", 0)));

        LinkedListStructure list = assertInstanceOf(LinkedListStructure.class, found.get(0));
        assertEquals(List.of(List.of(2L, 1L), List.of(3L, 4L)), list.getChains());
    }

    @Test
    void prevAndNextIsADoublyLinkedList() {
        List<Structure> found = detector.detect(snapshot(
                node(1, 10, "prev", 0, "next", 2), node(2, 20, "prev", 1, "next", 0)));

        LinkedListStructure list = assertInstanceOf(LinkedListStructure.class, found.get(0));
        assertTrue(list.isDoublyLinked());
        assertEquals(2, list.getNext(1));
        assertEquals(1, list.getPrev(2));
    }

    @Test
    void leftAndRightIsABinaryTree() {
        List<Structure> found = detector.detect(snapshot(
                node(1, 8, "left", 2, "right", 3), node(2, 3, "left", 0, "right", 0), node(3, 10, "left", 0, "right", 0)));

        assertEquals(1, found.size());
        BinaryTreeStructure tree = assertInstanceOf(BinaryTreeStructure.class, found.get(0));
        assertEquals(1, tree.getRoot());
        assertEquals(2, tree.getLeft(1));
        assertEquals(3, tree.getRight(1));
        assertEquals(-1, tree.getLeft(2));
    }

    @Test
    void otherObjectsAreBoxes() {
        ObjectNode point = new ObjectNode(1, "Main$Point",
                Map.of("x", new PrimitiveValue("3")), Map.of("x", "int"));

        List<Structure> found = detector.detect(snapshot(point));

        assertInstanceOf(GenericStructure.class, found.get(0));
    }

    // A B-tree node: an int[] of keys, a Node[] of children, and n (how many keys are in use).
    private static ObjectNode bnode(long id, long keys, long children, int n) {
        return bnode(id, keys, children, n, "n");
    }

    // The same, with the count field called countName instead of n.
    private static ObjectNode bnode(long id, long keys, long children, int n, String countName) {
        Map<String, Value> fields = new LinkedHashMap<>();
        Map<String, String> types = new LinkedHashMap<>();
        fields.put("keys", ref(keys));
        types.put("keys", "int[]");
        fields.put("children", ref(children));
        types.put("children", NODE + "[]");
        fields.put(countName, new PrimitiveValue(String.valueOf(n)));
        types.put(countName, "int");
        return new ObjectNode(id, NODE, fields, types);
    }

    private static ArrayNode refArray(long id, long... targets) {
        List<Value> cells = new java.util.ArrayList<>();
        for (long t : targets) {
            cells.add(ref(t));
        }
        return new ArrayNode(id, NODE + "[]", NODE, cells, targets.length);
    }

    @Test
    void anArrayOfItsOwnTypeMakesAMultiwayTree() {
        List<Structure> found = detector.detect(snapshot(
                bnode(1, 10, 11, 1), intArray(10, 10, 0, 0), refArray(11, 2, 3, 0, 0),
                bnode(2, 20, 21, 2), intArray(20, 3, 5, 0), refArray(21, 0, 0, 0, 0),
                bnode(3, 30, 31, 1), intArray(30, 20, 0, 0), refArray(31, 0, 0, 0, 0)));

        assertEquals(1, found.size()); // the key and child arrays belong to the tree
        MultiwayTreeStructure tree = assertInstanceOf(MultiwayTreeStructure.class, found.get(0));
        assertEquals(1, tree.getRoot());
        assertEquals(List.of(2L, 3L, -1L, -1L), tree.getChildren(1));
        assertEquals(10, tree.getKeysArray(1).getId());
        assertEquals(2, tree.getUsedKeys(2));
    }

    // In Turkish, "SIZE".toLowerCase() is "sıze" (a dotless ı), so a check that used the
    // computer's language would miss the count field there and show every key as used.
    @Test
    void countFieldIsFoundWhateverTheComputersLanguage() {
        Locale before = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr"));
        try {
            List<Structure> found = detector.detect(snapshot(
                    bnode(1, 10, 11, 2, "SIZE"), intArray(10, 3, 5, 7), refArray(11, 0, 0, 0, 0)));
            MultiwayTreeStructure tree = assertInstanceOf(MultiwayTreeStructure.class, found.get(0));
            assertEquals(2, tree.getUsedKeys(1));
        } finally {
            Locale.setDefault(before);
        }
    }

    @Test
    void cycleDoesNotHang() {
        List<Structure> found = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> detector.detect(snapshot(
                node(1, 1, "next", 2), node(2, 2, "next", 3), node(3, 3, "next", 1))));

        LinkedListStructure list = assertInstanceOf(LinkedListStructure.class, found.get(0));
        assertEquals(List.of(List.of(1L, 2L, 3L)), list.getChains());
        assertEquals(1, list.getNext(3)); // the arrow back to the start is kept, so the bug is visible
    }
}
