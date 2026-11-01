package raphael.ui;

// Anything that wants to know when the step changes. This is the Observer pattern: the panels
// "observe" the TraceModel, and the model tells them all when something changes.
// OOP: an interface with a default method.
public interface TraceListener {

    // Called after moving to another step.
    void stepChanged(TraceModel model);

    // Called when a new run is loaded (or the old one is cleared). By default, treat it like a step change.
    default void traceChanged(TraceModel model) {
        stepChanged(model);
    }
}
