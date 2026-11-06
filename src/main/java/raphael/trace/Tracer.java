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

    // The limits for the next run (the user's settings). By default a tracer has its own fixed limits.
    default void setLimits(RunLimits limits) {
    }

    // Files to put in the program's folder before each run, for it to read. By default there are none.
    default void setInputFiles(java.util.List<java.nio.file.Path> files) {
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
