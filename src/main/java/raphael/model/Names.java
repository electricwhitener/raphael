package raphael.model;

// Small helper for type names.
// OOP: a utility class. Only static methods, and a private constructor so no objects are made.
public final class Names {

    private Names() {
    }

    // "java.lang.String" → "String", "Main$Node" → "Node", "Main$Node[]" → "Node[]".
    public static String simple(String typeName) {
        String name = typeName.substring(typeName.lastIndexOf('.') + 1);
        return name.substring(name.lastIndexOf('$') + 1);
    }
}
