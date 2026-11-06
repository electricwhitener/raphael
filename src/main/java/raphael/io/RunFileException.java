package raphael.io;

import raphael.RaphaelException;

// A .raphael file that can't be read: it isn't one, a newer Raphael made it, or it's damaged
// (cut short while copying, for instance). The message says which, in words the user can act on.
// OOP: a custom checked exception, in the RaphaelException family; exception chaining (the cause
// keeps the low-level error, for the log).
public class RunFileException extends RaphaelException {

    public RunFileException(String message) {
        super(message);
    }

    public RunFileException(String message, Throwable cause) {
        super(message, cause);
    }
}
