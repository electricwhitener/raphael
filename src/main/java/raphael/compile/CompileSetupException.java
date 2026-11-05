package raphael.compile;

import raphael.RaphaelException;

// Raphael couldn't get ready to compile: there's no Java compiler (a JRE instead of a JDK), or
// the temporary folder for the compiled classes can't be written.
// OOP: a custom checked exception, in the RaphaelException family; constructor chaining with
// super(...).
public class CompileSetupException extends RaphaelException {

    public CompileSetupException(String message) {
        super(message);
    }

    public CompileSetupException(String message, Throwable cause) {
        super(message, cause);
    }
}
