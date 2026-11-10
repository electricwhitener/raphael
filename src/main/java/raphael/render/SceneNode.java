package raphael.render;

// One shape to draw, with its position and size. The key (e.g. "#12" for object 12, "#7[2]" for
// a cell) stays the same from step to step, so the Animator can tell it's the same shape and move it.
// The look says how the shape appears at this moment of an animation.
// OOP: a record (a small unchangeable data class) with a nested enum, a nested record and an
// overloaded constructor.
public record SceneNode(String key, Kind kind, double x, double y, double w, double h,
                        String text, String caption, Look look) {

    public enum Kind {
        CELL,        // an array cell: value inside, index underneath
        SLOT,        // a pointer slot; an arrow starts here
        NULL_SLOT,   // a pointer slot holding null, drawn with a slash
        BOX,         // the value part of a linked-list node
        CIRCLE,      // a tree node
        RED_NODE,    // a red-black tree's red node (a soft rose, not the red of an error)
        BLACK_NODE,  // a red-black tree's black node
        VISITED_NODE, // a graph's vertex that has been visited (grey: the past)
        OBJECT,      // a plain object: the box and its class name
        FIELD,       // one field line inside a plain object, so only a field that changed flashes
        TEXT,        // plain grey text, e.g. row numbers
        FRAME,       // invisible outline of a whole array, for labels and arrows to point at
        LABEL,       // a variable name, like "curr"
        MARKER,      // an index variable under an array cell, like "j"
        SIDE_MARKER, // an index variable beside a grid row, like "r"
        FOCUS_READ,  // a cell the next line will read (dashed outline)
        FOCUS_WRITE, // a cell the next line will write (solid outline)
        FOCUS_OUT,   // an index past the end of an array: the next line is about to crash
        FLYER        // a copied value travelling from one cell to another
    }

    // How a shape looks at one moment. alpha: how visible (0 to 1). flash: changed-value
    // highlight (0 to 1). scale: size while appearing or leaving. oldText and roll: the old
    // value rolling out while the new one rolls in (roll goes from 0 to 1).
    public record Look(double alpha, double flash, double scale, String oldText, double roll) {
        public static final Look STILL = new Look(1, 0, 1, null, 1);
        public static final Look DIM = new Look(0.4, 0, 1, null, 1);
    }

    public SceneNode(String key, Kind kind, double x, double y, double w, double h, String text, String caption) {
        this(key, kind, x, y, w, h, text, caption, Look.STILL);
    }

    public double centerX() {
        return x + w / 2;
    }

    public double centerY() {
        return y + h / 2;
    }

    public double bottom() {
        return y + h;
    }
}
