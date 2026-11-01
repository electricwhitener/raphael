package raphael.trace;

import raphael.compile.CompileResult;
import raphael.model.Trace;

// Runs a compiled program and records a Snapshot before every line it runs.
// OOP: interface + abstraction. The rest of Raphael only knows this interface, not how JdiTracer
// does the work. Overloading: two trace methods with the same name but different parameters.
// Default methods: a Tracer doesn't have to support stopping or progress.
public interface Tracer {

    // input is typed into the program's System.in (for Scanner).
    Trace trace(CompileResult program, String input) throws TraceException;

    // Asks a run in progress to end now, keeping the steps it has. By default there's nothing to stop.
    default void stop() {
    }

    // How many steps the run in progress has recorded so far (0 if none is running).
    default int stepsSoFar() {
        return 0;
    }

    // For programs that don't read any input.
    default Trace trace(CompileResult program) throws TraceException {
        return trace(program, "");
    }
}
