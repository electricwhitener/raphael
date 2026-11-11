// Interfaces: a class promises abilities by implementing interfaces. A Duck is both
// a Swimmer and a Flyer; code that only needs a Flyer doesn't care what else it is.
interface Swimmer {
    String swim();
}

interface Flyer {
    String fly();

    default String land() {        // a default method: shared code in an interface
        return "lands";
    }
}

class Duck implements Swimmer, Flyer {
    public String swim() {
        return "paddles";
    }

    public String fly() {
        return "flaps";
    }
}

class Plane implements Flyer {
    public String fly() {
        return "roars";
    }
}

public class Interfaces {
    public static void main(String[] args) {
        Flyer[] flyers = {new Duck(), new Plane()};
        for (Flyer f : flyers) {
            System.out.println(f.fly() + ", then " + f.land());
        }
        Swimmer s = new Duck();
        System.out.println(s.swim());
    }
}
