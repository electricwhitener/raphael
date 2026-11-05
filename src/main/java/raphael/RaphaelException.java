package raphael;

// The parent of every problem Raphael expects and can explain to the user, such as "no Java
// compiler found" or "the program's JVM wouldn't start". The window catches this one type and
// shows its message. Anything else that goes wrong is a bug in Raphael, and ErrorReporter
// handles it instead.
// OOP: an abstract class, so only its specific kinds can be thrown; inheritance (extends
// Exception, which makes it a checked exception: callers must catch it or declare it);
// exception chaining: the cause keeps the low-level error that started it.
//
//   Exception
//    └ RaphaelException (abstract)
//       ├ CompileSetupException   compile/  can't get ready to compile
//       └ TraceException          trace/    can't run or record the program
public abstract class RaphaelException extends Exception {

    protected RaphaelException(String message) {
        super(message);
    }

    protected RaphaelException(String message, Throwable cause) {
        super(message, cause);
    }
}
