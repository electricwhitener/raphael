package raphael.model;

import java.util.List;

// An array: the type of its elements and their values, in order. Java's collections (ArrayList,
// Stack, HashMap…) are stored this way too, as the list of their items.
// Very long arrays keep only their first cells (see TraceLimits); getLength() is still the real length.
// OOP: inheritance (extends HeapObject) and polymorphism (overrides describe).
public class ArrayNode extends HeapObject {

    private final String elementType; // e.g. "int", or "int[]" for a 2D array
    private final List<Value> elements;
    private final int length;

    public ArrayNode(long id, String typeName, String elementType, List<Value> elements, int length) {
        super(id, typeName);
        this.elementType = elementType;
        this.elements = List.copyOf(elements);
        this.length = length;
    }

    public String getElementType() {
        return elementType;
    }

    public List<Value> getElements() {
        return elements;
    }

    public int getLength() {
        return length;
    }

    // e.g. int[3] [5, 2, 9]. A grid only gives its size, like int[4][]; its cells are in the picture.
    @Override
    public String describe(Snapshot snapshot) {
        if (elementType.endsWith("[]")) {
            return shortName();
        }
        StringBuilder text = new StringBuilder(shortName() + " [");
        for (int i = 0; i < elements.size(); i++) {
            if (i > 0) {
                text.append(", ");
            }
            text.append(brief(elements.get(i), snapshot));
        }
        if (elements.size() < length) {
            text.append(", …");
        }
        return text.append("]").toString();
    }

    // e.g. int[3], or int[4][] for a grid: the first "[]" of the type, with the length inside it.
    // A collection has no "[]" in its type, so it shows its size like this: ArrayList(3).
    @Override
    public String shortName() {
        String type = Names.simple(getTypeName());
        return type.endsWith("[]") ? type.replaceFirst("\\[\\]", "[" + length + "]") : type + "(" + length + ")";
    }
}
