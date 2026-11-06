package raphael.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

// An object with fields, e.g. a Node with val and next.
// OOP: inheritance (extends HeapObject), polymorphism (overrides describe), and an overloaded constructor.
public class ObjectNode extends HeapObject {

    private static final long serialVersionUID = 1L; // the file format's version for this class; see RunFile

    private final Map<String, Value> fields;      // field name → value, in declared order
    private final Map<String, String> fieldTypes; // field name → declared type, e.g. "Main$Node"
    private final String parentName; // the student's class this one extends, or null

    public ObjectNode(long id, String typeName, Map<String, Value> fields, Map<String, String> fieldTypes) {
        this(id, typeName, fields, fieldTypes, null);
    }

    public ObjectNode(long id, String typeName, Map<String, Value> fields, Map<String, String> fieldTypes, String parentName) {
        super(id, typeName);
        this.parentName = parentName;
        this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
        this.fieldTypes = Collections.unmodifiableMap(new LinkedHashMap<>(fieldTypes));
    }

    public String getParentName() {
        return parentName;
    }

    public Map<String, Value> getFields() {
        return fields;
    }

    public Value getField(String name) {
        return fields.get(name);
    }

    public String getFieldType(String name) {
        return fieldTypes.get(name);
    }

    // e.g. Node{val=5, next→7}
    @Override
    public String describe(Snapshot snapshot) {
        StringBuilder text = new StringBuilder(Names.simple(getTypeName()) + "{");
        boolean first = true;
        for (Map.Entry<String, Value> field : fields.entrySet()) {
            if (!first) {
                text.append(", ");
            }
            String sign = field.getValue() instanceof ReferenceValue ? "" : "=";
            text.append(field.getKey()).append(sign).append(brief(field.getValue(), snapshot));
            first = false;
        }
        return text.append("}").toString();
    }

    // The object's plain data, e.g. 7, or its class name if it only holds references.
    @Override
    public String shortName() {
        StringBuilder data = new StringBuilder();
        for (Value value : fields.values()) {
            if (value instanceof PrimitiveValue) {
                data.append(data.length() > 0 ? ", " : "").append(value.display());
            }
        }
        return data.length() > 0 ? data.toString() : Names.simple(getTypeName());
    }
}
