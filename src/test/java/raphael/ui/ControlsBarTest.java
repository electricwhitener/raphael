package raphael.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import raphael.model.EndReason;
import raphael.model.Snapshot;
import raphael.model.Trace;
import raphael.render.Scene;

class ControlsBarTest {

    private static TraceModel modelWithSteps(int count) {
        List<Snapshot> snapshots = new ArrayList<>();
        List<Scene> scenes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            snapshots.add(new Snapshot(List.of(), Map.of(), Map.of(), 0, null));
            scenes.add(new Scene());
        }
        TraceModel model = new TraceModel();
        model.load(new Trace(snapshots, "", EndReason.FINISHED, "done"), scenes);
        return model;
    }

    @Test
    void movingToAStepByHandPausesPlaying() {
        TraceModel model = modelWithSteps(5);
        ControlsBar controls = new ControlsBar(model);
        model.addListener(controls);
        controls.play();
        assertTrue(controls.isPlaying());
        model.next();
        assertFalse(controls.isPlaying());
    }

    @Test
    void thereIsNothingToPlayWithOnlyOneStep() {
        TraceModel model = modelWithSteps(1);
        ControlsBar controls = new ControlsBar(model);
        controls.play();
        assertFalse(controls.isPlaying());
    }
}
