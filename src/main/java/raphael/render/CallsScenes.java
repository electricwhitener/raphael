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
public class CallsScenes {

    private static final double CARD_W = 220;
    private static final double GAP = 10;
    private static final double NOTE_X = CARD_W + 16;
    private static final double NOTE_W = 230;

    private final Trace trace;
    private final List<Moment> moments;
    private final Map<Integer, Scene> built = new HashMap<>();
    private final Dimension size;

    public CallsScenes(Trace trace, List<Moment> moments) {
        this.trace = trace;
        this.moments = moments;
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
    public Dimension getRunSize() {
        return size;
    }

    public Scene sceneAt(int step) {
        return built.computeIfAbsent(step, this::build);
    }

    private Scene build(int step) {
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
