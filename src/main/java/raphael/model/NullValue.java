package raphael.model;

// An arrow to nothing. There are two shared ones, by the word each language uses: Java's null
// (INSTANCE) and C's NULL (C), so the panels and moments say what the student wrote.
// OOP: implements the Value interface; the private constructor stops anyone making another one,
// so each word has exactly one shared object (a pair of singletons).
public final class NullValue implements Value {

    public static final NullValue INSTANCE = new NullValue("null");
    public static final NullValue C = new NullValue("NULL");

    private static final long serialVersionUID = 1L;

    private final String word; // missing in recordings made before C: read back as Java's null

    private NullValue(String word) {
        this.word = word;
    }

    // Reading a saved run gives back the shared NullValue for its word, not a second copy.
    private Object readResolve() {
        return "NULL".equals(word) ? C : INSTANCE;
    }

    @Override
    public String display() {
        return word;
    }
}
