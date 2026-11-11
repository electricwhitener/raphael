package raphael.render;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import raphael.model.ArrayNode;
import raphael.model.EndReason;
import raphael.model.FrameSnapshot;
import raphael.model.HeapObject;
import raphael.model.NullValue;
import raphael.model.ObjectNode;
import raphael.model.PrimitiveValue;
import raphael.model.ReferenceValue;
import raphael.model.Snapshot;
import raphael.model.Trace;
import raphael.model.Value;

class SceneBuilderTest {

    private static final String NODE = "Main$Node";

    private static Value ref(long id) {
        return id == 0 ? NullValue.INSTANCE : new ReferenceValue(id);
    }

    private static Value number(int n) {
        return new PrimitiveValue(String.valueOf(n));
    }

    private static ObjectNode node(long id, int val, Object... links) {
        Map<String, Value> fields = new LinkedHashMap<>();
        Map<String, String> types = new LinkedHashMap<>();
        fields.put("val", number(val));
        types.put("val", "int");
        for (int i = 0; i < links.length; i += 2) {
            fields.put((String) links[i], ref(((Number) links[i + 1]).longValue()));
            types.put((String) links[i], NODE);
        }
        return new ObjectNode(id, NODE, fields, types);
    }

    private static ArrayNode intArray(long id, int... values) {
        List<Value> cells = new ArrayList<>();
        for (int v : values) {
            cells.add(number(v));
        }
        return new ArrayNode(id, "int[]", "int", cells, values.length);
    }

    private static ObjectNode bnode(long id, long keys, long children, int n) {
        Map<String, Value> fields = new LinkedHashMap<>();
        Map<String, String> types = new LinkedHashMap<>();
        fields.put("keys", ref(keys));
        types.put("keys", "int[]");
        fields.put("children", ref(children));
        types.put("children", NODE + "[]");
        fields.put("n", number(n));
        types.put("n", "int");
        return new ObjectNode(id, NODE, fields, types);
    }

    private static ArrayNode refArray(long id, long... targets) {
        List<Value> cells = new ArrayList<>();
        for (long t : targets) {
            cells.add(ref(t));
        }
        return new ArrayNode(id, NODE + "[]", NODE, cells, targets.length);
    }

    // A snapshot paused at a line, whose only method has these local variables.
    private static Snapshot snapshot(int line, Map<String, Value> locals, HeapObject... objects) {
        Map<Long, HeapObject> heap = new LinkedHashMap<>();
        for (HeapObject o : objects) {
            heap.put(o.getId(), o);
        }
        return new Snapshot(List.of(new FrameSnapshot("Main", "main", line, locals)), heap, Map.of(), 0, null);
    }

    private static List<Scene> build(String source, Snapshot... snapshots) {
        return new SceneBuilder(source).build(new Trace(List.of(snapshots), "", EndReason.FINISHED, ""));
    }

    @Test
    void reversingAListKeepsTheNodesInPlace() {
        Snapshot before = snapshot(1, Map.of("head", ref(1)),
                node(1, 1, "next", 2), node(2, 2, "next", 3), node(3, 3, "next", 0));
        Snapshot after = snapshot(1, Map.of("head", ref(3)),
                node(1, 1, "next", 0), node(2, 2, "next", 1), node(3, 3, "next", 2));

        List<Scene> scenes = build("", before, after);

        for (String key : List.of("#1", "#2", "#3")) {
            assertEquals(scenes.get(0).getNode(key).x(), scenes.get(1).getNode(key).x(), key + " moved");
        }
        assertTrue(scenes.get(0).getNode("#1").x() < scenes.get(0).getNode("#2").x());
        // The same pointer (node 2's next) now points back at node 1, and node 1's next is null.
        assertEquals("#3", scenes.get(0).getEdge("#2.next→").to());
        assertEquals("#1", scenes.get(1).getEdge("#2.next→").to());
        assertEquals(SceneNode.Kind.NULL_SLOT, scenes.get(1).getNode("#1.next").kind());
    }

