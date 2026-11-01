package raphael.trace;

// The safety limits, kept in one place.
// OOP: a constants class. public static final values, and a private constructor so no objects are made.
public final class TraceLimits {

    public static final int MAX_STEPS = 2000;          // so an infinite loop still ends
    public static final int MAX_OBJECTS = 300;         // objects captured per snapshot
    public static final int MAX_ARRAY_CELLS = 64;      // cells kept per array
    public static final int MAX_FRAMES = 32;           // newest method calls read per step (deep recursion stays quick)
    public static final long EVENT_TIMEOUT_MS = 5000;  // longest wait for the program between two steps

    private TraceLimits() {
    }
}
