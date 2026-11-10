package raphael.trace;

import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.ArrayType;
import com.sun.jdi.CharValue;
import com.sun.jdi.ClassType;
import com.sun.jdi.Field;
import com.sun.jdi.IntegerValue;
import com.sun.jdi.InterfaceType;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.LocalVariable;
import com.sun.jdi.Location;
import com.sun.jdi.Method;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.StackFrame;
import com.sun.jdi.StringReference;
import com.sun.jdi.ThreadReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import raphael.model.ArrayNode;
import raphael.model.FrameSnapshot;
import raphael.model.HeapObject;
import raphael.model.Names;
import raphael.model.NullValue;
import raphael.model.ObjectNode;
import raphael.model.PrimitiveValue;
import raphael.model.ReferenceValue;
import raphael.model.Snapshot;
import raphael.model.ThrownException;
import raphael.model.Value;

// Reads the paused program's memory and copies it into a Snapshot made of plain Raphael objects.
// It starts from the local variables and static fields and follows every arrow to find each
// reachable object: a breadth-first search (BFS), the same walk as on a graph.
// (JDI has its own Value and PrimitiveValue types; those are written out in full as com.sun.jdi.….)
// Java's own objects are shown by name only, except collections (ArrayList, Stack, HashMap…):
// the paused program is asked for their items, which are then stored as an ordinary array.
// OOP: encapsulation. The queue, the caches and all the JDI calls are private; callers only use capture().
public class HeapReader {

    private static final Set<String> BOXED_TYPES = Set.of(
            "java.lang.Integer", "java.lang.Long", "java.lang.Short", "java.lang.Byte",
            "java.lang.Character", "java.lang.Boolean", "java.lang.Double", "java.lang.Float");
    private static final int MAX_STRING_LENGTH = 40;
    // Lists whose items are in an "elementData" array: the name of their size field.
    private static final Map<String, String> LIST_SIZE_FIELDS = Map.of(
            "java.util.ArrayList", "size", "java.util.Vector", "elementCount", "java.util.Stack", "elementCount");

    private final List<ReferenceType> studentClasses; // filled in by JdiTracer as classes load
    private final Map<ReferenceType, List<Field>> fieldsByType = new HashMap<>();
    // Every object we've seen, by id. Holding on to them stops JDI from recycling an id, so
    // object #12 is still #12 at every step, and lets the walk continue from a remembered object.
    private final Map<Long, ObjectReference> seen = new HashMap<>();
    // The student's objects as they were last read. Reading an object means a round trip to the
    // paused program, and a big list has hundreds, so an object is only read again once one of
    // its fields has been written (JdiTracer is told about every write; see fieldWritten).
    // Arrays and Java's collections are always read again: a write to an array cell can't be watched.
    private final Map<Long, ObjectNode> remembered = new HashMap<>();
    private final Set<Long> written = new HashSet<>(); // remembered objects that may have changed
    private boolean remembering = true;
    private int objectsRead; // round trips for objects, counted so a test can check remembering works
    private ThreadReference thread; // the paused thread, which runs the calls we make into the program
    // A boxed number (an Integer…) can never change, so each one is only read once. Asking the
    // paused program costs time, and a list of 64 Integers would otherwise ask 64 times per step.
    private final Map<ObjectReference, Value> boxedValues = new HashMap<>();

    private final Map<String, String> sourceFiles;

    public HeapReader(List<ReferenceType> studentClasses) {
        this(studentClasses, Map.of());
    }

    // sourceFiles: Java's name for each running file → the program's own path for it (see CompileResult).
    public HeapReader(List<ReferenceType> studentClasses, Map<String, String> sourceFiles) {
        this.studentClasses = studentClasses;
        this.sourceFiles = sourceFiles;
    }

    int objectsRead() {
        return objectsRead;
    }

    // A field of this object was written, so the next capture must read it again.
    public void fieldWritten(ObjectReference object) {
        written.add(object.uniqueID());
    }

    // Read every object at every step from now on. For a program with several threads: another
    // thread can be paused just before a write, so "nothing was written" can't be trusted.
    public void stopRemembering() {
        remembering = false;
        remembered.clear();
    }

    // Takes a Snapshot of a paused thread. outputLength is asked for last, so the output
    // threads have as much time as possible to catch up.
    public Snapshot capture(ThreadReference thread, IntSupplier outputLength, String exception) {
        return capture(thread, outputLength, exception, null);
    }

