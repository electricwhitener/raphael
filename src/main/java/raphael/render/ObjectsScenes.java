package raphael.render;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import raphael.analysis.CodeFacts;
import raphael.model.FrameSnapshot;
import raphael.model.HeapObject;
import raphael.model.Names;
import raphael.model.NullValue;
import raphael.model.ObjectNode;
import raphael.model.ReferenceValue;
import raphael.model.Snapshot;
import raphael.model.Trace;
import raphael.model.Value;

// The objects view, for object-oriented programs. On the left, the program's classes as a small UML
// class diagram: each with its fields and methods, "-" for private and "+" for the rest, parents
// above their children, a solid line up to a parent class and a dashed one up to an interface.
// On the right, the objects alive at this step, in columns: private fields marked "-", inherited
// ones saying which class they come from. The object whose method is running is outlined in blue,
// with the method under it.
// OOP: a class that builds Scene objects (drawn and animated like the picture's), from the run
// and from what the code says (CodeFacts).
public class ObjectsScenes implements SceneSource {

    private static final int PER_ROW = 3;      // boxes in a row before the row wraps
    private static final double MIN_W = 150;
    private static final double MAX_W = 300;
    private static final double PAD = 18;      // room either side of a box's longest line
    private static final double GAP = 28;
    private static final double LAYER_GAP = 36; // between a parent's row and its children's
    private static final double SIDE_GAP = 64;  // between the classes and the objects beside them
    private static final double TOP = 26;       // room for the two headings
    private static final double MARGIN = 8;     // so no box touches the panel's edge

    private final Trace trace;
    private final CodeFacts facts;
    private final List<List<String>> classRows = new ArrayList<>(); // parents' row first
    private final List<String> classes = new ArrayList<>();
    private final Map<Integer, Scene> built = new HashMap<>();
    private final double classW;
    private final double objectW;
    private final double classesW;
    private final double classesH;
    private final double slotH;      // one object's room in its column, the same all run
    private final int perColumn;
    private final Dimension size;

    public ObjectsScenes(Trace trace, CodeFacts facts) {
        this.trace = trace;
        this.facts = facts;
        Set<String> names = new LinkedHashSet<>();
        for (CodeFacts.MethodFact method : facts.methods()) {
            if (!method.name().equals("main") && !method.name().equals(method.className())) {
                names.add(method.className());
            }
        }
        for (CodeFacts.FieldFact field : facts.fields()) {
            names.add(field.className());
        }
        names.addAll(facts.superclasses().keySet());
        names.addAll(facts.superclasses().values());
        for (List<String> implemented : facts.interfaces().values()) {
            names.addAll(implemented);
        }
        names.removeIf(name -> !facts.interfaces().containsKey(name) || onlyMain(name)); // only the program's own classes
        classes.addAll(names);
        layOutClasses();
        double widest = MIN_W;
        for (String name : classes) {
            widest = Math.max(widest, Style.textWidth(title(name), Style.TITLE_FONT) + PAD);
            for (String line : classLines(name)) {
                widest = Math.max(widest, Style.textWidth(line, Style.FIELD_FONT) + PAD);
            }
        }
        classW = Math.min(MAX_W, widest);
        double classesH = 0;
        for (List<String> row : classRows) {
            classesH += rowHeight(row) + LAYER_GAP;
        }
        this.classesH = Math.max(0, classesH - LAYER_GAP);
        classesW = maxRow() * (classW + GAP) - GAP;
        widest = MIN_W;
        int most = 0;
        int tallest = 0;
        for (Snapshot step : trace.getSnapshots()) {
            List<ObjectNode> objects = objectsOf(step);
            most = Math.max(most, objects.size());
            for (ObjectNode object : objects) {
                widest = Math.max(widest, Style.textWidth("a " + Names.simple(object.getTypeName()), Style.TITLE_FONT) + PAD);
                List<String> lines = objectLines(object, step);
                tallest = Math.max(tallest, lines.size());
                for (String line : lines) {
                    widest = Math.max(widest, Style.textWidth(line, Style.FIELD_FONT) + PAD);
                }
            }
        }
        objectW = Math.min(MAX_W, widest);
        slotH = boxHeight(tallest) + Style.LINE_H + 10; // and "running m()" under it
        perColumn = Math.max(2, (int) ((this.classesH + 10) / slotH));
        int columns = Math.max(1, (most + perColumn - 1) / perColumn);
        double width = 2 * MARGIN + classesW + SIDE_GAP + columns * (objectW + GAP) - GAP;
        double height = TOP + Math.max(this.classesH, Math.min(most, perColumn) * slotH) + 8;
        size = new Dimension((int) Math.max(400, width), (int) height);
    }

