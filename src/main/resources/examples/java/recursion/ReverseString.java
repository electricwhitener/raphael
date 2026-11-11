// Reversing a string recursively: the reverse of "abc" is the reverse of "bc", then "a".
// The base case is a string with nothing left to reverse.
public class ReverseString {
    static String reverse(String s) {
        if (s.length() <= 1) {
            return s;
        }
        return reverse(s.substring(1)) + s.charAt(0);
    }

    public static void main(String[] args) {
        System.out.println(reverse("stack"));
    }
}
