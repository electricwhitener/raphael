// expect: FINISHED
// output: NaN
// output: -2147483648
// output: Infinity
// output: 9223372036854775807
public class Main {
    public static void main(String[] args) {
        double nan = 0.0 / 0.0;
        double inf = 1.0 / 0.0;
        double negZero = -0.0;
        int wrapped = Integer.MAX_VALUE + 1;
        long big = Long.MAX_VALUE;
        char zero = '\u0000';
        float tiny = Float.MIN_VALUE;
        byte b = (byte) 200;
        System.out.println(nan);
        System.out.println(wrapped);
        System.out.println(inf);
        System.out.println(big);
    }
}
