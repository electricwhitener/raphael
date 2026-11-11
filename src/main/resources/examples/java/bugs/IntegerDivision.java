// BUG: the average of 1, 2 and 2 is 1.67, but this prints 1.0.
// Watch the type of each value: int / int throws away the decimals before the double gets it.
public class IntegerDivision {
    public static void main(String[] args) {
        int[] marks = {1, 2, 2};
        int total = 0;
        for (int m : marks) {
            total += m;
        }
        double average = total / marks.length;     // the fix: (double) total / marks.length
        System.out.println("average: " + average);
    }
}
