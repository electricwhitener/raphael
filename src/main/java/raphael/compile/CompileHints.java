package raphael.compile;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// A plain hint for javac's common error messages. javac says what it saw ("';' expected",
// "cannot find symbol"); the hint says what usually causes it and how to fix it. Messages
// without a hint (the rare ones) are shown as they are.
// OOP: a utility class (static methods, private constructor).
public final class CompileHints {

    private CompileHints() {
    }

    // The hint for one of javac's messages, or null if there isn't one.
    public static String hint(String message) {
        if (message == null) {
            return null;
        }
        String first = message.lines().findFirst().orElse("").trim();
        if (first.equals("';' expected")) {
            return "A statement is missing the ; at its end. (javac often points at the line after it.)";
        }
        if (first.equals("')' expected") || first.equals("'(' expected") || first.equals("']' expected")) {
            return "A bracket is missing, or one too many is open, on this line or the one before.";
        }
        if (first.equals("<identifier> expected")) {
            return "Java expected a name here. A statement may be outside a method, or a type is missing.";
        }
        if (first.startsWith("cannot find symbol")) {
            String kind = find(message, "symbol:\\s+(variable|method|class)");
            String name = find(message, "symbol:\\s+\\w+\\s+(\\w+)");
            if (kind == null || name == null) {
                return "Java doesn't know a name used here. Check its spelling (capitals count) and that it's declared.";
            }
            return switch (kind) {
                case "variable" -> "Java doesn't know the variable " + name + ". Check that it's declared before this line, "
                        + "in this block or an outer one, and spelled the same way (capitals count).";
                case "method" -> "Java doesn't know a method " + name + "(…) that takes these arguments. "
                        + "Check its name, and the number and types of what's passed.";
                default -> "Java doesn't know the class " + name + ". Check its spelling, or whether an import is missing "
                        + "(like import java.util.*;).";
            };
        }
        if (first.startsWith("incompatible types: possible lossy conversion from")) {
            String from = find(first, "from (\\w+)");
            String to = find(first, "to (\\w+)");
            return "A " + from + " is stored in an " + to + ", which could lose part of it (the decimals, for example). "
                    + "Write a cast like (" + to + ") if that's what's meant.";
        }
        if (first.startsWith("incompatible types:")) {
            String from = find(first, "types: (.+?) cannot be converted");
            String to = find(first, "converted to (.+)$");
            return from == null || to == null ? "A value of one type is used where another type is needed."
                    : "A " + from + " is used where a " + to + " is needed.";
        }
        if (first.equals("missing return statement")) {
            return "Some way through this method reaches its end without returning a value. Add a return there.";
        }
        if (first.equals("unreachable statement")) {
            return "This line can never run, because a return, break, continue or throw always comes before it.";
        }
        if (first.matches("variable \\w+ might not have been initialized")) {
            return find(first, "variable (\\w+)") + " is used before it's given a value on every path. Give it a starting value.";
        }
        if (first.matches("variable \\w+ is already defined.*")) {
            return find(first, "variable (\\w+)") + " is declared twice in the same place. To reuse it, leave out its type "
                    + "the second time; otherwise give the new one another name.";
        }
        if (first.equals("reached end of file while parsing")) {
            return "A { is never closed: a } is missing, probably near the end.";
        }
        if (first.startsWith("class, interface, enum, or record expected")) {
            return "There's code outside a class. Often a } too many closed the class early.";
        }
        if (first.equals("'else' without 'if'")) {
            return "This else has no if just before it. Check the braces, and for a stray ; right after the if (…).";
        }
        if (first.equals("not a statement")) {
            return "This can't be a line on its own. A statement assigns, calls a method, declares something, and so on.";
        }
        if (first.equals("unclosed string literal")) {
            return "A string is missing its closing \".";
        }
        if (first.startsWith("non-static variable") || first.startsWith("non-static method")) {
            return "main is static, so it can't use this directly. Make it static too, or make an object and use it through that.";
        }
        if (first.equals("illegal start of expression")) {
            return "Something is missing or extra just before this point: often a }, ) or ; .";
        }
        if (first.startsWith("bad operand types for binary operator")) {
            return "This operator doesn't work on these types. To compare Strings, use equals() or compareTo(), not == or <.";
        }
        if (first.matches("(method|constructor) .* cannot be applied to given types;?")) {
            return "It's called with the wrong number or types of arguments. Compare the call with its declaration.";
        }
        if (first.startsWith("unreported exception")) {
            String type = find(first, "unreported exception (\\S+?);");
            return type + " is a checked exception: catch it with try/catch, or add \"throws " + type + "\" to the method.";
        }
        return null;
    }

    private static String find(String text, String regex) {
        Matcher match = Pattern.compile(regex).matcher(text);
        return match.find() ? match.group(1) : null;
    }
}
