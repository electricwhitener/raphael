package raphael.ui;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import raphael.model.Snapshot;
import raphael.model.Trace;
import raphael.render.Scene;

// The one place that knows which run is loaded, which step is showing, and how fast to play.
// Panels register as listeners and are told whenever the step changes (the Observer pattern),
// so no panel needs to know about any other panel.
// OOP: encapsulation. The fields are private, and setStep() keeps the step inside the run,
// so a panel can never see an impossible step.
public class TraceModel {

    private Trace trace;        // null when nothing has been run
    private List<Scene> scenes; // one picture per step
    private Dimension runSize = new Dimension(); // the room the run's biggest picture needs
    private int step;
    private double speed = 1;   // 1 = normal, 2 = twice as fast
    private final List<TraceListener> listeners = new ArrayList<>();

    public void addListener(TraceListener listener) {
        listeners.add(listener);
    }

    public void load(Trace trace, List<Scene> scenes) {
        Dimension biggest = new Dimension();
        for (Scene scene : scenes) {
            biggest.width = Math.max(biggest.width, scene.getSize().width);
            biggest.height = Math.max(biggest.height, scene.getSize().height);
        }
        load(trace, scenes, biggest);
    }

    // For pictures that are built when shown (SceneList), which already know the biggest size.
    public void load(Trace trace, List<Scene> scenes, Dimension runSize) {
        this.trace = trace;
        this.scenes = scenes;
        this.step = 0;
        this.runSize = new Dimension(runSize);
        for (TraceListener listener : listeners) {
            listener.traceChanged(this);
        }
    }

    public void clear() {
        if (trace == null) {
            return;
        }
        trace = null;
        scenes = null;
        runSize = new Dimension();
        step = 0;
        for (TraceListener listener : listeners) {
            listener.traceChanged(this);
        }
    }

    // True when there is a run with at least one step to show.
    public boolean hasSteps() {
        return trace != null && trace.size() > 0;
    }

    public Trace getTrace() {
        return trace;
    }

    public int getStep() {
        return step;
    }

    public int size() {
        return trace == null ? 0 : trace.size();
    }

    public Snapshot getSnapshot() {
        return hasSteps() ? trace.get(step) : null;
    }

    public Scene getScene() {
        return hasSteps() ? scenes.get(step) : null;
    }

    public Dimension getRunSize() {
        return new Dimension(runSize); // a copy, so nobody can change ours
    }

    public boolean isLastStep() {
        return hasSteps() && step == trace.size() - 1;
    }

    // Moves to another step. A step outside the run is clamped to the first or last step.
    public void setStep(int newStep) {
        if (!hasSteps()) {
            return;
        }
        int clamped = Math.max(0, Math.min(newStep, trace.size() - 1));
        if (clamped == step) {
            return;
        }
        step = clamped;
        for (TraceListener listener : listeners) {
            listener.stepChanged(this);
        }
    }

    // The step before this one, or null at the first step. The picture shows the state after
    // that step's line ran.
    public Snapshot getPreviousSnapshot() {
        return hasSteps() && step > 0 ? trace.get(step - 1) : null;
    }

    public void next() {
        setStep(step + 1);
    }

    // Steps forward without going inside the methods this line calls ("step over"): the next
    // step that is back in this method, or in one of its callers.
    public void stepOver() {
        moveToNext(depth(step));
    }

    // Steps forward until the running method has returned ("step out").
    public void stepOut() {
        moveToNext(depth(step) - 1);
    }

    // Jumps to the next step that runs this line, wrapping round to the start.
    // Returns false if the line never runs.
    public boolean jumpToLine(int line) {
        for (int k = 1; k <= size(); k++) {
            int candidate = (step + k) % size();
            if (trace.get(candidate).getLine() == line) {
                setStep(candidate);
                return true;
            }
        }
        return false;
    }

    // Moves to the next step whose call stack is no deeper than maxDepth (or to the end).
    private void moveToNext(int maxDepth) {
        if (!hasSteps()) {
            return;
        }
        for (int candidate = step + 1; candidate < size(); candidate++) {
            if (depth(candidate) <= maxDepth) {
                setStep(candidate);
                return;
            }
        }
        last();
    }

    private int depth(int s) {
        return trace.get(s).getFrames().size();
    }

    public void previous() {
        setStep(step - 1);
    }

    public void first() {
        setStep(0);
    }

    public void last() {
        setStep(size() - 1);
    }

    public double getSpeed() {
        return speed;
    }

    public void setSpeed(double speed) {
        if (speed <= 0) {
            throw new IllegalArgumentException("Speed must be more than 0");
        }
        this.speed = speed;
    }
}
