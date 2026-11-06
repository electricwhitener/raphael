// Composition: a Car is built from other objects. It has an Engine and four Wheels,
// and passes work on to them.
public class Composition {
    static class Engine {
        boolean running;

        void start() {
            running = true;
        }
    }

    static class Wheel {
        int pressure = 30;
    }

    static class Car {
        Engine engine = new Engine();
        Wheel[] wheels = {new Wheel(), new Wheel(), new Wheel(), new Wheel()};

        void drive() {
            engine.start();            // the car asks its engine
            for (Wheel w : wheels) {
                w.pressure--;
            }
        }
    }

    public static void main(String[] args) {
        Car car = new Car();
        car.drive();
        System.out.println("engine running: " + car.engine.running);
    }
}
