// BUG: the two strings hold the same letters, but == says they're different.
// == asks "the same object?"; equals() asks "the same letters?".
public class StringEquals {
    public static void main(String[] args) {
        String typed = new String("yes");          // like text read from input
        String answer = "yes";
        if (typed == answer) {
            System.out.println("matched with ==");
        } else {
            System.out.println("== says no");
        }
        if (typed.equals(answer)) {
            System.out.println("matched with equals()");
        }
    }
}