    // Whether the view has anything to show: the program has classes of its own besides main's.
    public boolean hasClasses() {
        return !classes.isEmpty();
    }

    @Override
    public Dimension getRunSize() {
        return size;
    }

    @Override
    public Scene sceneAt(int step) {
        return built.computeIfAbsent(step, this::build);
    }

    // UML's layout: a parent above its children. A class's layer is one below its parent's (its
    // superclass, else the first interface it implements), and a layer wider than PER_ROW wraps.
    private void layOutClasses() {
        Map<String, Integer> layers = new HashMap<>();
        int deepest = 0;
        for (String name : classes) {
            int layer = 0;
            for (String up = parentOf(name); up != null && layer < classes.size(); up = parentOf(up)) {
                layer++;
            }
            layers.put(name, layer);
            deepest = Math.max(deepest, layer);
        }
        for (int layer = 0; layer <= deepest; layer++) {
            List<String> row = new ArrayList<>();
            for (String name : classes) {
                if (layers.get(name) == layer) {
                    row.add(name);
                    if (row.size() == PER_ROW) {
                        classRows.add(row);
                        row = new ArrayList<>();
                    }
                }
            }
            if (!row.isEmpty()) {
                classRows.add(row);
            }
        }
    }

    // The class drawn above this one: its superclass, else the first interface it implements.
    private String parentOf(String name) {
        String parent = facts.superclasses().get(name);
        if (parent != null && classes.contains(parent)) {
            return parent;
        }
        for (String implemented : facts.interfaces().getOrDefault(name, List.of())) {
            if (classes.contains(implemented)) {
                return implemented;
            }
        }
        return null;
    }

    private int maxRow() {
        int most = 1;
        for (List<String> row : classRows) {
            most = Math.max(most, row.size());
        }
        return most;
    }

    private double rowHeight(List<String> row) {
        double tallest = 0;
        for (String name : row) {
            tallest = Math.max(tallest, boxHeight(classLines(name).size()));
        }
        return tallest;
    }

    private static double boxHeight(int lines) {
        return Style.TITLE_H + Math.max(1, lines) * Style.LINE_H + 4;
    }

    // Where a row of n class boxes starts, so the row is centred over the diagram.
    private double rowStart(int n) {
        return MARGIN + (classesW - (n * (classW + GAP) - GAP)) / 2;
    }

    private String title(String name) {
        return (facts.isInterface(name) ? "«interface» " : facts.isAbstract(name) ? "«abstract» " : "") + name;
    }

