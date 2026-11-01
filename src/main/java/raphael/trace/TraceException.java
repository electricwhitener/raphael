package raphael.trace;

// Thrown when a program can't be traced at all, for example when its JVM won't start.
// OOP: a custom exception. Inheritance: it extends Java's Exception class.
public class TraceException extends Exception {

    public TraceException(String message, Throwable cause) {
        super(message, cause);
    }
}
