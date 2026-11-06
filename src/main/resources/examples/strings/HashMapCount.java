// A HashMap links each key to a value. Here each word is linked to how often it appears.
import java.util.HashMap;

public class HashMapCount {
    public static void main(String[] args) {
        String[] words = {"to", "be", "or", "not", "to", "be"};
        HashMap<String, Integer> counts = new HashMap<>();
        for (String word : words) {
            // 0 if the word is new, then one more
            counts.put(word, counts.getOrDefault(word, 0) + 1);
        }
        System.out.println(counts);
        System.out.println("'to' appears " + counts.get("to") + " times");
    }
}
