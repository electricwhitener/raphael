package raphael.render;

// One number (an x, a y, a width…) pulled toward its target by a spring: like a weight on an
// elastic band with just enough friction that it never wobbles past the target ("critically
// damped"). The spring's equation is solved exactly for each time step, so the motion is the
// same at any frame rate:
//     x(t) = target + (a + b·t)·e^(−ω·t),   where a = start − target and b = speed + ω·a
// Changing the target keeps the current speed, so a shape that is already moving bends
// smoothly toward its new place instead of stopping and starting again.
// OOP: encapsulation. Value, speed and target are private; step() is the only way to move.
public class Spring {

    private double value;
    private double velocity; // units per second
    private double target;

    public Spring(double value) {
        this.value = value;
        this.target = value;
    }

    public double get() {
        return value;
    }

    public double getVelocity() {
        return velocity;
    }

    public void setTarget(double target) {
        this.target = target;
    }

    // Puts the value straight at a place, with no motion.
    public void jumpTo(double value) {
        this.value = value;
        this.target = value;
        this.velocity = 0;
    }

    // Moves forward dt seconds. omega (ω) is how stiff the spring is: bigger arrives sooner.
    public void step(double dt, double omega) {
        double a = value - target;
        double b = velocity + omega * a;
        double decay = Math.exp(-omega * dt);
        value = target + (a + b * dt) * decay;
        velocity = (b - omega * (a + b * dt)) * decay;
        if (isSettled()) {
            value = target;
            velocity = 0;
        }
    }

    public boolean isSettled() {
        return Math.abs(value - target) < 0.01 && Math.abs(velocity) < 0.05;
    }
}
