package raphael.model;

// A plain value, already turned into the text we show: 5, true, 'a', "hi".
// Java's own objects (Scanner, Random…) are also shown this way, by type name only.
// OOP: implements the Value interface (a record: a tiny class whose fields Java makes private and final).
public record PrimitiveValue(String text) implements Value {

    @Override
    public String display() {
        return text;
    }
}
