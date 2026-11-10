package raphael.render;

// An arrow or a line from one shape to another, found by their keys. Its own key is chosen so
// that the same pointer keeps its key when it starts pointing somewhere else: "#12.next→" is
// node 12's next pointer, whatever it points at. That lets the Animator swing the arrow to its
// new target instead of fading one arrow out and another one in.
// OOP: a record with a nested enum, a nested record and an overloaded constructor.
public record SceneEdge(String key, String from, String to, Kind kind, Look look) {

    public enum Kind {
        ARROW,      // a reference, e.g. next → node
        PREV_ARROW, // a doubly linked list's prev reference, drawn a little lower
        TREE,       // a parent–child line in a tree
        POINTER,    // from a variable label down to its object
        EXTENDS,    // UML: a child class up to its parent (a solid line, a hollow triangle)
        IMPLEMENTS  // UML: a class up to an interface it implements (dashed, a hollow triangle)
    }

    // How an arrow looks at one moment. alpha: how visible. oldTo and swing: it is swinging from
    // its old target to its new one (swing goes from 0 to 1). reach: how far it reaches
    // (1 = all the way, 0 = pulled back into where it starts).
    public record Look(double alpha, String oldTo, double swing, double reach) {
        public static final Look STILL = new Look(1, null, 1, 1);
    }

    public SceneEdge(String key, String from, String to, Kind kind) {
        this(key, from, to, kind, Look.STILL);
    }
}
