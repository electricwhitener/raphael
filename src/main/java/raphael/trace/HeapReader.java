package raphael.trace;

import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.ArrayReference;
import com.sun.jdi.ArrayType;
import com.sun.jdi.CharValue;
import com.sun.jdi.ClassType;
import com.sun.jdi.Field;
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

    private final List<ReferenceType> studentClasses; // filled in by JdiTracer as classes load
    private final Map<ReferenceType, List<Field>> fieldsByType = new HashMap<>();
    // Holding on to every object we've seen stops JDI from recycling its id,
    // so object #12 is still #12 at every step.
    private final Set<ObjectReference> seen = new HashSet<>();
    private ThreadReference thread; // the paused thread, which runs the calls we make into the program
    // A boxed number (an Integer…) can never change, so each one is only read once. Asking the
    // paused program costs time, and a list of 64 Integers would otherwise ask 64 times per step.
    private final Map<ObjectReference, Value> boxedValues = new HashMap<>();

    public HeapReader(List<ReferenceType> studentClasses) {
        this.studentClasses = studentClasses;
    }

    // Takes a Snapshot of a paused thread. outputLength is asked for last, so the output
    // threads have as much time as possible to catch up.
    public Snapshot capture(ThreadReference thread, IntSupplier outputLength, String exception) {
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
        return new Snapshot(frames, heap, statics, outputLength.getAsInt(), exception);
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
                frames.add(new FrameSnapshot(Names.simple(className), methodName(where), where.lineNumber(), locals));
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

        List<Field> fields = fieldsByType.computeIfAbsent(object.referenceType(), HeapReader::studentFields);
        Map<Field, com.sun.jdi.Value> values = object.getValues(fields);
        Map<String, Value> fieldValues = new LinkedHashMap<>();
        Map<String, String> fieldTypes = new LinkedHashMap<>();
        for (Field field : fields) {
            fieldValues.put(field.name(), toValue(values.get(field), toVisit));
            fieldTypes.put(field.name(), field.typeName());
        }
        return new ObjectNode(object.uniqueID(), object.referenceType().name(), fieldValues, fieldTypes,
                parentOf(object.referenceType()));
    }

    // Every Java collection can list its items with toArray(), whichever way it stores them, so
    // we call that inside the paused program. A map lists its entries, shown as key=value.
    private ArrayNode readCollection(ObjectReference collection, Deque<ObjectReference> toVisit) {
        // List.of() and friends use hidden class names like ImmutableCollections$List12: just say List.
        String type = collection.referenceType().name().replaceAll("ImmutableCollections\\$(List|Set|Map).*", "$1");
        try {
            boolean map = isMap(collection);
            ObjectReference items = map ? (ObjectReference) call(collection, "entrySet", "()Ljava/util/Set;") : collection;
            ArrayReference array = (ArrayReference) call(items, "toArray", "()[Ljava/lang/Object;");
            List<Value> cells = readCells(array, array.length(), toVisit);
            if (map) {
                for (int i = 0; i < cells.size(); i++) {
                    ObjectReference entry = (ObjectReference) array.getValue(i);
                    cells.set(i, new PrimitiveValue(toValue(entryPart(entry, "key", "getKey"), toVisit).display()
                            + "=" + toValue(entryPart(entry, "value", "getValue"), toVisit).display()));
                }
            }
            return new ArrayNode(collection.uniqueID(), type, "java.lang.Object", cells, array.length());
        } catch (Exception e) {
            Logger.getLogger(HeapReader.class.getName()).log(Level.FINE, "Couldn't list the items of a " + type, e);
            return new ArrayNode(collection.uniqueID(), type, "java.lang.Object", List.of(), 0); // couldn't ask it
        }
    }

    // A map entry's key or value. Reading its field is far quicker than calling a method in the
    // program, so we only call the method for the few kinds of map whose entries have no such field.
    private com.sun.jdi.Value entryPart(ObjectReference entry, String field, String getter) throws Exception {
        Field direct = entry.referenceType().fieldByName(field);
        return direct != null ? entry.getValue(direct) : call(entry, getter, "()Ljava/lang/Object;");
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

    // Turns a JDI value into a Raphael Value. Objects we should draw are queued for the BFS.
    private Value toValue(com.sun.jdi.Value value, Deque<ObjectReference> toVisit) {
        if (value == null) {
            return NullValue.INSTANCE;
        }
        if (value instanceof CharValue) {
            return new PrimitiveValue("'" + ((CharValue) value).value() + "'");
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
        seen.add(object);
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
