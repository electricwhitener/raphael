package raphael.render;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import raphael.explain.Moment;
import raphael.model.Exit;
import raphael.model.FrameSnapshot;
import raphael.model.HeapObject;
import raphael.model.Names;
import raphael.model.NullValue;
import raphael.model.ObjectNode;
import raphael.model.ReferenceValue;
import raphael.model.Snapshot;
import raphael.model.Trace;
import raphael.model.Value;

// The calls view: the call stack as a column of boxes, the running call on top, each titled with
// its method (and the object it runs on) and listing its variables, arguments first. Beside the top
// box, a note: what it's waiting for, what just came back to it ("← factorial(1) gave 1"), or that
// it's a base case. Each depth keeps the same key, so a box keeps its place from step to step,
// and the picture's Animator moves and flashes it like any other shape.
// OOP: a class that builds Scene objects (the same pictures ScenePainter draws), each step's
// built once, when asked.
public class CallsScenes implements SceneSource {

    private static final double CARD_W = 220;
    private static final double GAP = 10;
    private static final double NOTE_X = CARD_W + 16;
    private static final double NOTE_W = 230;

    private final Trace trace;
    private final List<Moment> moments;
    private final Map<Integer, Scene> built = new HashMap<>();
    private final Dimension size;
    // Branching recursion (fib, Hanoi) is drawn as a tree of calls instead of a stack.
    private final CallTree tree;
    private final List<CallTree.Call> shown = new ArrayList<>(); // the tree's calls, laid out once for the run
    private final Map<CallTree.Call, double[]> places = new HashMap<>();
    private final Map<CallTree.Call, Integer> hiddenInside = new HashMap<>();

    private static final SceneNode.Look RETURNED = new SceneNode.Look(0.62, 0, 1, null, 1); // faded, but its value still easy to read
    private static final int MAX_CALLS = 63;    // more than this, and the deepest calls fold into their parent
    private static final double TREE_GAP_X = 12;
    private static final double TREE_GAP_Y = 30;

    public CallsScenes(Trace trace, List<Moment> moments) {
        this.trace = trace;
        this.moments = moments;
        CallTree calls = new CallTree(trace);
        List<String> branching = calls.isComplete() ? calls.branchingMethods() : List.of();
        tree = branching.isEmpty() ? null : calls;
        if (tree != null) {
            size = layOutTree(branching);
            return;
        }
        int deepest = 1;
        int widest = 0;
        for (Snapshot step : trace.getSnapshots()) {
            deepest = Math.max(deepest, step.getFrames().size());
            for (FrameSnapshot frame : step.getFrames()) {
                widest = Math.max(widest, lines(frame, step).size());
            }
        }
        double cardH = Style.TITLE_H + Math.max(1, widest) * Style.LINE_H + 4;
        size = new Dimension((int) (NOTE_X + NOTE_W), (int) (deepest * (cardH + GAP) + Style.LABEL_SPACE));
    }

    // The room the run's deepest stack needs, for one scale for the whole run.
    @Override
    public Dimension getRunSize() {
        return size;
    }

    @Override
    public Scene sceneAt(int step) {
        return built.computeIfAbsent(step, this::build);
    }

    // Whether this run is drawn as a tree of calls (branching recursion) rather than a stack.
    public boolean isTree() {
        return tree != null;
    }

    private Scene build(int step) {
        if (tree != null) {
            return buildTree(step);
        }
        Scene scene = new Scene();
        Snapshot now = trace.get(step);
        List<FrameSnapshot> frames = now.getFrames(); // the running call first
        double y = Style.LABEL_SPACE;
        for (int i = 0; i < frames.size(); i++) {
            FrameSnapshot frame = frames.get(i);
            int depth = frames.size() - 1 - i; // main is 0, and keeps its key while calls come and go above it
            String key = "call:" + depth;
            List<String> lines = lines(frame, now);
            double height = Style.TITLE_H + Math.max(1, lines.size()) * Style.LINE_H + 4;
            scene.add(new SceneNode(key, SceneNode.Kind.OBJECT, 0, y, CARD_W, height, "", title(frame, now)
                    + (frame.getFile() == null ? "" : "  · " + frame.getFile().substring(frame.getFile().lastIndexOf('/') + 1))
                    + "  :" + frame.getLine()));
            int row = 0;
            for (String line : lines) {
                scene.add(new SceneNode(key + ".line:" + row, SceneNode.Kind.FIELD, 1, y + Style.TITLE_H + row * Style.LINE_H + 2,
                        CARD_W - 1, Style.LINE_H, line, null));
                row++;
            }
            if (i == 0) { // the running call: outlined in blue, the colour of "about to happen"
                scene.add(new SceneNode("call:running", SceneNode.Kind.FOCUS_WRITE, -2, y - 2, CARD_W + 4, height + 4, "", null));
                String note = noteFor(step, now, frames);
                if (note != null) {
                    scene.add(new SceneNode("call:note", SceneNode.Kind.TEXT, NOTE_X, y + 4, NOTE_W, Style.LINE_H, note, null));
                }
            } else if (i == 1) {
                scene.add(new SceneNode("call:waits", SceneNode.Kind.TEXT, NOTE_X, y + 4, NOTE_W, Style.LINE_H,
                        "waits for " + frames.get(0).getMethodName() + "() to finish", null));
            }
            y += height + GAP;
        }
        return scene;
    }

