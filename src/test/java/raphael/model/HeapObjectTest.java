package raphael.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HeapObjectTest {

    private static Snapshot snapshotOf(HeapObject... objects) {
        Map<Long, HeapObject> heap = new LinkedHashMap<>();
        for (HeapObject object : objects) {
            heap.put(object.getId(), object);
        }
        return new Snapshot(List.of(), heap, Map.of(), 0, null);
    }

    private static ObjectNode object(long id, Map<String, Value> fields) {
        return new ObjectNode(id, "Main$Node", fields, Map.of());
    }

    @Test
    void anArrayListsItsValuesWithoutIds() {
        ArrayNode array = new ArrayNode(1, "int[]", "int",
                List.of(new PrimitiveValue("5"), new PrimitiveValue("2")), 2);
        assertEquals("int[2] [5, 2]", array.describe(snapshotOf(array)));
    }

    @Test
    void aGridOnlyGivesItsSize() {
        ArrayNode grid = new ArrayNode(1, "int[][]", "int[]", List.of(new ReferenceValue(2)), 4);
        assertEquals("int[4][]", grid.describe(snapshotOf(grid)));
    }

    @Test
    void aReferenceShowsTheDataOfWhatItPointsAt() {
        ObjectNode next = object(2, Map.of("val", new PrimitiveValue("7")));
        Map<String, Value> fields = new LinkedHashMap<>();
        fields.put("val", new PrimitiveValue("5"));
        fields.put("next", new ReferenceValue(2));
        fields.put("other", NullValue.INSTANCE);
        ObjectNode node = object(1, fields);
        assertEquals("Node{val=5, next→7, other=null}", node.describe(snapshotOf(node, next)));
    }

    @Test
    void aReferenceToAnObjectWithNoDataShowsItsClass() {
        ObjectNode target = object(2, Map.of("next", NullValue.INSTANCE));
        ObjectNode node = object(1, Map.of("next", new ReferenceValue(2)));
        assertEquals("Node{next→Node}", node.describe(snapshotOf(node, target)));
    }

    @Test
    void aReferenceToAnObjectThatWasNotCapturedStillDescribes() {
        ObjectNode node = object(1, Map.of("next", new ReferenceValue(99)));
        assertEquals("Node{next→…}", node.describe(snapshotOf(node)));
    }
}
