// A word is a palindrome if its first and last letters match and the middle is a palindrome.
// Two base cases: the ends don't match (false), or nothing is left in the middle (true).
public class RecursivePalindrome {
    static boolean isPalindrome(String s, int lo, int hi) {
        if (lo >= hi) {
            return true;
        }
        if (s.charAt(lo) != s.charAt(hi)) {
            return false;
        }
        return isPalindrome(s, lo + 1, hi - 1);
    }

    public static void main(String[] args) {
        System.out.println(isPalindrome("racecar", 0, 6) + " " + isPalindrome("rocket", 0, 5));
    }
}
