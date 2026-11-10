package raphael.render;

import java.awt.Dimension;

// Something that draws a run one step at a time: the calls view, the objects view, the files view.
// VisualPanel shows whichever one is chosen, and the Animator moves between its scenes.
// OOP: an interface. Each view is a different class behind it, so VisualPanel doesn't need to
// know which one it has (polymorphism).
public interface SceneSource {

    // The picture of this step.
    Scene sceneAt(int step);

    // The room the run's biggest step needs, so one scale fits the whole run.
    Dimension getRunSize();
}
