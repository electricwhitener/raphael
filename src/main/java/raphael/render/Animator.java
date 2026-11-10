package raphael.render;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.Timer;

// Moves the picture smoothly from one step to the next. Every shape on screen is "live": it has
// a position and a speed, and springs (see Spring) pull it toward where the new step wants it.
// A spring keeps its speed when its target changes, so stepping quickly or playing fast flows
// instead of stopping and restarting. On top of that:
//   - new shapes fade in and grow slightly; leaving shapes fade out (faster) and shrink slightly,
//   - a changed value flashes amber, rolls the old value out and the new one in, then keeps a soft glow,
//   - an arrow whose target changed swings over to the new target; a pointer set to null pulls
//     back into its slot, and a new pointer grows out of its slot,
//   - a value copied between cells (arr[j] = arr[j + 1]) flies across to its new cell.
// A Swing Timer ticks about every 10 ms while anything is still moving, then stops.
// OOP: encapsulation (all the live state is private); private nested classes for live shapes,
// live arrows and flying values.
public class Animator {

    private enum Entrance { NONE, GROW, FADE }

    private final Timer timer;
    private final Runnable onFrame;
    private final Map<String, LiveNode> nodes = new LinkedHashMap<>();
    private final Map<String, LiveEdge> edges = new LinkedHashMap<>();
    private final List<Flyer> flyers = new ArrayList<>();
    private Scene target;
    private Scene current;
    private double omega = omegaFor(1);
    private long lastTick;
    private boolean showChanges = true; // false during a jump

    public Animator(Runnable onFrame) {
        this(onFrame, true);
    }

    // Tests pass useTimer = false and call advance() themselves.
    Animator(Runnable onFrame, boolean useTimer) {
        this.onFrame = onFrame;
        this.timer = useTimer ? new Timer(Style.FRAME_MS, e -> tick()) : null;
    }

    // Jumps straight to a scene, without animating (e.g. when a new run is loaded).
    public void show(Scene scene) {
        if (timer != null) {
            timer.stop();
        }
        nodes.clear();
        edges.clear();
        flyers.clear();
        target = scene;
        if (scene != null) {
            for (SceneNode node : scene.getNodes()) {
                nodes.put(node.key(), new LiveNode(node, false));
            }
            for (SceneEdge edge : scene.getEdges()) {
                edges.put(edge.key(), new LiveEdge(edge, Entrance.NONE));
            }
        }
        rebuild();
        onFrame.run();
    }

    // Sends every shape toward its place in the new scene, starting from wherever it is now.
    // speed is the playing speed (2 = twice as fast). nextStep is true when the new scene is the
    // step right after the old one, so values the line copied between cells can fly across.
    public void animateTo(Scene scene, double speed, boolean nextStep) {
        if (target == null || scene == null) {
            show(scene);
            return;
        }
        Scene previous = target;
        target = scene;
        omega = omegaFor(speed);
        Diff diff = Diff.between(previous, scene);

        // Shapes that are leaving go first, so they're drawn underneath the others.
        Map<String, LiveNode> ordered = new LinkedHashMap<>();
        for (LiveNode live : nodes.values()) {
            if (scene.getNode(live.key()) == null) {
                live.leave();
                ordered.put(live.key(), live);
            }
        }
        for (SceneNode node : scene.getNodes()) {
            LiveNode live = nodes.get(node.key());
            if (live == null) {
                live = new LiveNode(node, true);
            } else {
                live.retarget(node, showChanges && diff.getChanged().contains(node.key()));
            }
            ordered.put(node.key(), live);
        }
        nodes.clear();
        nodes.putAll(ordered);

        for (LiveEdge live : edges.values()) {
            if (scene.getEdge(live.key()) == null) {
                live.leave(scene.getNode(live.target.from()) != null);
            }
        }
        for (SceneEdge edge : scene.getEdges()) {
            LiveEdge live = edges.get(edge.key());
            if (live == null) {
                // A new pointer from a slot that was already there grows out of it; otherwise it fades in.
                Entrance entrance = previous.getNode(edge.from()) != null ? Entrance.GROW : Entrance.FADE;
                edges.put(edge.key(), new LiveEdge(edge, entrance));
            } else {
                live.retarget(edge);
            }
        }

        if (nextStep) {
            for (Scene.Move move : scene.getMoves()) {
                SceneNode from = previous.getNode(move.fromKey());
                LiveNode to = nodes.get(move.toKey());
                if (from != null && to != null) {
                    Flyer flyer = new Flyer(to.target.text(), from, move.toKey());
                    flyers.add(flyer);
                    to.waitFor(flyer);
                }
            }
        }
        rebuild();
        if (timer != null && !timer.isRunning()) {
            lastTick = System.nanoTime();
            timer.start();
        }
        onFrame.run();
    }

    // Moves to a scene many steps away (the slider, Home, End). Shapes still glide there, but nothing
    // flashes: amber means "one step just changed this", and that isn't true of a jump.
    public void jumpTo(Scene scene, double speed) {
        showChanges = false;
        animateTo(scene, speed, false);
        showChanges = true;
    }

