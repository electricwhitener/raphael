package raphael.detect;

import java.util.Set;

// A data structure Raphael knows how to draw: an array, a grid, a linked list, a binary tree,
// or a plain box for any other object.
// OOP: abstraction. An abstract class at the top of an inheritance hierarchy. Each subclass
// answers getObjectIds() and getKind() in its own way (polymorphism).
public abstract class Structure {

    // The ids of the heap objects this structure draws.
    public abstract Set<Long> getObjectIds();

    // What kind of structure this is, e.g. "linked list".
    public abstract String getKind();

    // Inherited as-is by every subclass.
    public boolean contains(long id) {
        return getObjectIds().contains(id);
    }
}