    private Scene build(int step) {
        Scene scene = new Scene();
        // Two headings: the classes are the code's plans, the objects are what exists right now.
        scene.add(new SceneNode("heading:classes", SceneNode.Kind.TEXT, MARGIN, 0, classesW, Style.LINE_H, "classes (the code)", null));
        scene.add(new SceneNode("heading:objects", SceneNode.Kind.TEXT, MARGIN + classesW + SIDE_GAP, 0, objectW, Style.LINE_H, "objects (now)", null));
        Snapshot now = trace.get(step);
        FrameSnapshot frame = now.getFrames().isEmpty() ? null : now.getFrames().get(0);
        String runningClass = frame == null ? null : Names.simple(frame.getClassName());
        String runningMethod = frame == null ? null : frame.getMethodName();
        double y = TOP;
        for (List<String> row : classRows) {
            double x = rowStart(row.size());
            for (String name : row) {
                List<String> lines = classLines(name);
                scene.add(new SceneNode("class:" + name, SceneNode.Kind.OBJECT, x, y, classW, boxHeight(lines.size()), "", title(name)));
                for (int line = 0; line < lines.size(); line++) {
                    double top = y + Style.TITLE_H + line * Style.LINE_H + 2;
                    scene.add(new SceneNode("class:" + name + ".line:" + line, SceneNode.Kind.FIELD, x + 1,
                            top, classW - 1, Style.LINE_H, lines.get(line), null));
                    if (name.equals(runningClass) && lines.get(line).startsWith("+ " + runningMethod + "()")) {
                        // the code that's running now: which class's version of the method it is
                        scene.add(new SceneNode("class:running", SceneNode.Kind.FOCUS_WRITE, x + 1, top, classW - 2, Style.LINE_H, "", null));
                    }
                }
                x += classW + GAP;
            }
            y += rowHeight(row) + LAYER_GAP;
        }
        for (String name : classes) { // as in UML: a solid line up to the parent, a dashed one to an interface
            String parent = facts.superclasses().get(name);
            if (parent != null && classes.contains(parent)) {
                scene.add(new SceneEdge("extends:" + name, "class:" + name, "class:" + parent, SceneEdge.Kind.EXTENDS));
            }
            for (String implemented : facts.interfaces().getOrDefault(name, List.of())) {
                if (classes.contains(implemented)) {
                    scene.add(new SceneEdge("implements:" + name + ":" + implemented, "class:" + name, "class:" + implemented,
                            SceneEdge.Kind.IMPLEMENTS));
                }
            }
        }
        long running = runningOn(now);
        List<ObjectNode> objects = objectsOf(now);
        for (int i = 0; i < objects.size(); i++) { // in columns beside the classes, oldest first
            ObjectNode object = objects.get(i);
            double x = MARGIN + classesW + SIDE_GAP + (i / perColumn) * (objectW + GAP);
            y = TOP + (i % perColumn) * slotH;
            String key = "obj:" + object.getId();
            List<String> lines = objectLines(object, now);
            double height = boxHeight(lines.size());
            scene.add(new SceneNode(key, SceneNode.Kind.OBJECT, x, y, objectW, height, "",
                    "a " + Names.simple(object.getTypeName())));
            for (int line = 0; line < lines.size(); line++) {
                scene.add(new SceneNode(key + ".line:" + line, SceneNode.Kind.FIELD, x + 1, y + Style.TITLE_H + line * Style.LINE_H + 2,
                        objectW - 1, Style.LINE_H, lines.get(line), null));
            }
            // Under the object: the variables that hold it, so "a" in a sentence can be found, and
            // the method running on it.
            List<String> under = new ArrayList<>();
            String holders = holders(object.getId(), now);
            if (!holders.isEmpty()) {
                under.add("in " + holders);
            }
            if (object.getId() == running) {
                scene.add(new SceneNode("obj:running", SceneNode.Kind.FOCUS_WRITE, x - 2, y - 2, objectW + 4, height + 4, "", null));
                under.add("running " + now.getFrames().get(0).getMethodName() + "()");
            }
            if (!under.isEmpty()) {
                scene.add(new SceneNode(key + ".under", SceneNode.Kind.TEXT, x, y + height + 3, objectW, Style.LINE_H,
                        String.join(" · ", under), null));
            }
        }
        return scene;
    }

    // A class's lines in the diagram: "- balance : int", then "+ deposit()".
    private List<String> classLines(String name) {
        List<String> lines = new ArrayList<>();
        for (CodeFacts.FieldFact field : facts.fields()) {
            if (field.className().equals(name)) {
                lines.add((field.isPrivate() ? "- " : "+ ") + field.name() + " : " + field.type() + (field.isStatic() ? "  (static)" : ""));
            }
        }
        Set<String> methods = new LinkedHashSet<>();
        Set<String> own = new HashSet<>();
        for (CodeFacts.MethodFact method : facts.methods()) {
            if (method.className().equals(name) && method.name().equals(name)) { // constructors first, as in UML
                methods.add("+ " + method.name() + "()");
            }
        }
        for (CodeFacts.MethodFact method : facts.methods()) {
            if (method.className().equals(name) && !method.name().equals(name)) {
                methods.add("+ " + method.name() + "()");
                own.add(method.name());
            }
        }
        for (String implemented : facts.interfaces().getOrDefault(name, List.of())) {
            for (CodeFacts.MethodFact method : facts.methods()) { // an interface's default method it doesn't override
                if (method.className().equals(implemented) && !own.contains(method.name()) && facts.isInterface(implemented)) {
                    methods.add("+ " + method.name() + "()  (from " + implemented + ")");
                }
            }
        }
        for (String method : facts.abstractMethods(name)) {
            methods.add("+ " + method + "()" + (facts.isInterface(name) ? "" : "  (abstract)"));
        }
        lines.addAll(methods);
        return lines;
    }

