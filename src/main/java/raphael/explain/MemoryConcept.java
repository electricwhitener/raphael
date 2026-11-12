package raphael.explain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import raphael.analysis.CodeFacts;
import raphael.model.ArrayNode;
import raphael.model.FrameSnapshot;
import raphael.model.HeapObject;
import raphael.model.Names;
import raphael.model.ObjectNode;
import raphael.model.ReferenceValue;
import raphael.model.Snapshot;
import raphael.model.Trace;
import raphael.model.Value;

// C's memory, in three moments: malloc making a block (and what's in it: ? until it's set), free
// giving one back (and the pointers left dangling), and a function changing its caller's
// variable through a pointer to it (push(&head, …), swap(&x, &y)).
// OOP: inheritance (a Concept); it overrides relevant() and find().
public class MemoryConcept extends Concept {

    private static final Set<String> ALLOCATORS = Set.of("malloc", "calloc", "realloc", "strdup", "new"); // new: C++

    @Override
    protected boolean relevant(CodeFacts facts) {
        return true; // cheap to look; a Java run has no malloc, no freed memory and no &x values
    }

    @Override
    protected void find(Trace trace, CodeFacts facts, List<Moment> into) {
        Set<String> explained = new java.util.HashSet<>(); // functions whose change through a pointer has paused once
        for (int s = 1; s < trace.size(); s++) {
            Snapshot before = trace.get(s - 1);
            Snapshot now = trace.get(s);
            if (before.getFrames().isEmpty() || now.getFrames().isEmpty()) {
                continue;
            }
            String file = now.getFrames().get(0).getFile();
            String ranFile = before.getFrames().get(0).getFile();
            String allocator = null;
            String freedWith = null;
            for (CodeFacts.CallFact call : facts.callsAt(ranFile, before.getLine())) {
                allocator = allocator == null && ALLOCATORS.contains(call.method()) ? call.method() : allocator;
                if (call.method().equals("free") && freedWith == null) {
                    freedWith = "free(" + (call.arguments().isEmpty() ? "" : call.arguments().get(0)) + ")";
                }
                if (call.method().equals("delete") && freedWith == null) { // C++
                    freedWith = "delete" + (call.arguments().isEmpty() ? "" : " " + call.arguments().get(0));
                }
            }
            if (allocator != null) {
                allocated(before, now, allocator, s, file, into);
            }
            freed(before, now, freedWith, s, file, into);
            throughPointer(before, now, s, file, into, explained);
        }
    }

    // malloc (or calloc, realloc, strdup) on the line that just ran made a block.
    private static void allocated(Snapshot before, Snapshot now, String allocator, int s, String file, List<Moment> into) {
        for (HeapObject object : now.getHeap().values()) {
            if (before.getHeap().containsKey(object.getId()) || object.isFreed()) {
                continue;
            }
            String what;
            boolean unset;
            if (object instanceof ArrayNode) {
                ArrayNode array = (ArrayNode) object;
                unset = !array.getElements().isEmpty() && array.getElements().stream().allMatch(v -> "?".equals(v.display()));
                what = "a block of " + array.getLength() + " " + Names.simple(array.getElementType()) + (array.getLength() == 1 ? "" : "s");
            } else if (object instanceof ObjectNode) {
                ObjectNode node = (ObjectNode) object;
                unset = !node.getFields().isEmpty() && node.getFields().values().stream().allMatch(v -> "?".equals(v.display()));
                what = "a new " + Names.simple(node.getTypeName());
            } else {
                continue;
            }
            List<String> holders = holders(now, object.getId());
            String text = allocator + " made " + what + (holders.isEmpty() ? "" : ", and " + String.join(" and ", holders)
                    + (holders.size() == 1 ? " points" : " point") + " to it") + ".";
            if (allocator.equals("realloc")) {
                text += " realloc made a new block, copied the old one into it, and gave the old one back.";
            } else if (unset && now.getFrames().get(0).getMethodName().equals(Names.simple(object.getTypeName()))) {
                text += " Its fields aren't set yet, so they show ?; its constructor, running now, sets them.";
            } else if (unset) {
                text += " Its memory isn't set yet, so it shows ?: whatever was there before.";
            } else if (allocator.equals("calloc")) {
                text += " calloc sets every byte to 0.";
            }
            into.add(new Moment(s, "malloc", allocator.equals("new") ? "new made an object" : allocator + " made a block", text, true,
                    file, now.getLine()));
            return; // one block per line is the moment; the rest are in the picture
        }
    }

