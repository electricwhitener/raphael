// Counting letters with an array of 26 counters. A char is a number underneath, so
// c - 'a' turns 'a' into 0, 'b' into 1, and so on: the index of that letter's counter.
public class LetterCount {
    public static void main(String[] args) {
        String text = "banana";
        int[] counts = new int[26];
        for (char c : text.toCharArray()) {
            counts[c - 'a']++;
        }
        System.out.println("a: " + counts[0] + ", b: " + counts[1] + ", n: " + counts['n' - 'a']);
    }
}
