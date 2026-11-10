package raphael.explain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import raphael.model.FrameSnapshot;
import raphael.model.HeapObject;
import raphael.model.Names;
import raphael.model.NullValue;
import raphael.model.ReferenceValue;
import raphael.model.Snapshot;
import raphael.model.Value;

// The words of the key moments, in one place: how a value, a call or a list of names is written,
// and how an expression is shown with its real values put in ("4 * factorial(3)" → "4 * 6").
// Every sentence is built from what the run really did, so it's never a guess.
// OOP: a utility class (static methods, private constructor).
public final class Phrases {

    private Phrases() {
    }

    // A value as the student would write it: 3, "Ann", 'x', null, or an object by its class ("a Circle").
    public static String value(Value value, Snapshot at) {
        if (value == null || value instanceof NullValue) {
            return "null";
        }
        if (value instanceof ReferenceValue) {
            HeapObject object = at == null ? null : at.getObject(((ReferenceValue) value).id());
            return object == null ? "an object" : article(Names.simple(object.getTypeName()));
        }
        return value.display();
    }

    // "a Circle", "an Account".
    public static String article(String noun) {
        return ("AEIOUaeiou".indexOf(noun.isEmpty() ? 'x' : noun.charAt(0)) >= 0 ? "an " : "a ") + noun;
    }

    // A running call with its arguments: "factorial(n = 3)". this is left out.
    public static String call(FrameSnapshot frame, List<String> parameters, Snapshot at) {
        List<String> parts = new ArrayList<>();
        for (String name : parameters) {
            Value value = frame.getLocals().get(name);
            if (value != null) {
                parts.add(name + " = " + value(value, at));
            }
        }
        return frame.getMethodName() + "(" + String.join(", ", parts) + ")";
    }

    // "a", "a and b", "a, b and c".
    public static String list(List<String> items) {
        if (items.size() <= 1) {
            return items.isEmpty() ? "" : items.get(0);
        }
        return String.join(", ", items.subList(0, items.size() - 1)) + " and " + items.get(items.size() - 1);
    }

    // An expression with the frame's simple values put in for its variables, and a call to the
    // method replaced by what that call returned: "n * factorial(n - 1)" with n = 4 and the call
    // returning 6 becomes "4 * 6". Null if it can't be shown honestly (an object, say).
    public static String withValues(String expression, String method, String returned, Map<String, Value> locals) {
        String shown = replaceCall(expression, method, returned);
        if (shown == null) {
            return null;
        }
        Matcher names = Pattern.compile("\\b[A-Za-z_]\\w*\\b").matcher(shown);
        StringBuilder out = new StringBuilder();
        while (names.find()) {
            Value value = locals.get(names.group());
            boolean simple = value != null && !(value instanceof ReferenceValue) && !(value instanceof NullValue);
            names.appendReplacement(out, Matcher.quoteReplacement(simple ? value.display() : names.group()));
        }
        names.appendTail(out);
        return out.toString();
    }

    // The first call to method(...) in an expression, with its brackets matched, replaced by a value.
    private static String replaceCall(String expression, String method, String returned) {
        Matcher m = Pattern.compile("\\b" + Pattern.quote(method) + "\\s*\\(").matcher(expression);
        if (!m.find()) {
            return expression;
        }
        int depth = 0;
        for (int i = m.end() - 1; i < expression.length(); i++) {
            char c = expression.charAt(i);
            depth += c == '(' ? 1 : c == ')' ? -1 : 0;
            if (depth == 0) {
                return expression.substring(0, m.start()) + returned + expression.substring(i + 1);
            }
        }
        return null;
    }

    // The object a frame runs on (its this), or null for a static method.
    public static HeapObject self(FrameSnapshot frame, Snapshot at) {
        Value self = frame.getLocals().get("this");
        return self instanceof ReferenceValue ? at.getObject(((ReferenceValue) self).id()) : null;
    }
}
