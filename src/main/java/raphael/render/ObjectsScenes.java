package raphael.render;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.HashMap;
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

// The objects view, for object-oriented programs. On top, the program's classes as a small UML
// class diagram: each with its fields and methods, "-" for private and "+" for the rest, and an
// arrow from each child class to its parent. Below, the objects alive at this step, each with its
// fields: private ones marked "-", and inherited ones saying which class they come from. The
// object whose method is running is outlined in blue, with the method beside it.
// OOP: a class that builds Scene objects (drawn and animated like the picture's), from the run
// and from what the code says (CodeFacts).
public class ObjectsScenes {

    private static final double CLASS_W = 190;
    private static final double OBJECT_W = 230;
    private static final double GAP = 24;
    private static final double ROW_GAP = 56;

    private final Trace trace;
    private final CodeFacts facts;
    private final List<String> classes = new ArrayList<>();
    private final Map<Integer, Scene> built = new HashMap<>();
    private final Dimension size;
    private final double classRowH;

    public ObjectsScenes(Trace trace, CodeFacts facts) {
        this.trace = trace;
        this.facts = facts;
        Set<String> names = new LinkedHashSet<>();
        for (CodeFacts.MethodFact method : facts.methods()) {
            if (!method.name().equals("main")) {
                names.add(method.className());
            }
        }
        for (CodeFacts.FieldFact field : facts.fields()) {
            names.add(field.className());
        }
        names.addAll(facts.superclasses().keySet());
        names.removeIf(this::onlyMain);
        classes.addAll(names);
        double tallest = 0;
        for (String name : classes) {
            tallest = Math.max(tallest, classLines(name).size());
        }
        classRowH = Style.TITLE_H + Math.max(1, tallest) * Style.LINE_H + 4;
        int widest = 0;
        double tallestObject = 0;
        for (Snapshot step : trace.getSnapshots()) {
            List<ObjectNode> objects = objectsOf(step);
            widest = Math.max(widest, objects.size());
            for (ObjectNode object : objects) {
                tallestObject = Math.max(tallestObject, object.getFields().size());
            }
        }
        double width = Math.max(classes.size() * (CLASS_W + GAP), widest * (OBJECT_W + GAP));
        double height = Style.LABEL_SPACE + classRowH + ROW_GAP + Style.TITLE_H + Math.max(1, tallestObject) * Style.LINE_H + 40;
        size = new Dimension((int) Math.max(400, width), (int) height);
    }

    // Whether the view has anything to show: the program has classes of its own besides main's.
    public boolean hasClasses() {
        return !classes.isEmpty();
    }

    public Dimension getRunSize() {
        return size;
    }

    public Scene sceneAt(int step) {
        return built.computeIfAbsent(step, this::build);
    }

    private Scene build(int step) {
        Scene scene = new Scene();
        double x = 0;
        double top = Style.LABEL_SPACE;
        for (String name : classes) {
            List<String> lines = classLines(name);
            double height = Style.TITLE_H + Math.max(1, lines.size()) * Style.LINE_H + 4;
            String title = (facts.isInterface(name) ? "«interface» " : facts.isAbstract(name) ? "«abstract» " : "") + name;
            scene.add(new SceneNode("class:" + name, SceneNode.Kind.OBJECT, x, top, CLASS_W, height, "", title));
            for (int row = 0; row < lines.size(); row++) {
                scene.add(new SceneNode("class:" + name + ".line:" + row, SceneNode.Kind.FIELD, x + 1,
                        top + Style.TITLE_H + row * Style.LINE_H + 2, CLASS_W - 1, Style.LINE_H, lines.get(row), null));
            }
            x += CLASS_W + GAP;
        }
        for (String name : classes) { // a child class points at its parent, as in UML
            String parent = facts.superclasses().get(name);
            if (parent != null && classes.contains(parent)) {
                scene.add(new SceneEdge("extends:" + name, "class:" + name, "class:" + parent, SceneEdge.Kind.ARROW));
            }
        }
        Snapshot now = trace.get(step);
        long running = runningOn(now);
        double y = top + classRowH + ROW_GAP;
        x = 0;
        for (ObjectNode object : objectsOf(now)) {
            String key = "obj:" + object.getId();
            List<String> lines = objectLines(object, now);
            double height = Style.TITLE_H + Math.max(1, lines.size()) * Style.LINE_H + 4;
            scene.add(new SceneNode(key, SceneNode.Kind.OBJECT, x, y, OBJECT_W, height, "",
                    "a " + Names.simple(object.getTypeName())));
            for (int row = 0; row < lines.size(); row++) {
                scene.add(new SceneNode(key + ".line:" + row, SceneNode.Kind.FIELD, x + 1, y + Style.TITLE_H + row * Style.LINE_H + 2,
                        OBJECT_W - 1, Style.LINE_H, lines.get(row), null));
            }
            if (object.getId() == running) {
                scene.add(new SceneNode("obj:running", SceneNode.Kind.FOCUS_WRITE, x - 2, y - 2, OBJECT_W + 4, height + 4, "", null));
                scene.add(new SceneNode("obj:method", SceneNode.Kind.TEXT, x, y + height + 6, OBJECT_W, Style.LINE_H,
                        "running " + now.getFrames().get(0).getMethodName() + "()", null));
            }
            x += OBJECT_W + GAP;
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
        for (CodeFacts.MethodFact method : facts.methods()) {
            if (method.className().equals(name) && !method.name().equals(name)) { // constructors left out
                methods.add("+ " + method.name() + "()");
            }
        }
        lines.addAll(methods);
        return lines;
    }

    private boolean onlyMain(String name) {
        for (CodeFacts.MethodFact method : facts.methods()) {
            if (method.className().equals(name) && !method.name().equals("main")) {
                return false;
            }
        }
        for (CodeFacts.FieldFact field : facts.fields()) {
            if (field.className().equals(name)) {
                return false;
            }
        }
        return !facts.superclasses().containsKey(name);
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
        return lines;
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