    private boolean onlyMain(String name) {
        if (facts.isInterface(name) || !facts.abstractMethods(name).isEmpty()) {
            return false;
        }
        for (List<String> implemented : facts.interfaces().values()) {
            if (implemented.contains(name)) {
                return false;
            }
        }
        for (CodeFacts.MethodFact method : facts.methods()) {
            if (method.className().equals(name) && !method.name().equals("main") && !method.name().equals(name)) {
                return false; // a method besides main and the constructors
            }
        }
        for (CodeFacts.FieldFact field : facts.fields()) {
            if (field.className().equals(name)) {
                return false;
            }
        }
        return !facts.superclasses().containsKey(name) && !facts.superclasses().containsValue(name);
    }

    // The student's own objects at this step, oldest first.
    private List<ObjectNode> objectsOf(Snapshot step) {
        List<ObjectNode> objects = new ArrayList<>();
        for (HeapObject object : step.getHeap().values()) {
            if (object instanceof ObjectNode && classes.contains(Names.simple(object.getTypeName()))) {
                objects.add((ObjectNode) object);
            }
        }
        objects.sort((a, b) -> Long.compare(a.getId(), b.getId()));
        return objects;
    }

    // An object's lines: "- balance = 50" (private), "name = "Rex"  (from Animal)" (inherited).
    private List<String> objectLines(ObjectNode object, Snapshot step) {
        String type = Names.simple(object.getTypeName());
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, Value> field : object.getFields().entrySet()) {
            String from = declaringClass(type, field.getKey());
            CodeFacts.FieldFact fact = from == null ? null : facts.field(from, field.getKey());
            String mark = fact != null && fact.isPrivate() ? "- " : "+ ";
            lines.add(mark + field.getKey() + text(field.getValue(), step) + (from != null && !from.equals(type) ? "  (from " + from + ")" : ""));
        }
        if (lines.isEmpty()) {
            lines.add("(no fields)");
        }
        return lines;
    }

    // The variables that hold this object at this step, the running call's first: "a", "acct".
    private static String holders(long id, Snapshot step) {
        List<String> names = new ArrayList<>();
        for (FrameSnapshot frame : step.getFrames()) {
            for (Map.Entry<String, Value> local : frame.getLocals().entrySet()) {
                if (!local.getKey().equals("this") && local.getValue() instanceof ReferenceValue
                        && ((ReferenceValue) local.getValue()).id() == id && !names.contains(local.getKey())) {
                    names.add(local.getKey());
                }
            }
        }
        return String.join(", ", names.subList(0, Math.min(3, names.size())));
    }

    // Which class declares a field: the object's own, or the nearest parent that has it.
    private String declaringClass(String type, String field) {
        for (String c = type; c != null; c = facts.superclasses().get(c)) {
            if (facts.field(c, field) != null) {
                return c;
            }
        }
        return null;
    }

    // The object whose method is running now (this of the top frame), or -1.
    private static long runningOn(Snapshot step) {
        if (step.getFrames().isEmpty()) {
            return -1;
        }
        FrameSnapshot top = step.getFrames().get(0);
        Value self = top.getLocals().get("this");
        return self instanceof ReferenceValue ? ((ReferenceValue) self).id() : -1;
    }

    private static String text(Value value, Snapshot step) {
        if (value == null || value instanceof NullValue) {
            return " = null";
        }
        if (value instanceof ReferenceValue) {
            HeapObject object = step.getObject(((ReferenceValue) value).id());
            return " → " + (object == null ? "an object" : "a " + Names.simple(object.getTypeName()));
        }
        return " = " + value.display();
    }
}