    // What to draw right now.
    public Scene getCurrent() {
        return current;
    }

    // The scene being moved to (the step that is really showing).
    public Scene getTarget() {
        return target;
    }

    // Moves everything forward by dt seconds. The timer calls this; so do the tests.
    void advance(double dt) {
        for (LiveNode node : nodes.values()) {
            node.advance(dt, omega);
        }
        for (LiveEdge edge : edges.values()) {
            edge.advance(dt, omega);
        }
        for (Flyer flyer : flyers) {
            flyer.advance(dt, omega);
        }
        flyers.removeIf(Flyer::landed);
        edges.values().removeIf(LiveEdge::gone);
        nodes.values().removeIf(node -> node.gone() && !usedByAnEdge(node.key()));
        rebuild();
    }

    boolean isSettled() {
        if (!flyers.isEmpty()) {
            return false;
        }
        for (LiveNode node : nodes.values()) {
            if (!node.settled()) {
                return false;
            }
        }
        for (LiveEdge edge : edges.values()) {
            if (!edge.settled()) {
                return false;
            }
        }
        return true;
    }

    private void tick() {
        long now = System.nanoTime();
        double dt = Math.min((now - lastTick) / 1e9, 0.05); // after a hiccup, don't jump too far
        lastTick = now;
        advance(dt);
        if (isSettled()) {
            timer.stop();
        }
        onFrame.run();
    }

    // Builds the scene for this moment from all the live shapes, arrows and flying values.
    private void rebuild() {
        Scene frame = new Scene();
        for (LiveNode node : nodes.values()) {
            frame.add(node.frame());
        }
        for (LiveEdge edge : edges.values()) {
            frame.add(edge.frame());
        }
        for (Flyer flyer : flyers) {
            LiveNode to = nodes.get(flyer.toKey);
            if (to != null) {
                frame.add(flyer.frame(to));
            }
        }
        current = frame;
    }

    // A leaving shape stays (invisibly) while an arrow still needs its position.
    private boolean usedByAnEdge(String key) {
        for (LiveEdge edge : edges.values()) {
            if (key.equals(edge.target.from()) || key.equals(edge.target.to()) || key.equals(edge.oldTo)) {
                return true;
            }
        }
        return false;
    }

    // How stiff the springs are: about SPRING_RESPONSE seconds per move at 1×, quicker when playing fast.
    private static double omegaFor(double speed) {
        double response = Math.max(Style.SPRING_RESPONSE / Math.sqrt(speed), 0.12);
        return 2 * Math.PI / response;
    }

    // Moves a value a little closer to its goal; time constant tau, in seconds.
    private static double approach(double value, double goal, double dt, double tau) {
        double next = goal + (value - goal) * Math.exp(-dt / tau);
        return Math.abs(next - goal) < 0.002 ? goal : next;
    }

    // One shape on screen: where it is, where it's going, and how it looks on the way.
    private static final class LiveNode {

        private SceneNode target;
        private final Spring x;
        private final Spring y;
        private final Spring w;
        private final Spring h;
        private double alpha;
        private boolean leaving;
        private double flash;      // changed-value highlight
        private double glow;       // what the highlight settles at
        private String oldText;    // the value before it changed, rolling out
        private double roll = 1;   // 0 → 1 while the new value rolls in
        private Flyer waitingFor;  // a copied value on its way here

        LiveNode(SceneNode node, boolean appearing) {
            target = node;
            x = new Spring(node.x());
            y = new Spring(node.y());
            w = new Spring(node.w());
            h = new Spring(node.h());
            alpha = appearing ? 0 : restingAlpha();
        }

        String key() {
            return target.key();
        }

        // 1 for most shapes, less for a dimmed one (like a marker just past the end of an array).
        double restingAlpha() {
            return target.look().alpha();
        }

        void retarget(SceneNode node, boolean changed) {
            if (changed) {
                boolean hasValue = node.kind() == SceneNode.Kind.CELL || node.kind() == SceneNode.Kind.BOX
                        || node.kind() == SceneNode.Kind.CIRCLE || node.kind() == SceneNode.Kind.RED_NODE
                        || node.kind() == SceneNode.Kind.BLACK_NODE;
                if (hasValue && !node.text().equals(target.text())) {
                    oldText = target.text();
                    roll = 0;
                }
                flash = 1;
                glow = Style.CHANGED_GLOW;
            } else {
                glow = 0;
            }
            leaving = false;
            target = node;
            x.setTarget(node.x());
            y.setTarget(node.y());
            w.setTarget(node.w());
            h.setTarget(node.h());
        }

        void leave() {
            leaving = true;
        }

        // Hold the new value (and its flash) back until the flying copy is nearly here.
        void waitFor(Flyer flyer) {
            waitingFor = flyer;
            flash = 0;
        }

