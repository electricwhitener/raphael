// An ArrayList is an array that grows by itself. Removing an item shifts the later ones left.
import java.util.ArrayList;

public class ArrayListBasics {
    public static void main(String[] args) {
        ArrayList<String> fruits = new ArrayList<>();
        fruits.add("apple");
        fruits.add("banana");
        fruits.add("cherry");
        fruits.add(1, "kiwi");         // insert at index 1
        fruits.set(0, "mango");        // replace index 0
        fruits.remove("banana");       // remove by value
        System.out.println(fruits.size() + " fruits: " + fruits);
        System.out.println("first: " + fruits.get(0));
    }
}
