package raphael.model;

// A method of the student's that returned between two steps: its class, its name (a constructor
// by its class's name) and what it returned (null for a void method or a constructor).
// The exits between two steps come in the order they happened, each from the top of the call
// stack the step before had, so a call's return value is never lost inside one line
// (in return n * factorial(n - 1), every factorial returns before the next line starts).
// OOP: a record, Serializable so a recording keeps it.
public record Exit(String className, String method, Value returned) implements java.io.Serializable {
}
