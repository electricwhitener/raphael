// An enum is a type with a fixed set of values. The compiler knows every value, so a switch
// can't be given anything else.
public class Enums {
    enum Light {
        RED, GREEN, YELLOW
    }

    static Light next(Light now) {
        switch (now) {
            case RED:
                return Light.GREEN;
            case GREEN:
                return Light.YELLOW;
            default:
                return Light.RED;
        }
    }

    public static void main(String[] args) {
        Light light = Light.RED;
        for (int i = 0; i < 4; i++) {
            System.out.println(light + " (number " + light.ordinal() + ")");
            light = next(light);
        }
    }
}
