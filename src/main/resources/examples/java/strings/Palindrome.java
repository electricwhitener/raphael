// A palindrome reads the same both ways. Two indexes walk in from the ends and compare.
public class Palindrome {
    static boolean isPalindrome(String text) {
        char[] letters = text.toCharArray();
        int i = 0;
        int j = letters.length - 1;
        while (i < j) {
            if (letters[i] != letters[j]) {
                return false;          // one mismatch is enough
            }
            i++;
            j--;
        }
        return true;
    }

    public static void main(String[] args) {
        System.out.println(isPalindrome("racecar"));
        System.out.println(isPalindrome("rocket"));
    }
}
