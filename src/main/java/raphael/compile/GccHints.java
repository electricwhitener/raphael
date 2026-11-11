package raphael.compile;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// A plain hint for the C compiler's common errors, like CompileHints for javac. Each recognises
// gcc's wording and Apple clang's (on a Mac, gcc is clang).
// OOP: a utility class (static methods, private constructor).
public final class GccHints {

    private GccHints() {
    }

    // The hint for one of the compiler's messages (with its notes on the lines after), or null.
    public static String hint(String message) {
        if (message == null) {
            return null;
        }
        String first = message.lines().findFirst().orElse("").trim();
        if (first.contains("expected ';'") || first.contains("expected ',' or ';'")) {
            return "A statement is missing the ; at its end. (gcc often points at the line after it.)";
        }
        if (first.contains("expected declaration or statement at end of input") || first.equals("expected '}'")) {
            return "A { has no matching }. Count the braces of the function above.";
        }
        String function = find(first, "(?:implicit declaration of function|call to undeclared (?:library )?function) '(\\w+)'");
        if (function != null) {
            String header = find(message, "<(\\w+\\.h)>");
            return header != null
                    ? "C doesn't know " + function + " until its header is included: add #include <" + header + "> at the top."
                    : "C doesn't know a function " + function + " here. Check its spelling, or write (or declare) it "
                    + "above the line that calls it.";
        }
        String variable = find(first, "^'(\\w+)' undeclared|use of undeclared identifier '(\\w+)'");
        if (variable != null) {
            return "C doesn't know the variable " + variable + ". Check that it's declared before this line, in this "
                    + "block or an outer one, and spelled the same way (capitals count).";
        }
        if (first.contains("incompatible pointer type")) {
            String to = find(first, "(?:of|to|initializing) '([^']+)'");
            String from = find(first, "(?:pointer type|of type) '([^']+)'");
            return to == null || from == null ? "A pointer to one type is used where a pointer to another type is needed."
                    : "A " + from + " is used where a " + to + " is needed: they point to different types.";
        }
        if (first.contains("makes pointer from integer") || first.contains("integer to pointer conversion")) {
            return "A number is used where a pointer (an address) is needed. A & may be missing, or a * is in the wrong place.";
        }
        if (first.contains("makes integer from pointer") || first.contains("pointer to integer conversion")) {
            return "A pointer (an address) is used where a number is needed. A * may be missing to read what it points to.";
        }
        if (first.startsWith("conflicting types for")) {
            return "This function is declared twice in different ways, or called before C has seen it "
                    + "(C then guesses that it returns an int). Declare it once, above its first call.";
        }
        if (first.equals("the program has no main function")) {
            return "Every C program starts at main. Add int main(void) { … }.";
        }
        if (first.equals("main is written more than once")) {
            return "Two files each have a main. A program has one; remove or rename the other.";
        }
        String missing = find(first, "^undefined reference to (\\w+)");
        if (missing != null) {
            return missing + " is declared, but its body isn't written anywhere in the program (or it's spelled "
                    + "differently there).";
        }
        String twice = find(first, "^(\\w+) is written more than once");
        if (twice != null) {
            return "Two files both write " + twice + ". Keep one, or make the other static so it stays in its file.";
        }
        return null;
    }

    // The first group that matched, or null.
    private static String find(String text, String regex) {
        Matcher match = Pattern.compile(regex).matcher(text);
        if (!match.find()) {
            return null;
        }
        for (int i = 1; i <= match.groupCount(); i++) {
            if (match.group(i) != null) {
                return match.group(i);
            }
        }
        return null;
    }
}