    @Test
    void treeIsLaidOutInOrderAndByDepth() {
        Scene scene = build("", snapshot(1, Map.of(),
                node(1, 8, "left", 2, "right", 3), node(2, 3, "left", 0, "right", 0),
                node(3, 10, "left", 0, "right", 0))).get(0);

        SceneNode root = scene.getNode("#1");
        SceneNode left = scene.getNode("#2");
        SceneNode right = scene.getNode("#3");
        assertTrue(left.x() < root.x() && root.x() < right.x());
        assertTrue(left.y() > root.y());
        assertEquals(left.y(), right.y());
        assertEquals("#2", scene.getEdge("#1.left→").to());
    }

    @Test
    void labelsSitAboveTheObjectTheyPointAt() {
        Scene scene = build("", snapshot(1, Map.of("curr", ref(2), "x", number(5)),
                node(1, 1, "next", 2), node(2, 2, "next", 0))).get(0);

        SceneNode label = scene.getNode("var:curr");
        SceneNode target = scene.getNode("#2");
        assertTrue(label.y() + label.h() <= target.y());
        assertEquals(target.centerX(), label.centerX(), 0.01);
        assertEquals("#2", scene.getEdge("var:curr→").to());
        assertNull(scene.getNode("var:x")); // plain numbers get no label
    }

    @Test
    void indexMarkersAndTheCellsTheNextLineTouches() {
        String source = """
                class Main {
                    void sort(int[] arr, int j) {
                        if (arr[j] > arr[j + 1]) { }
                    }
                }
                """;
        Scene scene = build(source, snapshot(3, Map.of("arr", ref(1), "j", number(1)),
                intArray(1, 5, 2, 9))).get(0);

        SceneNode marker = scene.getNode("idx:1:ROW:j");
        assertNotNull(marker, "j is drawn under the array");
        assertEquals(scene.getNode("#1[1]").centerX(), marker.centerX(), 0.01);
        // Line 3 reads arr[j] and arr[j + 1]: cells 1 and 2 get dashed "read" outlines.
        assertEquals(SceneNode.Kind.FOCUS_READ, scene.getNode("focus:1:0").kind());
        assertEquals(scene.getNode("#1[1]").x(), scene.getNode("focus:1:0").x(), 0.01);
        assertEquals(scene.getNode("#1[2]").x(), scene.getNode("focus:1:1").x(), 0.01);
    }

    // A C pointer into an array (its value reads "&arr[2]") is a marker on that cell, also when the
    // array belongs to a caller: swap(&arr[0], &arr[2]) marks main's arr.
    @Test
    void cPointersIntoAnArrayAreMarkers() {
        String source = """
                void swap(int *a, int *b) {
                    int t = *a;
                }
                int main(void) {
                    int arr[3] = {5, 2, 9};
                    swap(&arr[0], &arr[2]);
                    int *end = arr + 3;
                }
                """;
        Map<Long, HeapObject> heap = Map.of(1L, intArray(1, 5, 2, 9));
        Snapshot inSwap = new Snapshot(List.of(
                new FrameSnapshot("main", "swap", 2, Map.of("a", new PrimitiveValue("&arr[0]"), "b", new PrimitiveValue("&arr[2]"))),
                new FrameSnapshot("main", "main", 6, Map.of("arr", ref(1)))), heap, Map.of(), 0, null);
        Snapshot atEnd = new Snapshot(List.of(
                new FrameSnapshot("main", "main", 8, Map.of("arr", ref(1), "end", new PrimitiveValue("&arr[3]")))),
                heap, Map.of(), 0, null);
        List<Scene> scenes = build(source, inSwap, atEnd);

        assertEquals(scenes.get(0).getNode("#1[0]").centerX(), scenes.get(0).getNode("idx:1:ROW:a").centerX(), 0.01);
        assertEquals(scenes.get(0).getNode("#1[2]").centerX(), scenes.get(0).getNode("idx:1:ROW:b").centerX(), 0.01);
        assertNotNull(scenes.get(1).getNode("idx:1:ROW:end"), "one past the end is marked too, outside the cells");
    }

    @Test
    void aCopyBetweenCellsIsRememberedAsAMove() {
        String source = """
                class Main {
                    void shift(int[] arr) {
                        arr[0] = arr[1];
                    }
                }
                """;
        Snapshot before = snapshot(3, Map.of("arr", ref(1)), intArray(1, 5, 2));
        Snapshot after = snapshot(4, Map.of("arr", ref(1)), intArray(1, 2, 2));

        List<Scene> scenes = build(source, before, after);

        assertEquals(List.of(new Scene.Move("#1[1]", "#1[0]")), scenes.get(1).getMoves());
    }

