package raphael.render;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import org.junit.jupiter.api.Test;

class AnimatorTest {

    private static Scene oneCell(String text) {
        Scene scene = new Scene();
        scene.add(new SceneNode("a", SceneNode.Kind.CELL, 0, 0, 40, 30, text, null));
        return scene;
    }

    @Test
    void aJumpManyStepsAwayDoesNotFlash() {
        Animator stepped = new Animator(() -> { }, false);
        stepped.show(oneCell("1"));
        stepped.animateTo(oneCell("2"), 1, true);
        stepped.advance(3);
        assertEquals(Style.CHANGED_GLOW, stepped.getCurrent().getNode("a").look().flash(), 0.001);

        Animator jumped = new Animator(() -> { }, false);
        jumped.show(oneCell("1"));
        jumped.jumpTo(oneCell("2"), 1);
        jumped.advance(3);
        assertEquals(0, jumped.getCurrent().getNode("a").look().flash(), 0.001);
        assertEquals("2", jumped.getCurrent().getNode("a").text());
    }

    private static SceneNode cell(String key, double x, String text) {
        return new SceneNode(key, SceneNode.Kind.CELL, x, 0, 40, 30, text, null);
    }

    private static Scene scene(Object... parts) {
        Scene scene = new Scene();
        for (Object part : parts) {
            if (part instanceof SceneNode) {
                scene.add((SceneNode) part);
            } else if (part instanceof SceneEdge) {
                scene.add((SceneEdge) part);
            } else {
                scene.add((Scene.Move) part);
            }
        }
        return scene;
    }

    // An animator without a timer: the test moves time forward itself.
    private static Animator animator(Scene from, Scene to, boolean nextStep) {
        Animator animator = new Animator(() -> { }, false);
        animator.show(from);
        animator.animateTo(to, 1, nextStep);
        return animator;
    }

    private static void run(Animator animator, double seconds) {
        for (double t = 0; t < seconds; t += 0.01) {
            animator.advance(0.01);
        }
    }

    @Test
    void diffFindsAddedRemovedMovedAndChanged() {
        Scene before = scene(cell("a", 0, "1"), cell("b", 50, "2"), cell("c", 100, "3"));
        Scene after = scene(cell("a", 0, "9"), cell("b", 80, "2"), cell("d", 150, "4"));

        Diff diff = Diff.between(before, after);

        assertEquals(Set.of("d"), diff.getAdded());
        assertEquals(Set.of("c"), diff.getRemoved());
        assertEquals(Set.of("b"), diff.getMoved());
        assertEquals(Set.of("a"), diff.getChanged());
    }

    @Test
    void shapesGlideToTheirNewPlaceWithoutOvershooting() {
        Animator animator = animator(scene(cell("a", 0, "1")), scene(cell("a", 100, "1")), true);

        double last = 0;
        for (int frame = 0; frame < 100; frame++) {
            animator.advance(0.01);
            double x = animator.getCurrent().getNode("a").x();
            assertTrue(x >= last, "never moves backwards");
            assertTrue(x <= 100, "never overshoots");
            last = x;
        }
        assertEquals(100, last, 0.001);
        assertTrue(animator.isSettled());
    }

    @Test
    void newShapesFadeInAndLeavingShapesFadeOut() {
        Animator animator = animator(scene(cell("old", 0, "1")), scene(cell("new", 0, "2")), true);

        run(animator, 0.03);
        double arriving = animator.getCurrent().getNode("new").look().alpha();
        assertTrue(arriving > 0 && arriving < 1, "half visible while arriving");
        run(animator, 1);
        assertNull(animator.getCurrent().getNode("old"), "gone once faded out");
        assertEquals(1, animator.getCurrent().getNode("new").look().alpha(), 0.001);
    }

    @Test
    void aChangedValueRollsInFlashesAndKeepsAGlow() {
        Animator animator = animator(scene(cell("a", 0, "5")), scene(cell("a", 0, "2")), true);

        SceneNode.Look start = animator.getCurrent().getNode("a").look();
        assertEquals("5", start.oldText()); // the old value is still rolling out
        assertEquals(1, start.flash(), 0.001);
        run(animator, 2);
        SceneNode.Look end = animator.getCurrent().getNode("a").look();
        assertNull(end.oldText());
        assertEquals(Style.CHANGED_GLOW, end.flash(), 0.001);
    }

    @Test
    void anArrowSwingsToItsNewTarget() {
        SceneNode slot = new SceneNode("s", SceneNode.Kind.SLOT, 0, 0, 18, 34, "", null);
        Scene before = scene(slot, cell("a", 100, "1"), cell("b", 200, "2"),
                new SceneEdge("s→", "s", "a", SceneEdge.Kind.ARROW));
        Scene after = scene(slot, cell("a", 100, "1"), cell("b", 200, "2"),
                new SceneEdge("s→", "s", "b", SceneEdge.Kind.ARROW));
        Animator animator = animator(before, after, true);

        run(animator, 0.05);
        SceneEdge.Look swinging = animator.getCurrent().getEdge("s→").look();
        assertEquals("a", swinging.oldTo());
        assertTrue(swinging.swing() > 0 && swinging.swing() < 1);
        run(animator, 2);
        assertNull(animator.getCurrent().getEdge("s→").look().oldTo());
    }

    @Test
    void aCopiedValueFliesAcrossAndThenLands() {
        Scene before = scene(cell("#1[0]", 0, "5"), cell("#1[1]", 44, "2"));
        Scene after = scene(cell("#1[0]", 0, "2"), cell("#1[1]", 44, "2"), new Scene.Move("#1[1]", "#1[0]"));
        Animator animator = animator(before, after, true);

        run(animator, 0.05);
        assertTrue(animator.getCurrent().getNodes().stream().anyMatch(n -> n.kind() == SceneNode.Kind.FLYER));
        run(animator, 2);
        assertTrue(animator.getCurrent().getNodes().stream().noneMatch(n -> n.kind() == SceneNode.Kind.FLYER));
        assertTrue(animator.isSettled());
    }
}
