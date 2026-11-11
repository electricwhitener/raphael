package raphael.model;

import java.io.Serializable;
import java.util.List;

// The whole run: one Snapshot per step, everything the program printed, and why it ended.
// Going back a step just means showing snapshot i - 1.
// OOP: encapsulation. The list is private and read-only, so nobody can change a recorded run.
// Memento pattern: the Trace is the album that stores the saved states (Snapshots).
// Serializable: a whole run can be written to a file and read back (io/RunFile).
public class Trace implements Serializable {

    private static final long serialVersionUID = 1L; // the file format's version for this class; see RunFile

    private final List<Snapshot> snapshots;
    private final String output;
    private final EndReason endReason;
    private final String endMessage;
    private final List<TypedLine> typed; // null in a recording saved before the terminal (1.4.0)

    public Trace(List<Snapshot> snapshots, String output, EndReason endReason, String endMessage) {
        this(snapshots, output, endReason, endMessage, List.of());
    }

    public Trace(List<Snapshot> snapshots, String output, EndReason endReason, String endMessage, List<TypedLine> typed) {
        this.snapshots = List.copyOf(snapshots);
        this.output = output;
        this.endReason = endReason;
        this.endMessage = endMessage;
        this.typed = List.copyOf(typed);
    }

    public int size() {
        return snapshots.size();
    }

    public Snapshot get(int step) {
        return snapshots.get(step);
    }

    public List<Snapshot> getSnapshots() {
        return snapshots;
    }

    // Everything the program printed during the whole run.
    public String getOutput() {
        return output;
    }

    // What had been printed by this step. At the last step the program is over, so it's everything.
    public String getOutputAt(int step) {
        if (step == snapshots.size() - 1) {
            return output;
        }
        return output.substring(0, Math.min(output.length(), get(step).getOutputLength()));
    }

    // The lines typed into the terminal while it ran, in the order they were typed.
    public List<TypedLine> getTyped() {
        return typed == null ? List.of() : typed;
    }

    public EndReason getEndReason() {
        return endReason;
    }

    public String getEndMessage() {
        return endMessage;
    }
}
