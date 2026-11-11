// A variable holds a reference (an arrow) to an object, not the object itself.
public class References {
    static class Box {
        int value;

        Box(int value) {
            this.value = value;
        }
    }

    public static void main(String[] args) {
        Box first = new Box(1);
        Box second = first;          // copies the arrow: both point at one box
        second.value = 99;           // so first.value is 99 too
        System.out.println(first.value);

        Box third = new Box(99);
        System.out.println(first == second);   // same object: true
        System.out.println(first == third);    // equal values, different objects: false

        second = null;               // second points at nothing now; the box is still there
        System.out.println(first.value);
    }
}