    // thrown: an exception the program caught just before this step, or null.
    public Snapshot capture(ThreadReference thread, IntSupplier outputLength, String exception, ThrownException thrown) {
        return capture(thread, outputLength, exception, thrown, java.util.Map.of());
    }

    // files: the files in the program's folder at this step (see RunFolder).
    public Snapshot capture(ThreadReference thread, IntSupplier outputLength, String exception, ThrownException thrown,
                            java.util.Map<String, String> files) {
        this.thread = thread;
        Deque<ObjectReference> toVisit = new ArrayDeque<>();
        List<FrameSnapshot> frames = readFrames(thread, toVisit);
        Map<String, Value> statics = readStatics(toVisit);

        Map<Long, HeapObject> heap = new LinkedHashMap<>();
        while (!toVisit.isEmpty() && heap.size() < TraceLimits.MAX_OBJECTS) {
            ObjectReference object = toVisit.poll();
            if (!heap.containsKey(object.uniqueID())) {
                heap.put(object.uniqueID(), readObject(object, toVisit));
            }
        }
        return new Snapshot(frames, heap, statics, outputLength.getAsInt(), exception, thrown, files);
    }

    private List<FrameSnapshot> readFrames(ThreadReference thread, Deque<ObjectReference> toVisit) {
        List<FrameSnapshot> frames = new ArrayList<>();
        try {
            for (StackFrame frame : thread.frames(0, Math.min(thread.frameCount(), TraceLimits.MAX_FRAMES))) {
                Location where = frame.location();
                String className = where.declaringType().name();
                if (JdiTracer.isJdkClass(className)) {
                    continue; // e.g. Scanner's own methods, when the program crashed inside one
                }
                Map<String, Value> locals = new LinkedHashMap<>();
                ObjectReference self = frame.thisObject();
                if (self != null) {
                    locals.put("this", toValue(self, toVisit));
                }
                try {
                    List<LocalVariable> variables = frame.visibleVariables();
                    Map<LocalVariable, com.sun.jdi.Value> values = frame.getValues(variables);
                    for (LocalVariable variable : variables) {
                        if (variable.isArgument() && where.method().name().equals("main")) {
                            continue; // main's args is always there and almost never used
                        }
                        locals.put(variable.name(), toValue(values.get(variable), toVisit));
                    }
                } catch (AbsentInformationException e) {
                    // Compiled without -g: there are no variable names to show.
                }
                frames.add(new FrameSnapshot(Names.simple(className), methodName(where), where.lineNumber(), locals,
                        JdiTracer.fileOf(where, sourceFiles)));
            }
        } catch (IncompatibleThreadStateException e) {
            throw new IllegalStateException("The program must be paused before it can be read", e);
        }
        return frames;
    }

    private static String methodName(Location where) {
        String name = where.method().name();
        if (name.equals("<init>")) {
            return Names.simple(where.declaringType().name()); // a constructor
        }
        if (name.equals("<clinit>")) {
            return "static init";
        }
        return name;
    }

    private Map<String, Value> readStatics(Deque<ObjectReference> toVisit) {
        Map<String, Value> statics = new LinkedHashMap<>();
        for (ReferenceType type : studentClasses) {
            List<Field> fields = new ArrayList<>();
            for (Field field : type.fields()) {
                if (field.isStatic() && !field.isSynthetic()) {
                    fields.add(field);
                }
            }
            if (fields.isEmpty()) {
                continue;
            }
            Map<Field, com.sun.jdi.Value> values = type.getValues(fields);
            for (Field field : fields) {
                statics.put(Names.simple(type.name()) + "." + field.name(), toValue(values.get(field), toVisit));
            }
        }
        return statics;
    }

    private HeapObject readObject(ObjectReference object, Deque<ObjectReference> toVisit) {
        if (object instanceof ArrayReference) {
            ArrayReference array = (ArrayReference) object;
            int length = array.length();
            String elementType = ((ArrayType) array.referenceType()).componentTypeName();
            return new ArrayNode(array.uniqueID(), array.referenceType().name(), elementType,
                    readCells(array, length, toVisit), length);
        }
        if (isCollection(object) || isMap(object)) {
            return readCollection(object, toVisit);
        }
        long id = object.uniqueID();
        ObjectNode before = remembered.get(id);
        if (remembering && before != null && !written.contains(id)) {
            for (Value value : before.getFields().values()) { // unchanged: just keep walking from it
                if (value instanceof ReferenceValue) {
                    toVisit.add(seen.get(((ReferenceValue) value).id()));
                }
            }
            return before;
        }
        written.remove(id);
        objectsRead++;

        List<Field> fields = fieldsByType.computeIfAbsent(object.referenceType(), HeapReader::studentFields);
        Map<Field, com.sun.jdi.Value> values = object.getValues(fields);
        Map<String, Value> fieldValues = new LinkedHashMap<>();
        Map<String, String> fieldTypes = new LinkedHashMap<>();
        for (Field field : fields) {
            fieldValues.put(field.name(), toValue(values.get(field), toVisit));
            fieldTypes.put(field.name(), field.typeName());
        }
        ObjectNode node = new ObjectNode(id, object.referenceType().name(), fieldValues, fieldTypes,
                parentOf(object.referenceType()));
        if (remembering) {
            remembered.put(id, node);
        }
        return node;
    }

