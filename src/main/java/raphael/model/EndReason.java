package raphael.model;

// Why tracing stopped.
// OOP: an enum, a fixed set of named values (safer than using numbers or strings).
public enum EndReason {
    FINISHED,   // main returned (or the program called System.exit)
    STEP_LIMIT, // too many steps: probably an infinite loop
    EXCEPTION,  // the program crashed with an exception nobody caught
    TIMEOUT,    // the program stopped responding
    STOPPED     // the user pressed stop
}
