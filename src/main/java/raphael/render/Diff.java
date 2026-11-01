package raphael.render;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

// What changed between two Scenes, found by comparing shape keys:
//   added   - only in the new scene (fades in)
//   removed - only in the old scene (fades out)
//   moved   - in both, but somewhere else (slides)
//   changed - in both, but showing a different value (flashes)
// OOP: encapsulation (the sets can only be read); a static factory method, between(),
// instead of a public constructor.
public class Diff {

    private final Set<String> added = new LinkedHashSet<>();
    private final Set<String> removed = new LinkedHashSet<>();
    private final Set<String> moved = new LinkedHashSet<>();
    private final Set<String> changed = new LinkedHashSet<>();

    private Diff() {
    }

    public static Diff between(Scene before, Scene after) {
        Diff diff = new Diff();
        for (SceneNode now : after.getNodes()) {
            SceneNode then = before.getNode(now.key());
            if (then == null) {
                diff.added.add(now.key());
                continue;
            }
            if (then.x() != now.x() || then.y() != now.y()) {
                diff.moved.add(now.key());
            }
            if (!then.text().equals(now.text()) || then.kind() != now.kind()) {
                diff.changed.add(now.key());
            }
        }
        for (SceneNode then : before.getNodes()) {
            if (after.getNode(then.key()) == null) {
                diff.removed.add(then.key());
            }
        }
        return diff;
    }

    public Set<String> getAdded() {
        return Collections.unmodifiableSet(added);
    }

    public Set<String> getRemoved() {
        return Collections.unmodifiableSet(removed);
    }

    public Set<String> getMoved() {
        return Collections.unmodifiableSet(moved);
    }

    public Set<String> getChanged() {
        return Collections.unmodifiableSet(changed);
    }
}
