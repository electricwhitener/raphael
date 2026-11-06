package raphael.explain;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Turns an exception into a sentence a student can act on. Java's messages are written for
// programmers who already know what went wrong; "Index 5 out of bounds for length 5" becomes
// "The array has 5 cells, numbered 0 to 4, so there's no cell 5. A loop that uses <= instead of
// < often does this."
// Each kind of exception has its own explainer. explain() asks each in turn whether it handles
// this type, and the first that does writes the explanation; Fallback handles everything else.
// The messages Java writes are the explainers' best source: since Java 14, a NullPointerException
// names the variable that was null ("because \"curr\" is null"), because Raphael compiles with -g.
// OOP: an abstract class with an abstract method each subclass overrides (polymorphism); nested
// static subclasses (inheritance); explain() picks the subclass that handles the type and calls
// its describe(), without knowing which one it is.
public abstract class ErrorExplainer {

    private static final List<ErrorExplainer> EXPLAINERS = List.of(
            new NullPointer(), new IndexOutOfBounds(), new DivideByZero(), new NumberFormat(), new BadInput(),
            new NoMoreInput(), new ClassCast(), new StackOverflow(), new ConcurrentModification(),
            new OutOfMemory(), new Thrown());

    // Whether this explainer knows the exception type ("NullPointerException").
    abstract boolean handles(String type);

    // The explanation: one or two plain sentences. message may be null.
    abstract String describe(String type, String message);

    // The explanation of any exception (type is the short name, like "NullPointerException").
    public static String explain(String type, String message) {
        for (ErrorExplainer explainer : EXPLAINERS) {
            if (explainer.handles(type)) {
                return explainer.describe(type, message);
            }
        }
        return new Fallback().describe(type, message);
    }

    // Whether there's advice for this type, beyond restating what was thrown. The program's own
    // exceptions, and ones like IllegalArgumentException that programs throw on purpose, have none.
    public static boolean hasAdvice(String type) {
        for (ErrorExplainer explainer : EXPLAINERS) {
            if (explainer.handles(type)) {
                return !(explainer instanceof Thrown);
            }
        }
        return false;
    }

    // The first group of a pattern in the message, or null.
    static String find(String message, String regex) {
        if (message == null) {
            return null;
        }
        Matcher match = Pattern.compile(regex).matcher(message);
        return match.find() ? match.group(1) : null;
    }

    static class NullPointer extends ErrorExplainer {
        @Override
        boolean handles(String type) {
            return type.equals("NullPointerException");
        }

        @Override
        String describe(String type, String message) {
            String what = find(message, "because \"(.+?)\" is null");
            if (what == null) {
                return "Something was null (it pointed to no object), and the line used it as if it were an object.";
            }
            String action = find(message, "^Cannot (read field \"\\w+\"|assign field \"\\w+\"|invoke \"[^\"]+\""
                    + "|read the array length|load from \\w+ array|store to \\w+ array)");
            String doing = action == null ? "use it"
                    : action.startsWith("read field") ? "read its field " + find(action, "\"(\\w+)\"")
                    : action.startsWith("assign field") ? "set its field " + find(action, "\"(\\w+)\"")
                    : action.startsWith("invoke") ? "call " + method(find(action, "\"([^\"]+)\"")) + " on it"
                    : action.startsWith("read the array length") ? "read its length"
                    : action.startsWith("load") ? "read a cell of it" : "write a cell of it";
            return what + " was null (it pointed to no object), so the line couldn't " + doing + ". "
                    + "Check where " + what + " gets its value, or test for null before using it.";
        }

        // "Main$Node.getValue()" → "getValue()"
        private static String method(String signature) {
            String name = signature.replaceAll("\\(.*", "");
            return name.substring(name.lastIndexOf('.') + 1) + "()";
        }
    }

    static class IndexOutOfBounds extends ErrorExplainer {
        @Override
        boolean handles(String type) {
            return type.endsWith("IndexOutOfBoundsException");
        }

        @Override
        String describe(String type, String message) {
            String index = find(message, "(?:Index|index) (-?\\d+)");
            String length = find(message, "(?:length|size:?) (\\d+)");
            String what = type.startsWith("String") ? "string" : type.startsWith("Array") ? "array" : "list";
            String cells = type.startsWith("String") ? "characters" : what.equals("array") ? "cells" : "items";
            if (index == null || length == null) {
                return "The line used a position that isn't in the " + what + ".";
            }
            int i = Integer.parseInt(index);
            int n = Integer.parseInt(length);
            String range = n == 0 ? "The " + what + " is empty, so it has no position " + i + " at all."
                    : "The " + what + " has " + n + " " + cells + ", numbered 0 to " + (n - 1) + ", so there's no position " + i + ".";
            if (i == n) {
                return range + " A loop that uses <= instead of < often does this.";
            }
            if (i < 0) {
                return range + " Positions start at 0, so they're never negative.";
            }
            return range;
        }
    }