    @Test
    void aBTreeIsDrawnWithParentsCentredOverTheirChildren() {
        String source = "class Main { void f(Node x, int i) { x.keys[i + 1] = x.keys[i]; } }";
        HeapObject[] objects = {
                bnode(1, 10, 11, 1), intArray(10, 10, 0, 0), refArray(11, 2, 3, 0, 0),
                bnode(2, 20, 21, 2), intArray(20, 3, 5, 0), refArray(21, 0, 0, 0, 0),
                bnode(3, 30, 31, 1), intArray(30, 20, 0, 0), refArray(31, 0, 0, 0, 0)};
        Scene scene = build(source, snapshot(1, Map.of("x", ref(2), "i", number(1)), objects)).get(0);

        SceneNode root = scene.getNode("#1");
        SceneNode left = scene.getNode("#2");
        SceneNode right = scene.getNode("#3");
        assertTrue(left.y() > root.y() && left.y() == right.y());
        assertEquals((left.x() + right.x() + right.w()) / 2, root.centerX(), 0.01);
        assertEquals("3", scene.getNode("#20[0]").text());           // key cells keep their array's keys
        assertEquals(SceneNode.Look.DIM, scene.getNode("#20[2]").look()); // past n: faded
        assertEquals("#2", scene.getEdge("#11[0]→").to());           // child pointer → child node
        assertNotNull(scene.getNode("idx:20:ROW:i"));                // i is found through "x.keys"
    }

    @Test
    void anIndexPastTheEndShowsAGhostCell() {
        String source = """
                class Main {
                    void f(int[] arr, int i) {
                        sum += arr[i];
                    }
                }
                """;
        Snapshot step = snapshot(3, Map.of("arr", ref(1), "i", number(3)), intArray(1, 4, 7, 1));
        Scene scene = new SceneBuilder(source).build(new Trace(List.of(step), "", EndReason.EXCEPTION, "")).get(0);

        SceneNode ghost = scene.getNode("focus:1:0");
        assertEquals(SceneNode.Kind.FOCUS_OUT, ghost.kind());
        assertTrue(ghost.x() > scene.getNode("#1[2]").x(), "just past the last cell");
    }

    @Test
    void aRunThatDoesNotCrashShowsNoRedGhost() {
        String source = "class Main {\n void f(int[] a, int i) {\n  while (i < a.length && a[i] != 9) { }\n }\n}\n";
        Scene scene = build(source, snapshot(3, Map.of("a", ref(1), "i", number(3)), intArray(1, 4, 7, 1))).get(0);

        for (SceneNode node : scene.getNodes()) {
            assertNotEquals(SceneNode.Kind.FOCUS_OUT, node.kind());
        }
        assertEquals(SceneNode.Look.DIM, scene.getNode("#1:ghost3").look()); // a plain faded cell instead
    }

    @Test
    void anIndexJustOutsideAnArrayGetsAGhostCellAndAFullStrengthMarker() {
        String source = "class Main {\n void f(int[] a, int i) {\n  a[i] = 1;\n }\n}\n";
        Scene scene = build(source, snapshot(1, Map.of("a", ref(1), "i", number(-1)), intArray(1, 4, 7))).get(0);

        SceneNode ghost = scene.getNode("#1:ghost-1");
        SceneNode marker = scene.getNode("idx:1:ROW:i");
        assertEquals("-1", ghost.caption());
        assertEquals(scene.getNode("#1[0]").x() - ghost.w(), ghost.x(), 0.01);
        assertTrue(ghost.x() >= 0, "the ghost cell fits inside the picture");
        assertEquals(ghost.centerX(), marker.centerX(), 0.01);
        assertEquals("i=-1", marker.text());
        assertEquals(SceneNode.Look.STILL, marker.look());
    }

    @Test
    void arraysStayOnTopAndUnlinkedTreeNodesWaitBesideTheirTree() {
        Scene scene = build("", snapshot(1, Map.of(),
                node(1, 8, "left", 2, "right", 0), node(2, 3, "left", 0, "right", 0),
                node(3, 5, "left", 0, "right", 0), // not linked to anything yet
                intArray(9, 1, 2))).get(0);

        assertTrue(scene.getNode("#9[0]").y() < scene.getNode("#1").y(), "the array is above the tree");
        assertEquals(scene.getNode("#1").y(), scene.getNode("#3").y(), "the new node is in the tree's row");
        assertTrue(scene.getNode("#3").x() > scene.getNode("#1").x(), "and beside it");
    }

