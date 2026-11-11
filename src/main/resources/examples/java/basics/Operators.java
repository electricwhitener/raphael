// Operators: / between two ints drops the remainder, % gives it, and an int that grows past
// its biggest value wraps round to a negative number (overflow). Watch each result appear.
public class Operators {
    public static void main(String[] args) {
        int a = 17;
        int b = 5;
        int quotient = a / b;                    // moment: {a} / {b} with two ints: the fraction is dropped
        int remainder = a % b;                   // moment: % gives what's left over after dividing
        double exact = a / 2.0;                  // moment: with a double in it, / keeps the fraction
        int i = 3;
        int before = i++;                        // moment: i++ gives the old value, then adds one
        int after = ++i;                         // moment: ++i adds one first, then gives the new value
        int big = Integer.MAX_VALUE;
        int wrapped = big + 1;                   // moment: {big} is the biggest int there is, so + 1 overflows
        System.out.println(quotient + " " + remainder + " " + exact + " " + before + " " + after + " " + wrapped);
    }
}
