// Strings never change: toUpperCase() makes a new string. A StringBuilder can change in place.
// == asks "the same object?", equals() asks "the same letters?".
public class StringsBasics {
    public static void main(String[] args) {
        String word = "java";
        String loud = word.toUpperCase();        // moment: this makes a new string; word stays "java"
        StringBuilder sb = new StringBuilder();
        for (int i = word.length() - 1; i >= 0; i--) {
            sb.append(word.charAt(i));
        }
        String reversed = sb.toString();
        String typed = new String("java");
        boolean same = typed == word;            // moment: == compares references: typed and word are different objects
        boolean equal = typed.equals(word);      // moment: equals() compares the letters, which match
        System.out.println(loud + " " + reversed + " " + same + " " + equal);
    }
}