    // Beside the running call: a base case, or what just came back to it, or nothing.
    private String noteFor(int step, Snapshot now, List<FrameSnapshot> frames) {
        for (Moment moment : moments) {
            if (moment.step() == step && moment.kind().equals("base-case")) {
                return "base case: it stops here, without calling itself";
            }
        }
        List<Exit> exits = now.getExits();
        if (!exits.isEmpty()) {
            Exit last = exits.get(exits.size() - 1);
            if (last.returned() != null) {
                return "← " + last.method() + "() gave back " + text(last.returned(), now);
            }
        }
        return null;
    }

    // ---- the tree of calls ----

    // Places every call of the branching methods once, for the whole run, so a box never moves:
    // each leaf gets the next slot along, and a parent sits centred over its children.
    private Dimension layOutTree(List<String> branching) {
        List<CallTree.Call> roots = new ArrayList<>();
        collect(tree.getRoots(), branching, roots);
        int depth = 1;
        while (count(roots, depth + 1) <= MAX_CALLS && count(roots, depth + 1) > count(roots, depth)) {
            depth++;
        }
        boolean folds = count(roots, depth) < count(roots, Integer.MAX_VALUE / 2);
        double boxW = Style.textWidth(folds ? "waits  +99 inside" : "base case", Style.FIELD_FONT) + 16;
        for (CallTree.Call root : roots) {
            boxW = Math.max(boxW, widest(root, depth));
        }
        double[] next = {0};
        double boxH = Style.TITLE_H + Style.LINE_H + 4;
        for (CallTree.Call root : roots) {
            place(root, 0, depth, boxW, boxH, next);
        }
        int deepest = 0;
        for (double[] place : places.values()) {
            deepest = (int) Math.max(deepest, place[1]);
        }
        return new Dimension((int) (next[0] - TREE_GAP_X),
                (int) (Style.LABEL_SPACE + (deepest + 1) * (boxH + TREE_GAP_Y)));
    }

    // The calls of the branching methods, each under the nearest such call that made it.
    private final Map<CallTree.Call, List<CallTree.Call>> kids = new HashMap<>();

    private void collect(List<CallTree.Call> calls, List<String> branching, List<CallTree.Call> into) {
        for (CallTree.Call call : calls) {
            if (branching.contains(call.getMethod())) {
                into.add(call);
                List<CallTree.Call> mine = new ArrayList<>();
                kids.put(call, mine);
                collect(call.getChildren(), branching, mine);
            } else {
                collect(call.getChildren(), branching, into);
            }
        }
    }

    private int count(List<CallTree.Call> calls, int depth) {
        int n = 0;
        for (CallTree.Call call : calls) {
            n += 1 + (depth > 1 ? count(kids.get(call), depth - 1) : 0);
        }
        return n;
    }

    private double widest(CallTree.Call call, int depth) {
        double w = Style.textWidth(label(call), Style.TITLE_FONT) + 16;
        if (call.getReturned() != null) {
            w = Math.max(w, Style.textWidth("= " + text(call.getReturned(), call.getStartedIn()), Style.FIELD_FONT) + 16);
        }
        if (depth > 1) {
            for (CallTree.Call child : kids.get(call)) {
                w = Math.max(w, widest(child, depth - 1));
            }
        }
        return w;
    }

    private void place(CallTree.Call call, int level, int depth, double boxW, double boxH, double[] next) {
        shown.add(call);
        List<CallTree.Call> children = level + 1 < depth ? kids.get(call) : List.of();
        if (level + 1 >= depth && !kids.get(call).isEmpty()) {
            hiddenInside.put(call, count(kids.get(call), Integer.MAX_VALUE / 2));
        }
        double x;
        if (children.isEmpty()) {
            x = next[0];
            next[0] += boxW + TREE_GAP_X;
        } else {
            for (CallTree.Call child : children) {
                place(child, level + 1, depth, boxW, boxH, next);
            }
            x = (places.get(children.get(0))[0] + places.get(children.get(children.size() - 1))[0]) / 2;
        }
        places.put(call, new double[] {x, level, boxW, boxH});
    }

