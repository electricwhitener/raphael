package raphael.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import raphael.model.EndReason;
import raphael.model.Snapshot;
import raphael.model.Trace;
import raphael.render.Scene;
import raphael.render.SceneNode;

class TraceModelTest {

    // A model holding a run of the given number of (empty) steps, and a list of the step
    // numbers its listener was told about.
    private final List<Integer> heard = new ArrayList<>();

    private TraceModel modelWithSteps(int count) {
        List<Snapshot> snapshots = new ArrayList<>();
        List<Scene> scenes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            snapshots.add(new Snapshot(List.of(), Map.of(), Map.of(), 0, null));
            scenes.add(new Scene());
        }
        TraceModel model = new TraceModel();
        model.addListener(m -> heard.add(m.getStep()));
        model.load(new Trace(snapshots, "", EndReason.FINISHED, "done"), scenes);
        heard.clear();
        return model;
    }

    @Test
    void runSizeIsTheBiggestPictureOfTheRun() {
        Snapshot empty = new Snapshot(List.of(), Map.of(), Map.of(), 0, null);
        Scene wide = new Scene();
        wide.add(new SceneNode("a", SceneNode.Kind.CELL, 0, 0, 500, 10, "", null));
        Scene tall = new Scene();
        tall.add(new SceneNode("b", SceneNode.Kind.CELL, 0, 0, 10, 300, "", null));
        TraceModel model = new TraceModel();
        model.load(new Trace(List.of(empty, empty), "", EndReason.FINISHED, "done"), List.of(wide, tall));
        assertEquals(new Dimension(wide.getSize().width, tall.getSize().height), model.getRunSize());
        model.clear();
        assertEquals(new Dimension(), model.getRunSize());
    }

    @Test
    void stepIsKeptInsideTheRun() {
        TraceModel model = modelWithSteps(3);

        model.setStep(100);
        assertEquals(2, model.getStep());
        model.setStep(-5);
        assertEquals(0, model.getStep());
    }

    @Test
    void listenersHearEveryRealChangeOnce() {
        TraceModel model = modelWithSteps(3);

        model.next();
        model.next();
        model.next(); // already at the last step: nothing changes, so nobody is told
        model.previous();

        assertEquals(List.of(1, 2, 1), heard);
        assertFalse(model.isLastStep());
    }

    @Test
    void clearingRemovesTheRun() {
        TraceModel model = modelWithSteps(3);

        model.clear();

        assertFalse(model.hasSteps());
        assertNull(model.getSnapshot());
        model.next(); // must not crash
    }

    // Steps at these lines; depth = how many methods are running (2 = inside a call from main).
    private TraceModel modelWithCalls() {
        int[][] steps = {{10, 1}, {11, 1}, {3, 2}, {4, 2}, {11, 1}, {12, 1}};
        List<Snapshot> snapshots = new ArrayList<>();
        List<Scene> scenes = new ArrayList<>();
        for (int[] step : steps) {
            List<raphael.model.FrameSnapshot> frames = new ArrayList<>();
            for (int d = 0; d < step[1]; d++) {
                frames.add(new raphael.model.FrameSnapshot("Main", d == step[1] - 1 ? "main" : "helper",
                        d == 0 ? step[0] : 11, Map.of()));
            }
            snapshots.add(new Snapshot(frames, Map.of(), Map.of(), 0, null));
            scenes.add(new Scene());
        }
        TraceModel model = new TraceModel();
        model.load(new Trace(snapshots, "", EndReason.FINISHED, "done"), scenes);
        return model;
    }

    @Test
    void stepOverSkipsTheInsideOfACall() {
        TraceModel model = modelWithCalls();
        model.next();     // line 11, which calls helper
        model.stepOver(); // skip lines 3 and 4 inside helper
        assertEquals(4, model.getStep());
        assertEquals(11, model.getSnapshot().getLine());
    }

    @Test
    void stepOutRunsUntilTheMethodReturns() {
        TraceModel model = modelWithCalls();
        model.setStep(2); // inside helper, line 3
        model.stepOut();
        assertEquals(4, model.getStep());
    }

    @Test
    void jumpToLineFindsTheNextTimeItRuns() {
        TraceModel model = modelWithCalls();
        assertTrue(model.jumpToLine(11));
        assertEquals(1, model.getStep());
        assertTrue(model.jumpToLine(11));
        assertEquals(4, model.getStep());
        assertFalse(model.jumpToLine(99));
    }

    @Test
    void speedMustBePositive() {
        TraceModel model = new TraceModel();
        assertThrows(IllegalArgumentException.class, () -> model.setSpeed(0));
    }

    // Moments off: the run keeps its moments, but none are shown (no cards, marks or list) until
    // they're switched on again; the listeners hear of each change. The calls view still gets them all.
    @Test
    void momentsOffHidesThemWithoutLosingThem() {
        MomentMode before = MomentMode.current();
        try {
            MomentMode.set(MomentMode.SHOW);
            TraceModel model = new TraceModel();
            List<String> told = new ArrayList<>();
            model.addListener(new TraceListener() {
                @Override
                public void stepChanged(TraceModel m) {
                }

                @Override
                public void momentsChanged(TraceModel m) {
                    told.add("moments " + m.getMoments().size());
                }
            });
            Snapshot empty = new Snapshot(List.of(), Map.of(), Map.of(), 0, null);
            raphael.explain.Moment moment = new raphael.explain.Moment(1, "base-case", "Base case", "…", true, null, 3);
            model.load(new Trace(List.of(empty, empty), "", EndReason.FINISHED, "done"), List.of(new Scene(), new Scene()),
                    new Dimension(), List.of(moment));
            assertEquals(List.of(moment), model.getMoments());
            MomentMode.set(MomentMode.OFF);
            model.momentsChanged();
            assertEquals(List.of(), model.getMoments());
            assertEquals(List.of(), model.momentsAt(1));
            assertEquals(List.of(moment), model.getAllMoments());
            MomentMode.set(MomentMode.PAUSE);
            model.momentsChanged();
            assertEquals(List.of(moment), model.getMoments());
            assertEquals(List.of("moments 0", "moments 1"), told);
        } finally {
            MomentMode.set(before);
        }
    }
}
