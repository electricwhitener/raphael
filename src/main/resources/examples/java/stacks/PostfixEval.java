// Postfix (reverse Polish) puts the operator after its two numbers: "2 3 4 * +" is 2 + 3 * 4.
// Numbers go on a stack; an operator pops two, works them out, and pushes the answer.
import java.util.ArrayDeque;

public class PostfixEval {
    public static void main(String[] args) {
        String[] tokens = {"2", "3", "4", "*", "+", "5", "-"};
        ArrayDeque<Integer> stack = new ArrayDeque<>();
        for (String t : tokens) {
            if (t.equals("+") || t.equals("-") || t.equals("*")) {
                int b = stack.pop();   // the second number was pushed last
                int a = stack.pop();
                int result = t.equals("+") ? a + b : t.equals("-") ? a - b : a * b;
                stack.push(result);
            } else {
                stack.push(Integer.parseInt(t));
            }
        }
        System.out.println("= " + stack.pop());
    }
}