    @Test
    void smallArraysShareARowAndAGrowingOneDoesNotPushItsNeighbour() {
        Snapshot before = snapshot(1, Map.of(), intArray(1, 1), intArray(2, 5));
        Snapshot after = snapshot(1, Map.of(), intArray(1, 1, 2, 3), intArray(2, 5));
        List<Scene> scenes = build("", before, after);

        assertEquals(scenes.get(0).getNode("#1[0]").y(), scenes.get(0).getNode("#2[0]").y(), "the same row");
        assertEquals(scenes.get(0).getNode("#2[0]").x(), scenes.get(1).getNode("#2[0]").x(), "the neighbour stays put");
        assertTrue(scenes.get(1).getNode("#1[2]").x() + scenes.get(1).getNode("#1[2]").w() < scenes.get(1).getNode("#2[0]").x());
    }

    @Test
    void aNewArrayDoesNotPushATreeDown() {
        ObjectNode tree = node(1, 8, "left", 0, "right", 0);
        List<Scene> scenes = build("", snapshot(1, Map.of(), tree), snapshot(1, Map.of(), tree, intArray(9, 1, 2)));

        assertEquals(scenes.get(0).getNode("#1").y(), scenes.get(1).getNode("#1").y());
        assertTrue(scenes.get(1).getNode("#9[0]").y() < scenes.get(1).getNode("#1").y());
    }

    @Test
    void aRowThatWouldGetTooWideWraps() {
        int[] wide = new int[10];
        Scene scene = build("", snapshot(1, Map.of(), intArray(1, wide), intArray(2, wide))).get(0);

        assertTrue(scene.getNode("#2[0]").y() > scene.getNode("#1[0]").y());
        assertEquals(scene.getNode("#1[0]").x(), scene.getNode("#2[0]").x());
    }

    @Test
    void anArrayInsideAnObjectOnlyHasMarkersWhileTheCodeUsesIt() {
        String source = "class Main {\n void f(Node x, Node y, int i) {\n  x.keys[i] = 1;\n  y.keys[i] = 2;\n }\n}\n";
        HeapObject[] objects = {bnode(1, 10, 11, 1), intArray(10, 1, 0), refArray(11, 0, 0, 0),
                bnode(2, 20, 21, 1), intArray(20, 2, 0), refArray(21, 0, 0, 0)};
        Scene scene = build(source, snapshot(3, Map.of("x", ref(1), "y", ref(2), "i", number(1)), objects)).get(0);

        assertNotNull(scene.getNode("idx:10:ROW:i"), "line 3 uses x.keys");
        assertNull(scene.getNode("idx:20:ROW:i"), "line 3 doesn't use y.keys");
    }

    @Test
    void aNodeStillBeingConstructedIsDrawnAsNew() {
        Scene scene = build("", snapshot(1, Map.of("t", ref(1)), bnode(1, 0, 0, 0))).get(0);

        assertEquals("new", scene.getNode("#1:new").text());
        assertEquals(SceneNode.Look.DIM, scene.getNode("#1:new").look());
    }

    @Test
    void aTrieNodeShowsItsFieldsAndOnlyItsUsedSlots() {
        Map<String, Value> fields = new LinkedHashMap<>();
        Map<String, String> types = new LinkedHashMap<>();
        fields.put("children", ref(11));
        types.put("children", NODE + "[]");
        fields.put("end", new PrimitiveValue("true"));
        types.put("end", "boolean");
        long[] slots = new long[26];
        slots[2] = 2;
        Map<String, Value> childFields = new LinkedHashMap<>(fields);
        childFields.put("children", ref(21));
        Scene scene = build("", snapshot(1, Map.of(), new ObjectNode(1, NODE, fields, types), refArray(11, slots),
                new ObjectNode(2, NODE, childFields, types), refArray(21, new long[26]))).get(0);

        assertEquals("end=true", scene.getNode("#1:data").text());
        assertEquals("2", scene.getNode("#11[2]").caption());
        assertNull(scene.getNode("#11[0]"), "an empty slot isn't drawn");
    }