    // At this step: the calls made so far. The running one is outlined in blue, the ones waiting
    // for it say so, and the ones that returned are faded with what they gave back.
    private Scene buildTree(int step) {
        Scene scene = new Scene();
        Snapshot now = trace.get(step);
        CallTree.Call running = tree.runningAt(step);
        while (running != null && !places.containsKey(running)) {
            running = running.getParent(); // a helper, or a folded call: light the nearest shown one
        }
        boolean baseCase = false;
        for (Moment moment : moments) {
            baseCase |= moment.step() == step && moment.kind().equals("base-case");
        }
        for (CallTree.Call call : shown) {
            if (call.getStart() > step) {
                continue; // not called yet
            }
            double[] place = places.get(call);
            double x = place[0];
            double y = Style.LABEL_SPACE + place[1] * (place[3] + TREE_GAP_Y);
            String key = "tree:" + call.getStart();
            boolean done = !call.isActiveAt(step);
            String line;
            if (done) {
                line = call.getReturned() == null ? "done" : "= " + text(call.getReturned(), now);
            } else if (call == running) {
                line = baseCase ? "base case" : "running";
            } else {
                line = "waits";
            }
            Integer folded = hiddenInside.get(call);
            if (folded != null && !done) {
                line += "  +" + folded + " inside";
            }
            SceneNode.Look look = done ? RETURNED : SceneNode.Look.STILL;
            scene.add(new SceneNode(key, SceneNode.Kind.OBJECT, x, y, place[2], place[3], "", label(call), look));
            scene.add(new SceneNode(key + ".line:0", SceneNode.Kind.FIELD, x + 1, y + Style.TITLE_H + 2, place[2] - 1, Style.LINE_H,
                    line, null, look));
            if (call == running) {
                scene.add(new SceneNode("call:running", SceneNode.Kind.FOCUS_WRITE, x - 2, y - 2, place[2] + 4, place[3] + 4, "", null));
            }
            CallTree.Call parent = call.getParent();
            while (parent != null && !places.containsKey(parent)) {
                parent = parent.getParent();
            }
            if (parent != null) {
                scene.add(new SceneEdge("tree:" + parent.getStart() + ">" + call.getStart(), "tree:" + parent.getStart(), key,
                        SceneEdge.Kind.TREE));
            }
        }
        return scene;
    }

    // "fib(3)", or "move(2, 0, 1, 2)": the method and the values it was called with.
    private static String label(CallTree.Call call) {
        StringBuilder text = new StringBuilder(call.getMethod()).append('(');
        boolean first = true;
        for (Map.Entry<String, Value> argument : call.getArguments().entrySet()) {
            if (!argument.getKey().equals("this")) {
                text.append(first ? "" : ", ").append(text(argument.getValue(), call.getStartedIn()));
                first = false;
            }
        }
        return text.append(')').toString();
    }

    // "factorial()", or "Circle.area()" for a method running on an object, then the line.
    private static String title(FrameSnapshot frame, Snapshot step) {
        HeapObject self = self(frame, step);
        String on = self == null ? "" : Names.simple(self.getTypeName()) + ".";
        return on + frame.getMethodName() + "()";
    }

    // The call's variables, one a line: "n = 3", "result = 6", "head → a Node (val 4)".
    private static List<String> lines(FrameSnapshot frame, Snapshot step) {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, Value> local : frame.getLocals().entrySet()) {
            if (!local.getKey().equals("this")) {
                Value value = local.getValue();
                lines.add(local.getKey() + (value instanceof ReferenceValue ? " → " : " = ") + text(value, step));
            }
        }
        return lines;
    }

    private static String text(Value value, Snapshot step) {
        if (value == null || value instanceof NullValue) {
            return "null";
        }
        if (value instanceof ReferenceValue) {
            HeapObject object = step.getObject(((ReferenceValue) value).id());
            if (object instanceof ObjectNode) {
                for (Map.Entry<String, Value> field : ((ObjectNode) object).getFields().entrySet()) {
                    if (!(field.getValue() instanceof ReferenceValue) && !(field.getValue() instanceof NullValue)) {
                        return Names.simple(object.getTypeName()) + " (" + field.getKey() + " " + field.getValue().display() + ")";
                    }
                }
            }
            return object == null ? "an object" : object.shortName();
        }
        return value.display();
    }

    private static HeapObject self(FrameSnapshot frame, Snapshot step) {
        Value self = frame.getLocals().get("this");
        return self instanceof ReferenceValue ? step.getObject(((ReferenceValue) self).id()) : null;
    }
}
