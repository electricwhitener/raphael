// The longest sequence of letters found in both words, in order (not always side by side).
// Cell [i][j] compares the first i letters of a with the first j of b.
public class LongestCommonSubsequence {
    public static void main(String[] args) {
        String a = "ABCB";
        String b = "BDCAB";
        int[][] len = new int[a.length() + 1][b.length() + 1];
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                if (a.charAt(i - 1) == b.charAt(j - 1)) {
                    len[i][j] = len[i - 1][j - 1] + 1;         // the letters match
                } else {
                    len[i][j] = Math.max(len[i - 1][j], len[i][j - 1]);
                }
            }
        }
        System.out.println("length: " + len[a.length()][b.length()]);
    }
}
