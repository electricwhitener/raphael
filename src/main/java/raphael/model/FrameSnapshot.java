package raphael.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

// One method call that is running: its class, its name, the line it's on, and its local variables.
// OOP: encapsulation. Private final fields with getters, and a map that can't be changed from outside.
public class FrameSnapshot {

    private final String className;
    private final String methodName;
    private final int line;
    private final Map<String, Value> locals; // variable name → value, in declared order

    public FrameSnapshot(String className, String methodName, int line, Map<String, Value> locals) {
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

    public Map<String, Value> getLocals() {
        return locals;
    }
}
