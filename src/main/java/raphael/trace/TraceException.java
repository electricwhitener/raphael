package raphael.trace;

import raphael.RaphaelException;

// Thrown when a program can't be traced at all, for example when its JVM won't start.
// OOP: a custom checked exception, in the RaphaelException family (inheritance).
public class TraceException extends RaphaelException {

    public TraceException(String message, Throwable cause) {
        super(message, cause);
    }
}
