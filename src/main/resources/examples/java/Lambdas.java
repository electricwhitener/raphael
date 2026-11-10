// A lambda is a short way to write an object with one method. Here the same comparison is
// written as an anonymous class and as a lambda; sort() calls it to compare two words.
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Lambdas {
    public static void main(String[] args) {
        List<String> words = new ArrayList<>(List.of("kiwi", "fig", "banana"));
        Comparator<String> byLength = new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return a.length() - b.length();  // moment: sort() asks: is {a} shorter than {b}?
            }
        };
        words.sort(byLength);
        List<String> copy = new ArrayList<>(words);
        copy.sort((a, b) -> b.compareTo(a));     // the same idea as a lambda: reverse alphabetical
        System.out.println(words + " " + copy);
    }
}
