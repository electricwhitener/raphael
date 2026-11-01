package raphael.render;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SpringTest {

    private static final double OMEGA = 18;

    @Test
    void arrivesWithoutOvershooting() {
        Spring spring = new Spring(0);
        spring.setTarget(100);
        double highest = 0;
        for (int i = 0; i < 100; i++) {
            spring.step(0.01, OMEGA);
            highest = Math.max(highest, spring.get());
        }
        assertEquals(100, spring.get(), 0.001);
        assertTrue(highest <= 100.0001);
        assertTrue(spring.isSettled());
    }

    @Test
    void keepsItsSpeedWhenTheTargetChanges() {
        Spring spring = new Spring(0);
        spring.setTarget(100);
        spring.step(0.05, OMEGA);
        double speed = spring.getVelocity();
        assertTrue(speed > 0);

        spring.setTarget(200); // a new step arrives mid-move
        assertEquals(speed, spring.getVelocity(), 1e-9, "no sudden stop");
        double before = spring.get();
        spring.step(0.01, OMEGA);
        assertTrue(spring.get() > before, "still moving the same way");
    }

    @Test
    void sameMotionAtAnyFrameRate() {
        Spring big = new Spring(0);
        Spring small = new Spring(0);
        big.setTarget(50);
        small.setTarget(50);
        big.step(0.1, OMEGA);
        for (int i = 0; i < 10; i++) {
            small.step(0.01, OMEGA);
        }
        assertEquals(big.get(), small.get(), 1e-9);
    }
}