        void advance(double dt, double omega) {
            x.step(dt, omega);
            y.step(dt, omega);
            w.step(dt, omega);
            h.step(dt, omega);
            alpha = approach(alpha, leaving ? 0 : restingAlpha(), dt, leaving ? Style.FADE_OUT : Style.FADE_IN);
            flash = waitingFor != null ? 0 : approach(flash, glow, dt, Style.FLASH_DECAY);
            if (waitingFor != null && waitingFor.hasArrived()) {
                waitingFor = null;
                flash = 1; // the value lands: now it flashes
            }
            if (waitingFor == null) {
                roll = approach(roll, 1, dt, Style.ROLL);
            }
            if (roll >= 1) {
                oldText = null;
            }
        }

        boolean gone() {
            return leaving && alpha <= 0.002;
        }

        boolean settled() {
            return x.isSettled() && y.isSettled() && w.isSettled() && h.isSettled() && !leaving
                    && alpha == restingAlpha() && flash == glow && oldText == null && waitingFor == null;
        }

        SceneNode frame() {
            double shown = Math.min(1, alpha / Math.max(restingAlpha(), 0.01)); // 0 → 1 while appearing
            double from = leaving ? Style.EXIT_SCALE : Style.ENTER_SCALE;
            double scale = from + (1 - from) * shown;
            return new SceneNode(target.key(), target.kind(), x.get(), y.get(), w.get(), h.get(),
                    target.text(), target.caption(), new SceneNode.Look(alpha, flash, scale, oldText, roll));
        }
    }

    // One arrow on screen. If its target changes, it swings from the old target to the new one.
    private static final class LiveEdge {

        private SceneEdge target;
        private String oldTo;
        private final Spring swing = new Spring(1); // 0 → 1 while swinging to a new target
        private final Spring reach = new Spring(1); // 1 = all the way; 0 = pulled back into its slot
        private double alpha;
        private boolean leaving;
        private boolean retracting;

        LiveEdge(SceneEdge edge, Entrance entrance) {
            target = edge;
            alpha = entrance == Entrance.FADE ? 0 : 1;
            if (entrance == Entrance.GROW) {
                reach.jumpTo(0);
                reach.setTarget(1);
            }
        }

        String key() {
            return target.key();
        }

        void retarget(SceneEdge edge) {
            if (!edge.to().equals(target.to())) {
                // Swing from wherever the arrow mostly points now.
                oldTo = oldTo != null && swing.get() < 0.5 ? oldTo : target.to();
                swing.jumpTo(0);
                swing.setTarget(1);
            }
            leaving = false;
            retracting = false;
            reach.setTarget(1);
            target = edge;
        }

        // If its slot is still there (the pointer became null), it pulls back; otherwise it fades with its slot.
        void leave(boolean slotStays) {
            leaving = true;
            if (slotStays) {
                retracting = true;
                reach.setTarget(0);
            }
        }

        void advance(double dt, double omega) {
            swing.step(dt, omega);
            reach.step(dt, omega * 1.4); // pulling back is a little quicker than moving
            if (swing.isSettled()) {
                oldTo = null;
            }
            alpha = approach(alpha, leaving && !retracting ? 0 : 1, dt, leaving ? Style.FADE_OUT : Style.FADE_IN);
        }

        boolean gone() {
            return leaving && (retracting ? reach.get() < 0.02 : alpha <= 0.002);
        }

        boolean settled() {
            return !leaving && swing.isSettled() && reach.isSettled() && alpha == 1;
        }

        SceneEdge frame() {
            return new SceneEdge(target.key(), target.from(), target.to(), target.kind(),
                    new SceneEdge.Look(alpha, oldTo, swing.get(), reach.get()));
        }
    }

    // A copied value flying from the cell it came from to its new cell, in a small arc.
    private static final class Flyer {

        private static int count;

        private final String key = "fly:" + count++;
        private final String text;
        private final double startX;
        private final double startY;
        private final double w;
        private final double h;
        private final String toKey;
        private final Spring progress = new Spring(0);

        Flyer(String text, SceneNode from, String toKey) {
            this.text = text;
            this.startX = from.centerX();
            this.startY = from.centerY();
            this.w = from.w();
            this.h = from.h();
            this.toKey = toKey;
            progress.setTarget(1);
        }

        void advance(double dt, double omega) {
            progress.step(dt, omega * 0.9);
        }

        // Far enough along that the cell can start showing the new value.
        boolean hasArrived() {
            return progress.get() > 0.6;
        }

        boolean landed() {
            return progress.get() > 0.995;
        }

        SceneNode frame(LiveNode to) {
            double p = progress.get();
            double endX = to.x.get() + to.w.get() / 2;
            double endY = to.y.get() + to.h.get() / 2;
            double cx = startX + (endX - startX) * p;
            double cy = startY + (endY - startY) * p - Math.sin(Math.PI * p) * h * 0.6; // lifted over the other cells
            double alpha = p < 0.8 ? 1 : Math.max(0, (1 - p) / 0.2);
            return new SceneNode(key, SceneNode.Kind.FLYER, cx - w / 2, cy - h / 2, w, h, text, null,
                    new SceneNode.Look(alpha, 0, 1, null, 1));
        }
    }
}
