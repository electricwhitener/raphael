// Every order of A, B and C. Fix one letter in place k, arrange the rest the same way,
// then swap back (backtrack) before trying the next letter there.
public class Permutations {
    static void permute(char[] letters, int k) {
        if (k == letters.length) {
            System.out.println(new String(letters));
            return;
        }
        for (int i = k; i < letters.length; i++) {
            swap(letters, k, i);
            permute(letters, k + 1);
            swap(letters, k, i);       // undo, so the next try starts clean
        }
    }

    static void swap(char[] letters, int a, int b) {
        char tmp = letters[a];
        letters[a] = letters[b];
        letters[b] = tmp;
    }

    public static void main(String[] args) {
        permute(new char[] {'A', 'B', 'C'}, 0);
    }
}
