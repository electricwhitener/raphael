package raphael.explain;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import raphael.model.ArrayNode;
import raphael.model.FrameSnapshot;
import raphael.model.HeapObject;
import raphael.model.NullValue;
import raphael.model.ObjectNode;
import raphael.model.PrimitiveValue;
import raphael.model.ReferenceValue;
import raphael.model.Snapshot;
import raphael.model.Value;

// What went wrong when a C program crashed, in one sentence, found from the crash step's values
// and the line's text: "head was NULL when the line used head->val", "second points to
// memory that free() gave back", "a has 5 cells, numbered 0 to 4, so a[7] is outside it". C itself only says "Segmentation
// fault"; Java's tracer gets such sentences from Java's own messages.
// OOP: a utility class (static methods, private constructor).
public final class CCrash {

    private static final String NAME = "[A-Za-z_]\\w*";
    // a->b->c (the last part is what was read), *p, a[i]
    private static final Pattern ARROWS = Pattern.compile("\\b(" + NAME + ")((?:->" + NAME + ")+)");
    private static final Pattern STAR = Pattern.compile("\\*\\s*(" + NAME + ")\\b(?!\\s*\\()");
    private static final Pattern INDEX = Pattern.compile("\\b(" + NAME + ")\\s*\\[\\s*(" + NAME + "|\\d+)\\s*\\]");
    private static final Pattern GLIBC_ASSERT = Pattern.compile("Assertion `(.+)' failed");
    private static final Pattern MAC_ASSERT = Pattern.compile("Assertion failed: \\((.+)\\), function \\w+, file ");
    private static final Pattern MINGW_ASSERT = Pattern.compile("Assertion failed: (.+), file ");

    private CCrash() {
    }

    // The crash's detail, or null if nothing on the line explains it. signal: "SIGSEGV" and so on;
    // line: the text of the line that crashed; output: what the program printed.
    public static String detail(String signal, Snapshot step, String line, String output) {
        if ("SIGABRT".equals(signal)) {
            return aborted(output);
        }
        if (step == null || step.getFrames().isEmpty() || line == null) {
            return null;
        }
        String code = line.replaceAll("//.*$", "").replaceAll("\"(\\\\.|[^\"\\\\])*\"", "\"\"");
        int number = step.getLine();
        if ("SIGFPE".equals(signal)) {
            Matcher division = Pattern.compile("[/%]\\s*(" + NAME + ")").matcher(code);
            while (division.find()) {
                if ("0".equals(plain(step, division.group(1)))) {
                    return division.group(1) + " was 0 when the line divided by it";
                }
            }
            return null;
        }
        Matcher arrows = ARROWS.matcher(code);
        while (arrows.find()) {
            String found = followArrows(step, arrows.group(1), arrows.group(2), number);
            if (found != null) {
                return found;
            }
        }
        Matcher star = STAR.matcher(code);
        while (star.find()) {
            String found = problem(step, star.group(1), value(step, star.group(1)), "*" + star.group(1), number);
            if (found != null) {
                return found;
            }
        }
        Matcher index = INDEX.matcher(code);
        while (index.find()) {
            Value array = value(step, index.group(1));
            String used = index.group(1) + "[" + index.group(2) + "]";
            String found = problem(step, index.group(1), array, used, number);
            if (found != null) {
                return found;
            }
            Integer at = index.group(2).matches("\\d+") ? Integer.valueOf(index.group(2)) : number(plain(step, index.group(2)));
            HeapObject cells = array instanceof ReferenceValue ? step.getObject(((ReferenceValue) array).id()) : null;
            if (at != null && cells instanceof ArrayNode && (at < 0 || at >= ((ArrayNode) cells).getLength())) {
                int length = ((ArrayNode) cells).getLength();
                return index.group(1) + " has " + length + (length == 1 ? " cell" : " cells") + ", numbered 0 to " + (length - 1)
                        + ", so " + index.group(1) + "[" + at + "] is outside it";
            }
        }
        return null;
    }

    // a->b->c: the first part of the chain that was NULL, freed or never set.
    private static String followArrows(Snapshot step, String start, String chain, int line) {
        String[] fields = chain.substring(2).split("->");
        String path = start;
        Value value = value(step, start);
        String read = start + chain;
        for (String field : fields) {
            String found = problem(step, path, value, read, line);
            if (found != null) {
                return found;
            }
            HeapObject object = value instanceof ReferenceValue ? step.getObject(((ReferenceValue) value).id()) : null;
            if (!(object instanceof ObjectNode)) {
                return null;
            }
            value = ((ObjectNode) object).getField(field);
            path = path + "->" + field;
        }
        return null;
    }

    // What's wrong with using this pointer, or null if nothing is.
    private static String problem(Snapshot step, String name, Value value, String used, int line) {
        if (value instanceof NullValue) {
            return name + " was NULL when the line used " + used;
        }
        if (value instanceof PrimitiveValue && "?".equals(value.display())) {
            return name + " was never set (it holds whatever was in memory) when the line used " + used;
        }
        if (value instanceof ReferenceValue) {
            HeapObject object = step.getObject(((ReferenceValue) value).id());
            if (object != null && object.isFreed()) {
                return name + " points to memory that free() gave back, and the line used " + used;
            }
        }
        return null;
    }

    private static String aborted(String output) {
        if (output == null) {
            return null;
        }
        for (Pattern format : List.of(GLIBC_ASSERT, MAC_ASSERT, MINGW_ASSERT)) { // Linux's, macOS's and Windows' wording
            Matcher assertion = format.matcher(output);
            if (assertion.find()) {
                return "assert(" + assertion.group(1) + ") failed";
            }
        }
        if (output.contains("double free")) {
            return "free() was called twice on the same block";
        }
        if (output.contains("free(): invalid pointer") || output.contains("munmap_chunk(): invalid pointer")) {
            return "free() was given a pointer that malloc didn't return";
        }
        return null;
    }

    // A variable of the running function, or a global ("main.count" in statics).
    private static Value value(Snapshot step, String name) {
        FrameSnapshot top = step.getFrames().get(0);
        Value local = top.getLocals().get(name);
        if (local != null) {
            return local;
        }
        for (Map.Entry<String, Value> global : step.getStatics().entrySet()) {
            if (global.getKey().endsWith("." + name)) {
                return global.getValue();
            }
        }
        return null;
    }

    private static String plain(Snapshot step, String name) {
        Value value = value(step, name);
        return value instanceof PrimitiveValue ? value.display() : null;
    }

    private static Integer number(String text) {
        try {
            return text == null ? null : Integer.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
