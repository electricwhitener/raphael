package raphael.model;

// An arrow to nothing. There is only one NullValue, shared by everyone (a singleton).
// OOP: implements the Value interface; the private constructor stops anyone making another one.
public final class NullValue implements Value {

    public static final NullValue INSTANCE = new NullValue();

    private NullValue() {
    }

    @Override
    public String display() {
        return "null";
    }
}
