package raphael.detect;

import java.util.Set;
import raphael.model.ObjectNode;

// Any other object, drawn as a box listing its fields.
// OOP: inheritance (extends Structure) and overriding.
public class GenericStructure extends Structure {

    private final ObjectNode object;

    public GenericStructure(ObjectNode object) {
        this.object = object;
    }

    public ObjectNode getObject() {
        return object;
    }

    @Override
    public Set<Long> getObjectIds() {
        return Set.of(object.getId());
    }

    @Override
    public String getKind() {
        return "object";
    }
}
