package raphael.model;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

// One method call that is running: its class, its name, the line it's on, and its local variables.
// OOP: encapsulation. Private final fields with getters, and a map that can't be changed from outside.
public class FrameSnapshot implements Serializable {

    private static final long serialVersionUID = 1L; // the file format's version for this class; see RunFile

    private final String className;
    private final String methodName;
    private final int line;
    private final Map<String, Value> locals; // variable name → value, in declared order
    // The program's file the line is in ("shapes/Circle.java"), or null for a program of one file
    // (and in recordings made before programs could have several files).
    private final String file;

    public FrameSnapshot(String className, String methodName, int line, Map<String, Value> locals) {
        this(className, methodName, line, locals, null);
    }

    public FrameSnapshot(String className, String methodName, int line, Map<String, Value> locals, String file) {
        this.file = file;
        this.className = className;
        this.methodName = methodName;
        this.line = line;
        this.locals = Collections.unmodifiableMap(new LinkedHashMap<>(locals));
    }

    public String getClassName() {
        return className;
    }

    public String getMethodName() {
        return methodName;
    }

    public int getLine() {
        return line;
    }

    public String getFile() {
        return file;
    }

    public Map<String, Value> getLocals() {
        return locals;
    }
}
