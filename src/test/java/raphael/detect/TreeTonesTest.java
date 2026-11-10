package raphael.detect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import raphael.model.NullValue;
import raphael.model.ObjectNode;
import raphael.model.PrimitiveValue;
import raphael.model.Value;

class TreeTonesTest {

    private static ObjectNode node(String colorField, String color) {
        Map<String, Value> fields = new LinkedHashMap<>();
        Map<String, String> types = new LinkedHashMap<>();
        fields.put("key", new PrimitiveValue("8"));
        types.put("key", "int");
        fields.put(colorField, new PrimitiveValue(color));
        types.put(colorField, "char");
        fields.put("height", new PrimitiveValue("2"));
        types.put("height", "int");
        for (String link : new String[] {"left", "right"}) {
            fields.put(link, NullValue.INSTANCE);
            types.put(link, "Node");
        }
        return new ObjectNode(1, "Node", fields, types);
    }

    // The colour is read the ways textbooks store it, and it's left out of the label.
    @Test
    void coloursAreReadFromTheField() {
        assertEquals("red", LinkedStructure.toneOf(node("color", "'R'")));
        assertEquals("black", LinkedStructure.toneOf(node("color", "BLACK")));
        assertEquals("red", LinkedStructure.toneOf(node("color", "true")));   // Sedgewick: RED = true
        assertEquals("black", LinkedStructure.toneOf(node("isBlack", "true")));
        assertNull(LinkedStructure.toneOf(node("weight", "3")));
        assertEquals("8", LinkedStructure.labelOf(node("color", "'R'")), "colour and height aren't in the label");
        assertEquals("h=2", LinkedStructure.captionOf(node("color", "'R'")));
    }

    // A parent link doesn't count as a third child: left, right and p is still a binary tree.
    @Test
    void aParentLinkIsABackLink() {
        assertTrue(LinkedStructure.isBackLink("p"));
        assertTrue(LinkedStructure.isBackLink("parent"));
        assertTrue(!LinkedStructure.isBackLink("next"));
        Map<String, Value> fields = new LinkedHashMap<>();
        Map<String, String> types = new LinkedHashMap<>();
        for (String link : new String[] {"left", "right", "p"}) {
            fields.put(link, NullValue.INSTANCE);
            types.put(link, "Node");
        }
        ObjectNode alone = new ObjectNode(1, "Node", fields, types);
        raphael.model.Snapshot step = new raphael.model.Snapshot(java.util.List.of(), Map.of(1L, alone), Map.of(), 0, null);
        assertEquals("binary tree", new StructureDetector().detect(step).get(0).getKind());
    }
}
