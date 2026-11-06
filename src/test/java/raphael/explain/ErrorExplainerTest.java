package raphael.explain;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ErrorExplainerTest {

    private static void says(String type, String message, String... parts) {
        String text = ErrorExplainer.explain(type, message);
        for (String part : parts) {
            assertTrue(text.contains(part), () -> "\"" + text + "\" lacks \"" + part + "\"");
        }
    }

    @Test
    void namesTheVariableThatWasNull() {
        says("NullPointerException", "Cannot read field \"next\" because \"curr\" is null",
                "curr was null", "read its field next");
        says("NullPointerException", "Cannot invoke \"Main$Node.getValue()\" because \"this.head\" is null",
                "this.head was null", "call getValue() on it");
        says("NullPointerException", "Cannot load from int array because \"arr\" is null", "arr was null", "read a cell");
        says("NullPointerException", null, "pointed to no object");
    }

    @Test
    void explainsAnIndexPastTheEnd() {
        says("ArrayIndexOutOfBoundsException", "Index 5 out of bounds for length 5",
                "5 cells, numbered 0 to 4", "<= instead of <");
        says("ArrayIndexOutOfBoundsException", "Index -1 out of bounds for length 3", "never negative");
        says("IndexOutOfBoundsException", "Index 2 out of bounds for length 0", "list is empty");
        says("StringIndexOutOfBoundsException", "index 9, length 4", "string has 4 characters");
    }

    @Test
    void explainsTheCommonOthers() {
        says("ArithmeticException", "/ by zero", "divided by zero");
        says("NumberFormatException", "For input string: \"abc\"", "\"abc\"", "isn't one");
        says("NumberFormatException", "For input string: \" 42\"", "trim()");
        says("InputMismatchException", null, "expected a number");
        says("NoSuchElementException", null, "more input");
        says("ClassCastException", "class Main$Cat cannot be cast to class Main$Dog (Main$Cat and Main$Dog are in unnamed module)",
                "Cat", "isn't a Dog", "instanceof");
        says("StackOverflowError", null, "base case");
        says("ConcurrentModificationException", null, "for-each loop");
        says("OutOfMemoryError", "Java heap space", "memory");
        says("IllegalStateException", "the stack is full", "on purpose", "the stack is full");
    }

    @Test
    void anythingElseStillGetsASentence() {
        says("StackEmptyException", "pop on an empty stack", "StackEmptyException", "pop on an empty stack");
    }

    @Test
    void onlyRealAdviceCountsAsAdvice() {
        assertTrue(ErrorExplainer.hasAdvice("NullPointerException"));
        assertTrue(!ErrorExplainer.hasAdvice("IllegalStateException"));
        assertTrue(!ErrorExplainer.hasAdvice("StackEmptyException"));
    }
}
