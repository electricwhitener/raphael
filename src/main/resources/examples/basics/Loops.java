// Three loops: for counts a known number of times, while repeats while a condition holds,
// and do-while always runs its body once before checking. Watch the counters change.
public class Loops {
    public static void main(String[] args) {
        int sum = 0;
        for (int i = 1; i <= 4; i++) {
            sum += i;
        }
        int n = 3071;
        int digits = 0;
        while (n > 0) {
            n = n / 10;                          // moment: dividing by 10 drops the last digit of {n}
            digits++;
        }
        int tries = 0;
        do {
            tries++;                             // moment: do-while: the body runs before the condition is checked
        } while (tries < 0);
        System.out.println(sum + " " + digits + " " + tries);
    }
}
