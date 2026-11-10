package raphael.explain;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import raphael.analysis.CodeFacts;
import raphael.model.Exit;
import raphael.model.FieldWrite;
import raphael.model.FrameSnapshot;
import raphael.model.HeapObject;
import raphael.model.ObjectNode;
import raphael.model.Snapshot;
import raphael.model.Trace;
import raphael.model.Value;

// Encapsulation: an object's private fields change only through its own methods. The moments:
// the first time a private field is written (and by which method), and a setter that refused a
// value: it was called, its check said no, and the field stayed as it was.
// OOP: inheritance (a Concept).
class EncapsulationConcept extends Concept {

    @Override
    protected boolean relevant(CodeFacts facts) {
        for (CodeFacts.FieldFact field : facts.fields()) {
            if (field.isPrivate() && !field.isStatic()) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void find(Trace trace, CodeFacts facts, List<Moment> into) {
        Set<String> explained = new HashSet<>();
        for (int s = 1; s < trace.size(); s++) {
            Snapshot now = trace.get(s);
            String file = now.getFrames().isEmpty() ? null : now.getFrames().get(0).getFile();
            for (FieldWrite write : now.getWrites()) {
                CodeFacts.FieldFact field = facts.field(write.className(), write.field());
                if (field != null && field.isPrivate() && !field.isStatic() && explained.add(write.className() + "." + write.field())) {
                    boolean constructor = write.method().equals(write.className());
                    into.add(new Moment(s, "private-write", write.field() + " is private",
                            write.field() + " is private: only " + write.className() + "'s own methods can change it. "
                                    + (constructor ? "Its constructor" : write.method() + "()") + " just set it to "
                                    + Phrases.value(write.value(), now) + ".",
                            true, file, write.line()));
                }
            }
            setterStarted(trace, s, facts, into);
        }
    }

    // A setter's call starts at this step: follow it to its return, and see whether it wrote its field.
    private void setterStarted(Trace trace, int start, CodeFacts facts, List<Moment> into) {
        Snapshot at = trace.get(start);
        Snapshot before = trace.get(start - 1);
        if (at.getFrames().size() <= before.getFrames().size()) {
            return; // not the first line of a new call
        }
        FrameSnapshot frame = at.getFrames().get(0);
        CodeFacts.SetterFact setter = facts.setter(frame.getClassName(), frame.getMethodName());
        CodeFacts.MethodFact method = facts.methodAt(frame.getFile(), frame.getLine());
        if (setter == null || setter.guardLine() < 0 || method == null || method.parameters().size() != 1) {
            return; // only a setter with a check can refuse
        }
        Value given = frame.getLocals().get(method.parameters().get(0));
        for (int s = start + 1; s < trace.size(); s++) {
            Snapshot step = trace.get(s);
            for (FieldWrite write : step.getWrites()) {
                if (write.field().equals(setter.field()) && write.method().equals(setter.method())) {
                    return; // it set the field: not refused
                }
            }
            for (Exit exit : step.getExits()) {
                if (exit.method().equals(setter.method()) && exit.className().equals(setter.className())) {
                    HeapObject self = Phrases.self(frame, step);
                    Value kept = self instanceof ObjectNode ? ((ObjectNode) self).getField(setter.field()) : null;
                    into.add(new Moment(s, "refused", setter.method() + " refused " + Phrases.value(given, at),
                            setter.method() + "(" + Phrases.value(given, at) + ") was refused by the check on line "
                                    + setter.guardLine() + ", so " + setter.field() + " stays "
                                    + (kept == null ? "as it was" : Phrases.value(kept, step))
                                    + ". The object keeps its own data safe: that's encapsulation.",
                            true, frame.getFile(), setter.guardLine()));
                    return;
                }
            }
        }
    }
}
