// Inheritance: Dog and Cat extend Animal, so they get its name field and eat() method,
// and add or change what they need.
class Animal {
    protected String name;
    protected int energy = 5;

    Animal(String name) {
        this.name = name;
    }

    void eat() {
        energy += 3;
    }

    String sound() {
        return "...";
    }
}

class Dog extends Animal {
    int tricks;

    Dog(String name) {
        super(name);               // runs Animal's constructor first
    }

    @Override
    String sound() {
        return "Woof";
    }

    void learnTrick() {
        tricks++;
        energy--;
    }
}

class Cat extends Animal {
    Cat(String name) {
        super(name);
    }

    @Override
    String sound() {
        return "Meow";
    }
}

public class Inheritance {
    public static void main(String[] args) {
        Dog rex = new Dog("Rex");
        Cat tom = new Cat("Tom");
        rex.learnTrick();
        rex.eat();                 // eat() comes from Animal
        tom.eat();
        System.out.println(rex.name + " says " + rex.sound() + ", " + tom.name + " says " + tom.sound());
    }
}
