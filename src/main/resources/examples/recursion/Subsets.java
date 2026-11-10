// Every subset of {a, b, c}: for each letter, one branch leaves it out and one puts it in.
// That's a tree of calls, two branches at every level: open the Calls view to see it.
import java.util.ArrayList;
import java.util.List;

public class Subsets {
    static void subsets(char[] letters, int i, String chosen, List<String> out) {
        if (i == letters.length) {
            out.add("{" + chosen + "}");
            return;
        }
        subsets(letters, i + 1, chosen, out);                 // leave letters[i] out
        subsets(letters, i + 1, chosen + letters[i], out);    // put it in
    }

    public static void main(String[] args) {
        List<String> all = new ArrayList<>();
        subsets(new char[] {'a', 'b', 'c'}, 0, "", all);
        System.out.println(all);
    }
}