    // Every Java collection can list its items with toArray(), whichever way it stores them, so
    // we call that inside the paused program. A map lists its entries, shown as key=value.
    private ArrayNode readCollection(ObjectReference collection, Deque<ObjectReference> toVisit) {
        // List.of() and friends use hidden class names like ImmutableCollections$List12: just say List.
        String type = collection.referenceType().name().replaceAll("ImmutableCollections\\$(List|Set|Map).*", "$1");
        try {
            ArrayNode direct = readListFields(collection, type, toVisit);
            if (direct != null) {
                return direct;
            }
            boolean map = isMap(collection);
            ObjectReference items = map ? (ObjectReference) call(collection, "entrySet", "()Ljava/util/Set;") : collection;
            ArrayReference array = (ArrayReference) call(items, "toArray", "()[Ljava/lang/Object;");
            List<Value> cells = readCells(array, array.length(), toVisit);
            if (map) {
                for (int i = 0; i < cells.size(); i++) {
                    ObjectReference entry = (ObjectReference) array.getValue(i);
                    com.sun.jdi.Value[] keyAndValue = entryParts(entry);
                    cells.set(i, new PrimitiveValue(toValue(keyAndValue[0], toVisit).display()
                            + "=" + toValue(keyAndValue[1], toVisit).display()));
                }
            }
            return new ArrayNode(collection.uniqueID(), type, "java.lang.Object", cells, array.length());
        } catch (Exception e) {
            Logger.getLogger(HeapReader.class.getName()).log(Level.FINE, "Couldn't list the items of a " + type, e);
            return new ArrayNode(collection.uniqueID(), type, "java.lang.Object", List.of(), 0); // couldn't ask it
        }
    }

    // The lists DSA code uses most keep their items in an array field and their size in an int
    // field. Reading those two fields is much quicker than calling toArray() inside the paused
    // program. Null for any other kind of collection, which is then asked with toArray().
    private ArrayNode readListFields(ObjectReference list, String type, Deque<ObjectReference> toVisit) {
        String sizeField = LIST_SIZE_FIELDS.get(list.referenceType().name());
        Field data = sizeField == null ? null : list.referenceType().fieldByName("elementData");
        Field size = sizeField == null ? null : list.referenceType().fieldByName(sizeField);
        if (data == null || size == null) {
            return null;
        }
        Map<Field, com.sun.jdi.Value> values = list.getValues(List.of(data, size));
        int count = ((IntegerValue) values.get(size)).value();
        List<Value> cells = readCells((ArrayReference) values.get(data), count, toVisit);
        return new ArrayNode(list.uniqueID(), type, "java.lang.Object", cells, count);
    }

    // A map entry's key and value, in one round trip when they're fields (most maps). Only the
    // few kinds of map whose entries have no such fields are asked with getKey() and getValue().
    private com.sun.jdi.Value[] entryParts(ObjectReference entry) throws Exception {
        Field key = entry.referenceType().fieldByName("key");
        Field value = entry.referenceType().fieldByName("value");
        if (key != null && value != null) {
            Map<Field, com.sun.jdi.Value> both = entry.getValues(List.of(key, value));
            return new com.sun.jdi.Value[] {both.get(key), both.get(value)};
        }
        return new com.sun.jdi.Value[] {call(entry, "getKey", "()Ljava/lang/Object;"),
                call(entry, "getValue", "()Ljava/lang/Object;")};
    }

    // Calls a method with no arguments inside the paused program.
    private com.sun.jdi.Value call(ObjectReference object, String name, String signature) throws Exception {
        Method method = ((ClassType) object.referenceType()).concreteMethodByName(name, signature);
        return object.invokeMethod(thread, method, List.of(), ObjectReference.INVOKE_SINGLE_THREADED);
    }

    private static boolean isCollection(ObjectReference object) {
        return isJavaType(object, "java.util.Collection");
    }

