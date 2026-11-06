package raphael.model;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Everything at one pause: the running methods (call stack), every object the program can reach,
// the static fields, and how much output had been printed. A Snapshot never changes after it's made.
// OOP: encapsulation (private final fields, read-only collections). Memento pattern: it is a
// saved copy of the program's state, so Raphael can go back to it later.
public class Snapshot implements Serializable {

    private static final long serialVersionUID = 1L; // the file format's version for this class; see RunFile

    private final List<FrameSnapshot> frames;  // the method running now first, main last
    private final Map<Long, HeapObject> heap;  // object id → object
    private final Map<String, Value> statics;  // e.g. "Main.count" → value
    private final int outputLength;            // how many characters had been printed so far
    private final String exception;            // null unless the program crashed at this step
    private final ThrownException thrown;      // an exception caught just before this step, or null
    private final Map<String, String> files;   // the text files in the program's folder: name → contents

    public Snapshot(List<FrameSnapshot> frames, Map<Long, HeapObject> heap, Map<String, Value> statics,
                    int outputLength, String exception) {
        this(frames, heap, statics, outputLength, exception, null);
    }

    // thrown: an exception caught just before this step (the first line of its catch), or null.
    public Snapshot(List<FrameSnapshot> frames, Map<Long, HeapObject> heap, Map<String, Value> statics,
                    int outputLength, String exception, ThrownException thrown) {
        this(frames, heap, statics, outputLength, exception, thrown, Map.of());
    }

    // files: the files in the program's folder at this step, name → contents. The same unchangeable
    // map is passed in for every step where nothing changed, so it's stored once, not copied.
    public Snapshot(List<FrameSnapshot> frames, Map<Long, HeapObject> heap, Map<String, Value> statics,
                    int outputLength, String exception, ThrownException thrown, Map<String, String> files) {
        this.thrown = thrown;
        this.files = files;
        this.frames = List.copyOf(frames);
        this.heap = Collections.unmodifiableMap(new LinkedHashMap<>(heap));
        this.statics = Collections.unmodifiableMap(new LinkedHashMap<>(statics));
        this.outputLength = outputLength;
        this.exception = exception;
    }

    // The line about to run, or -1 if none of the student's code is running.
    public int getLine() {
        return frames.isEmpty() ? -1 : frames.get(0).getLine();
    }

    // The text files in the program's folder at this step, by name, in name order.
    public Map<String, String> getFiles() {
        return files;
    }

    public List<FrameSnapshot> getFrames() {
        return frames;
    }

    public Map<Long, HeapObject> getHeap() {
        return heap;
    }

    // The object with this id, or null if it wasn't captured.
    public HeapObject getObject(long id) {
        return heap.get(id);
    }

    public Map<String, Value> getStatics() {
        return statics;
    }

    public int getOutputLength() {
        return outputLength;
    }

    public ThrownException getThrown() {
        return thrown;
    }

    public String getException() {
        return exception;
    }
}
