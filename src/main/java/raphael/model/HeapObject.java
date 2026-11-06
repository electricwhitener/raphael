package raphael.model;

// One object in the program's memory, identified by the id the debugger gives it.
// OOP: abstraction. This is an abstract class, so you can't make a plain HeapObject, only an
// ObjectNode or an ArrayNode. Encapsulation: private final fields, read through getters.
public abstract class HeapObject implements java.io.Serializable {

    private static final long serialVersionUID = 1L; // the file format's version for this class; see RunFile

    private final long id;
    private final String typeName;

    protected HeapObject(long id, String typeName) {
        this.id = id;
        this.typeName = typeName;
    }

    public long getId() {
        return id;
    }

    // The full type name, e.g. "Main$Node" or "int[]".
    public String getTypeName() {
        return typeName;
    }

    // One line of text describing the object, e.g. Node{val=5, next→7}. Each subclass writes its
    // own version. It needs the Snapshot to look up what its references point at.
    public abstract String describe(Snapshot snapshot);

    // A very short name for this object, shown when something else points at it, e.g. 7 or int[5].
    public abstract String shortName();

    // A value as it looks inside a description: a reference shows what it points at, e.g. →7.
    protected static String brief(Value value, Snapshot snapshot) {
        if (value instanceof ReferenceValue) {
            HeapObject target = snapshot.getObject(((ReferenceValue) value).id());
            return "→" + (target == null ? "…" : target.shortName());
        }
        return value.display();
    }
}
