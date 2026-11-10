package raphael.model;

// One write to a field between two steps: which object (its id; -1 for a static field), the
// field's class and name, the value written, and the method and line that wrote it.
// OOP: a record, Serializable so a recording keeps it.
public record FieldWrite(long objectId, String className, String field, Value value, String method, int line)
        implements java.io.Serializable {
}
