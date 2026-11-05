package raphael.render;

import java.awt.Dimension;
import java.util.AbstractList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntFunction;

// The pictures of a run, one per step, each built the first time it's asked for. Building them
// all up front kept every shape of every step in memory: 155 MB for a 2,000-step list of 500
// nodes, and ten times that at the 20,000-step limit. Here only the 64 most recently shown are
// kept; going back further builds the step again (well under a millisecond), and it comes out
// exactly the same.
// The run's biggest picture sets one scale for the whole run (VisualPanel.fitToRun), so every
// step is built once at the start to measure it, then let go.
// OOP: inheritance (extends AbstractList, so the rest of Raphael just sees a List<Scene>);
// composition (it holds the function that builds a step); an anonymous subclass of LinkedHashMap
// that overrides removeEldestEntry, which makes it a "least recently used" cache.
public class SceneList extends AbstractList<Scene> {

    private static final int KEPT = 64;

    private final int size;
    private final IntFunction<Scene> builder;
    private final Dimension runSize = new Dimension();
    private final Map<Integer, Scene> recent = new LinkedHashMap<>(KEPT * 2, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, Scene> eldest) {
            return size() > KEPT;
        }
    };

    SceneList(int size, IntFunction<Scene> builder) {
        this.size = size;
        this.builder = builder;
        for (int step = 0; step < size; step++) {
            Dimension sceneSize = builder.apply(step).getSize();
            runSize.width = Math.max(runSize.width, sceneSize.width);
            runSize.height = Math.max(runSize.height, sceneSize.height);
        }
    }

    @Override
    public Scene get(int step) {
        Objects.checkIndex(step, size);
        return recent.computeIfAbsent(step, builder::apply);
    }

    @Override
    public int size() {
        return size;
    }

    // The room the run's biggest picture needs.
    public Dimension getRunSize() {
        return new Dimension(runSize);
    }
}
