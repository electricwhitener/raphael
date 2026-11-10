package raphael.explain;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import raphael.analysis.CodeFacts;
import raphael.model.ArrayNode;
import raphael.model.FrameSnapshot;
import raphael.model.HeapObject;
import raphael.model.ObjectNode;
import raphael.model.ReferenceValue;
import raphael.model.Snapshot;
import raphael.model.Trace;
import raphael.model.Value;

// The author's own key moments: a "// moment: text" comment on a line becomes a moment each
// time that line is about to run. {name}, {node.val} and {a[i]} in the text are filled in from the
// step's values, read from the recorded step (nothing in the program is run to get them).
// The examples use these for the steps of an algorithm that no general rule can name
// ("case 2: the node has one child"); students can write them in their own code too.
// OOP: inheritance (a Concept).
class AuthorMoments extends Concept {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z_]\\w*(?:\\.[A-Za-z_]\\w*|\\[[A-Za-z_0-9]+\\])*)\\}");
    private static final Pattern PART = Pattern.compile("\\.([A-Za-z_]\\w*)|\\[([A-Za-z_0-9]+)\\]");

    @Override
    protected boolean relevant(CodeFacts facts) {
        return !facts.momentComments().isEmpty();
    }

    @Override
    protected void find(Trace trace, CodeFacts facts, List<Moment> into) {
        for (int s = 0; s < trace.size(); s++) {
            Snapshot step = trace.get(s);
            if (step.getFrames().isEmpty()) {
                continue;
            }
            FrameSnapshot top = step.getFrames().get(0);
            CodeFacts.MomentComment comment = facts.momentAt(top.getFile(), top.getLine());
            if (comment == null) {
                continue;
            }
            String text = fill(comment.text(), top, step);
            String title = "Note on line " + top.getLine(); // the sentence itself is the text, so the card doesn't say it twice
            into.add(new Moment(s, "author", title, text, true, top.getFile(), top.getLine())); // as the author wrote it
        }
    }

    // The text with each {placeholder} replaced by its value at this step, or left as written if it has none.
    static String fill(String text, FrameSnapshot frame, Snapshot step) {
        Matcher m = PLACEHOLDER.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            Value value = resolve(m.group(1), frame, step);
            m.appendReplacement(out, Matcher.quoteReplacement(value == null ? m.group() : Phrases.value(value, step)));
        }
        m.appendTail(out);
        return out.toString();
    }

    // "node.next.val" or "a[i]" read from the frame's variables and the heap; null if any part is missing.
    private static Value resolve(String path, FrameSnapshot frame, Snapshot step) {
        String first = path.split("[.\\[]", 2)[0];
        Value value = frame.getLocals().get(first);
        if (value == null) {
            HeapObject self = Phrases.self(frame, step); // a field of this, written without "this."
            value = self instanceof ObjectNode ? ((ObjectNode) self).getField(first) : null;
        }
        Matcher part = PART.matcher(path.substring(first.length()));
        while (value != null && part.find()) {
            if (!(value instanceof ReferenceValue)) {
                return null;
            }
            HeapObject object = step.getObject(((ReferenceValue) value).id());
            if (part.group(1) != null) {
                value = object instanceof ObjectNode ? ((ObjectNode) object).getField(part.group(1)) : null;
            } else {
                Integer index = indexOf(part.group(2), frame);
                value = object instanceof ArrayNode && index != null && index >= 0
                        && index < ((ArrayNode) object).getElements().size() ? ((ArrayNode) object).getElements().get(index) : null;
            }
        }
        return value;
    }

    private static Integer indexOf(String index, FrameSnapshot frame) {
        try {
            return Integer.parseInt(index);
        } catch (NumberFormatException e) {
            Value value = frame.getLocals().get(index);
            try {
                return value == null ? null : Integer.parseInt(value.display());
            } catch (NumberFormatException notANumber) {
                return null;
            }
        }
    }
}
