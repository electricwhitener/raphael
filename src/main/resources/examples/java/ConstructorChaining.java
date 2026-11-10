// Constructors run from the top of the family down: new Puppy() first runs Animal's
// constructor, then Dog's, then Puppy's own. super(...) passes values up to the parent.
public class ConstructorChaining {
    static class Animal {
        protected String name;

        Animal(String name) {
            this.name = name;
            System.out.println("Animal: " + name);
        }
    }

    static class Dog extends Animal {
        protected String sound = "Woof";

        Dog(String name) {
            super(name);
            System.out.println("Dog: " + sound);
        }
    }

    static class Puppy extends Dog {
        private final int ageInWeeks;

        Puppy(String name, int ageInWeeks) {
            super(name);
            this.ageInWeeks = ageInWeeks;
            System.out.println("Puppy: " + ageInWeeks + " weeks");
        }
    }

    public static void main(String[] args) {
        Puppy p = new Puppy("Bolt", 9);
        System.out.println(p.name + " says " + p.sound);
    }
}