    // A block that free() gave back on the line that just ran.
    private static void freed(Snapshot before, Snapshot now, String freedWith, int s, String file, List<Moment> into) {
        for (HeapObject object : now.getHeap().values()) {
            HeapObject was = before.getHeap().get(object.getId());
            if (!object.isFreed() || (was != null && was.isFreed())) {
                continue;
            }
            List<String> holders = holders(now, object.getId());
            String what = object instanceof ArrayNode
                    ? "the block of " + ((ArrayNode) object).getLength() + " " + Names.simple(((ArrayNode) object).getElementType()) + "s"
                    : "the " + Names.simple(object.getTypeName());
            boolean cpp = freedWith != null && freedWith.startsWith("delete");
            into.add(new Moment(s, "free", cpp ? "delete gave it back" : "free gave it back",
                    (freedWith == null ? "free()" : freedWith) + " gave " + what + " back: it isn't the program's memory any more."
                            + (holders.isEmpty() ? "" : " " + String.join(" and ", holders) + (holders.size() == 1 ? " still holds" : " still hold")
                            + " its address: " + (holders.size() == 1 ? "a dangling pointer" : "dangling pointers")
                            + ". Using it now is a bug; set it to " + (cpp ? "nullptr" : "NULL") + "."),
                    true, file, now.getLine()));
            return;
        }
    }

    // A caller's variable changed while a function holding its address ran a line: *head_ref = n
    // changed main's head.
    // Only a function's first such moment pauses: swap(&x, &y) changes x, then y, which is one idea.
    private static void throughPointer(Snapshot before, Snapshot now, int s, String file, List<Moment> into, Set<String> explained) {
        List<FrameSnapshot> was = before.getFrames();
        List<FrameSnapshot> is = now.getFrames();
        if (was.size() < 2 || is.size() != was.size() || !was.get(0).getMethodName().equals(is.get(0).getMethodName())) {
            return; // the same call has to have run the line
        }
        FrameSnapshot running = was.get(0);
        for (int f = 1; f < was.size(); f++) {
            FrameSnapshot callerWas = was.get(f);
            FrameSnapshot callerIs = is.get(f);
            for (Map.Entry<String, Value> variable : callerIs.getLocals().entrySet()) {
                Value old = callerWas.getLocals().get(variable.getKey());
                if (old == null || Objects.equals(old, variable.getValue())) {
                    continue;
                }
                for (Map.Entry<String, Value> pointer : running.getLocals().entrySet()) {
                    if (pointer.getValue().display().equals("&" + variable.getKey())) {
                        into.add(new Moment(s, "through-pointer", "Changed through a pointer",
                                running.getMethodName() + "() changed " + callerIs.getMethodName() + "()'s " + variable.getKey()
                                        + " through " + pointer.getKey() + ", which holds &" + variable.getKey()
                                        + ". That's how a C function changes its caller's variables: it's given their addresses.",
                                explained.add(running.getMethodName()), file, now.getLine()));
                        return;
                    }
                }
            }
        }
    }

    // What points to this object: variables in any running function, and the fields of the
    // structs they point to (head->next).
    private static List<String> holders(Snapshot snapshot, long id) {
        List<String> names = new ArrayList<>();
        for (FrameSnapshot frame : snapshot.getFrames()) {
            for (Map.Entry<String, Value> local : frame.getLocals().entrySet()) {
                if (pointsTo(local.getValue(), id) && !names.contains(local.getKey())) {
                    names.add(local.getKey());
                }
            }
        }
        for (FrameSnapshot frame : snapshot.getFrames()) {
            for (Map.Entry<String, Value> local : frame.getLocals().entrySet()) {
                HeapObject object = local.getValue() instanceof ReferenceValue
                        ? snapshot.getObject(((ReferenceValue) local.getValue()).id()) : null;
                if (object instanceof ObjectNode && object.getId() != id) {
                    for (Map.Entry<String, Value> field : ((ObjectNode) object).getFields().entrySet()) {
                        String name = local.getKey() + "->" + field.getKey();
                        if (pointsTo(field.getValue(), id) && !names.contains(name)) {
                            names.add(name);
                        }
                    }
                }
            }
        }
        return names;
    }

    private static boolean pointsTo(Value value, long id) {
        return value instanceof ReferenceValue && ((ReferenceValue) value).id() == id;
    }
}
