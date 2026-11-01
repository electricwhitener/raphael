package raphael.model;

// An arrow to object #id. The object itself is in the Snapshot's heap.
// OOP: implements the Value interface.
public record ReferenceValue(long id) implements Value {

    @Override
    public String display() {
        return "#" + id;
    }
}