    @Test
    void aListOfListsIsDrawnAsATableWithUnevenRows() {
        ArrayNode outer = new ArrayNode(1, "java.util.ArrayList", "java.lang.Object", List.of(ref(2), ref(3)), 2);
        ArrayNode first = new ArrayNode(2, "java.util.ArrayList", "java.lang.Object", List.of(number(5)), 1);
        ArrayNode second = new ArrayNode(3, "java.util.ArrayList", "java.lang.Object", List.of(number(6), number(7)), 2);
        Scene scene = build("", snapshot(1, Map.of(), outer, first, second)).get(0);

        assertNotNull(scene.getNode("#1:r1"), "row numbers");
        assertEquals(scene.getNode("#2[0]").x(), scene.getNode("#3[0]").x(), 0.01);
        assertTrue(scene.getNode("#3[0]").y() > scene.getNode("#2[0]").y());
        assertNull(scene.getEdge("#1[0]→"), "no long arrows");

        ArrayNode none = new ArrayNode(4, "java.util.ArrayList", "java.lang.Object", List.of(), 0);
        ArrayNode withEmptyRow = new ArrayNode(1, "java.util.ArrayList", "java.lang.Object", List.of(ref(2), ref(4)), 2);
        assertEquals("(empty)", build("", snapshot(1, Map.of(), withEmptyRow, first, none)).get(0).getNode("#4:empty").text());

        ArrayNode arrayOfLists = new ArrayNode(1, "java.util.ArrayList[]", "java.util.ArrayList", List.of(ref(2), ref(3)), 2);
        assertNotNull(build("", snapshot(1, Map.of(), arrayOfLists, first, second)).get(0).getNode("#1:r1"));

        ArrayNode notFilledYet = new ArrayNode(1, "java.util.ArrayList[]", "java.util.ArrayList", List.of(ref(2), ref(0)), 2);
        Scene filling = build("", snapshot(1, Map.of(), notFilledYet, first)).get(0);
        assertNotNull(filling.getNode("#1:r1"), "a table even before all its rows exist");
        assertEquals(SceneNode.Kind.NULL_SLOT, filling.getNode("#1[1]").kind());
    }

    @Test
    void anArrayHeldOnlyByAFieldIsNamedAfterIt() {
        Map<String, Value> fields = new LinkedHashMap<>();
        Map<String, String> types = new LinkedHashMap<>();
        fields.put("heap", ref(2));
        types.put("heap", "int[]");
        Scene scene = build("", snapshot(1, Map.of(), new ObjectNode(1, "Main$Box", fields, types), intArray(2, 1, 2))).get(0);
        assertEquals("heap", scene.getNode("field:#2").text());

        Scene named = build("", snapshot(1, Map.of("a", ref(2)), new ObjectNode(1, "Main$Box", fields, types), intArray(2, 1))).get(0);
        assertNull(named.getNode("field:#2"), "a variable already names it");
    }

    @Test
    void anObjectsFieldsAreSeparateShapesAndItsTitleShowsItsParent() {
        Map<String, Value> fields = new LinkedHashMap<>();
        Map<String, String> types = new LinkedHashMap<>();
        fields.put("id", number(1));
        types.put("id", "int");
        fields.put("balance", number(50));
        types.put("balance", "int");
        Scene before = build("", snapshot(1, Map.of(), new ObjectNode(1, "Main$Savings", fields, types, "Main$Account"))).get(0);
        assertEquals("Savings extends Account", before.getNode("#1").caption());
        assertEquals("id = 1", before.getNode("#1.line:id").text());
        assertEquals("balance = 50", before.getNode("#1.line:balance").text());
    }

    @Test
    void valuesAfterTheFirstInANodeAreNamed() {
        Map<String, Value> fields = new LinkedHashMap<>();
        Map<String, String> types = new LinkedHashMap<>();
        fields.put("key", number(20));
        types.put("key", "int");
        fields.put("height", number(3));
        types.put("height", "int");
        fields.put("next", ref(0));
        types.put("next", NODE);
        Scene scene = build("", snapshot(1, Map.of(), new ObjectNode(1, NODE, fields, types))).get(0);

        assertEquals("20 h=3", scene.getNode("#1").text());
    }
}