    static class DivideByZero extends ErrorExplainer {
        @Override
        boolean handles(String type) {
            return type.equals("ArithmeticException");
        }

        @Override
        String describe(String type, String message) {
            if (message != null && message.contains("by zero")) {
                return "The line divided by zero (with / or %). Whole numbers can't be divided by zero; check the divisor first.";
            }
            return "A calculation on the line can't be done: " + message + ".";
        }
    }

    static class NumberFormat extends ErrorExplainer {
        @Override
        boolean handles(String type) {
            return type.equals("NumberFormatException");
        }

        @Override
        String describe(String type, String message) {
            String text = find(message, "For input string: \"(.*)\"");
            if (text == null) {
                return "A text was turned into a number, but it isn't one.";
            }
            return text.isEmpty() ? "An empty text was turned into a number. There's no number in it."
                    : "\"" + text + "\" was turned into a number, but it isn't one"
                    + (text.trim().equals(text) ? "." : ": it has spaces around it. trim() removes them.");
        }
    }

    static class BadInput extends ErrorExplainer {
        @Override
        boolean handles(String type) {
            return type.equals("InputMismatchException");
        }

        @Override
        String describe(String type, String message) {
            return "The input had something else where the program expected a number (for example, Scanner's nextInt() "
                    + "read a word). Check what's typed in the stdin box.";
        }
    }

    static class NoMoreInput extends ErrorExplainer {
        @Override
        boolean handles(String type) {
            return type.equals("NoSuchElementException");
        }

        @Override
        String describe(String type, String message) {
            return "The program asked for more input than it was given, or for the next item when there wasn't one. "
                    + "If it reads with Scanner, type all its input in the stdin box before running.";
        }
    }

    static class ClassCast extends ErrorExplainer {
        @Override
        boolean handles(String type) {
            return type.equals("ClassCastException");
        }

        @Override
        String describe(String type, String message) {
            String from = find(message, "class (\\S+) cannot be cast");
            String to = find(message, "to class (\\S+)");
            if (from == null || to == null) {
                return "An object was cast to a type it isn't.";
            }
            return "The object is a " + simple(from) + ", and a " + simple(from) + " isn't a " + simple(to)
                    + ", so it can't be cast to one. Check its type with instanceof first.";
        }

        private static String simple(String name) {
            String last = name.substring(name.lastIndexOf('.') + 1);
            return last.substring(last.lastIndexOf('$') + 1);
        }
    }

    static class StackOverflow extends ErrorExplainer {
        @Override
        boolean handles(String type) {
            return type.equals("StackOverflowError");
        }

        @Override
        String describe(String type, String message) {
            return "A method called itself (or other methods) so many times that Java ran out of room to keep track. "
                    + "A recursion is probably missing its base case, or never reaches it.";
        }
    }

    static class ConcurrentModification extends ErrorExplainer {
        @Override
        boolean handles(String type) {
            return type.equals("ConcurrentModificationException");
        }

        @Override
        String describe(String type, String message) {
            return "A collection was changed (an add or a remove) while a for-each loop was going through it. "
                    + "Use an Iterator and its remove(), or collect the changes and make them after the loop.";
        }
    }

    static class OutOfMemory extends ErrorExplainer {
        @Override
        boolean handles(String type) {
            return type.equals("OutOfMemoryError");
        }

        @Override
        String describe(String type, String message) {
            return "The program used more memory than it's allowed (see Settings). It may be making new objects in an endless loop.";
        }
    }

    // Exceptions a program throws on purpose: Java's IllegalArgumentException and the like, and
    // the student's own exception classes.
    static class Thrown extends ErrorExplainer {
        @Override
        boolean handles(String type) {
            return type.equals("IllegalArgumentException") || type.equals("IllegalStateException")
                    || type.equals("UnsupportedOperationException");
        }

        @Override
        String describe(String type, String message) {
            return "The program threw this on purpose, to say something was wrong"
                    + (message == null ? "." : ": \"" + message + "\".");
        }
    }

    static class Fallback extends ErrorExplainer {
        @Override
        boolean handles(String type) {
            return true;
        }

        @Override
        String describe(String type, String message) {
            if (type.endsWith("Exception") || type.endsWith("Error")) {
                return message == null ? "The program threw a " + type + "."
                        : "The program threw a " + type + ": \"" + message + "\".";
            }
            return "The program threw " + type + (message == null ? "." : ": \"" + message + "\".");
        }
    }
}
