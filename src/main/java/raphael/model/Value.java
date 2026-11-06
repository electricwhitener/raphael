package raphael.model;

// The value stored in a variable, field or array cell: a primitive (5, 'a', "hi"),
// an arrow to an object (#12), or null.
// OOP: interface + polymorphism. Each kind of value shows itself in its own way. It extends
// Serializable, so every kind of value can be saved in a .raphael file (see io/RunFile).
public interface Value extends java.io.Serializable {

    // The text to show for this value, e.g. "5", "#12" or "null".
    String display();
}