    private static boolean isMap(ObjectReference object) {
        return isJavaType(object, "java.util.Map");
    }

    // True for one of Java's own classes that implements this interface. (A student's own class
    // that implements it is drawn from its fields, like any other class.)
    private static boolean isJavaType(ObjectReference object, String interfaceName) {
        ReferenceType type = object.referenceType();
        if (!(type instanceof ClassType) || !JdiTracer.isJdkClass(type.name())) {
            return false;
        }
        for (InterfaceType implemented : ((ClassType) type).allInterfaces()) {
            if (implemented.name().equals(interfaceName)) {
                return true;
            }
        }
        return false;
    }

    // The first cells of an array that are in use, up to the limit on how many we keep.
    private List<Value> readCells(ArrayReference array, int inUse, Deque<ObjectReference> toVisit) {
        int kept = Math.min(inUse, TraceLimits.MAX_ARRAY_CELLS);
        List<Value> cells = new ArrayList<>();
        if (kept > 0) {
            for (com.sun.jdi.Value cell : array.getValues(0, kept)) {
                cells.add(toValue(cell, toVisit));
            }
        }
        return cells;
    }

    // The student's class that this class extends, or null if it only extends one of Java's.
    private static String parentOf(ReferenceType type) {
        ClassType parent = type instanceof ClassType ? ((ClassType) type).superclass() : null;
        return parent == null || JdiTracer.isJdkClass(parent.name()) ? null : parent.name();
    }

    // The object's own fields (and its parent classes' fields), but not static ones,
    // not ones Java adds by itself, and not ones inherited from Java's own classes.
    private static List<Field> studentFields(ReferenceType type) {
        List<Field> fields = new ArrayList<>();
        for (Field field : type.allFields()) {
            if (!field.isStatic() && !field.isSynthetic() && !JdiTracer.isJdkClass(field.declaringType().name())) {
                fields.add(field);
            }
        }
        return fields;
    }

    // A char as Java code writes it: 'a', or '\0' for an empty char[] cell (a char that isn't
    // a letter has no glyph, so it would be drawn as a box).
    static String charLiteral(char c) {
        switch (c) {
            case 0:
                return "'\\0'";
            case '\n':
                return "'\\n'";
            case '\t':
                return "'\\t'";
            case '\r':
                return "'\\r'";
            default:
                return Character.isISOControl(c) ? String.format("'\\u%04x'", (int) c) : "'" + c + "'";
        }
    }

    // Turns a JDI value into a Raphael Value. Objects we should draw are queued for the BFS.
    private Value toValue(com.sun.jdi.Value value, Deque<ObjectReference> toVisit) {
        if (value == null) {
            return NullValue.INSTANCE;
        }
        if (value instanceof CharValue) {
            return new PrimitiveValue(charLiteral(((CharValue) value).value()));
        }
        if (value instanceof com.sun.jdi.PrimitiveValue) {
            return new PrimitiveValue(value.toString());
        }
        if (value instanceof StringReference) {
            return new PrimitiveValue(quote(((StringReference) value).value()));
        }
        ObjectReference object = (ObjectReference) value;
        String type = object.referenceType().name();
        if (BOXED_TYPES.contains(type)) {
            // An Integer (or Double, …) is shown as its number.
            Value number = boxedValues.get(object);
            if (number == null) {
                number = toValue(object.getValue(object.referenceType().fieldByName("value")), toVisit);
                boxedValues.put(object, number);
            }
            return number;
        }
        if (object.referenceType() instanceof ClassType && ((ClassType) object.referenceType()).isEnum()) {
            // An enum constant is shown as its name, kept in a private field of its parent, java.lang.Enum.
            // Inside the enum's own constructor, Java hasn't set the name yet.
            ClassType parent = ((ClassType) object.referenceType()).superclass();
            com.sun.jdi.Value name = object.getValue(parent.fieldByName("name"));
            return new PrimitiveValue(name == null ? Names.simple(type) : ((StringReference) name).value());
        }
        if (!(object instanceof ArrayReference) && !isCollection(object) && !isMap(object) && JdiTracer.isJdkClass(type)) {
            return new PrimitiveValue(Names.simple(type)); // Java's own objects are shown by name only
        }
        seen.put(object.uniqueID(), object);
        toVisit.add(object);
        return new ReferenceValue(object.uniqueID());
    }

    private static String quote(String text) {
        if (text.length() > MAX_STRING_LENGTH) {
            text = text.substring(0, MAX_STRING_LENGTH) + "…";
        }
        return "\"" + text + "\"";
    }
}
