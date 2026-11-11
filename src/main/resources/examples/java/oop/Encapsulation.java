// Encapsulation: the fields are private, so the only way to change them is through methods
// that check the new value first.
public class Encapsulation {
    static class Thermostat {
        private int target = 20;

        int getTarget() {
            return target;
        }

        void setTarget(int degrees) {
            if (degrees < 10 || degrees > 30) {
                System.out.println(degrees + " is out of range, kept " + target);
                return;
            }
            target = degrees;
        }
    }

    public static void main(String[] args) {
        Thermostat home = new Thermostat();
        home.setTarget(24);
        home.setTarget(90);        // refused: the object protects its own state
        System.out.println("target: " + home.getTarget());
    }
}
