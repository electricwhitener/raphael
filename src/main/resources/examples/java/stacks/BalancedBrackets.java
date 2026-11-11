// Are the brackets balanced? Push each opening bracket. A closing bracket must match the one
// on top of the stack. At the end the stack must be empty.
public class BalancedBrackets {
    static boolean balanced(String text) {
        char[] stack = new char[text.length()];
        int top = 0;
        for (char c : text.toCharArray()) {
            if (c == '(' || c == '[' || c == '{') {
                stack[top++] = c;
            } else if (c == ')' || c == ']' || c == '}') {
                if (top == 0) {
                    return false;      // nothing to close
                }
                char open = stack[--top];
                if ((c == ')' && open != '(') || (c == ']' && open != '[') || (c == '}' && open != '{')) {
                    return false;      // the wrong kind
                }
            }
        }
        return top == 0;
    }

    public static void main(String[] args) {
        System.out.println(balanced("{[()()]}"));
        System.out.println(balanced("([)]"));
    }
}
