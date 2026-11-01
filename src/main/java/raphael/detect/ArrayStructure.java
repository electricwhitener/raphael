package raphael.detect;

import java.util.Set;
import raphael.model.ArrayNode;

// A one-dimensional array, drawn as a row of cells.
// OOP: inheritance (extends Structure) and overriding.
public class ArrayStructure extends Structure {

    private final ArrayNode array;

    public ArrayStructure(ArrayNode array) {
        this.array = array;
    }

    public ArrayNode getArray() {
        return array;
    }

    @Override
    public Set<Long> getObjectIds() {
        return Set.of(array.getId());
    }

    @Override
    public String getKind() {
        return "array";
    }
}
