package raphael.explain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import raphael.analysis.CodeFacts;
import raphael.model.FrameSnapshot;
import raphael.model.HeapObject;
import raphael.model.Names;
import raphael.model.Snapshot;
import raphael.model.Trace;

// Inheritance: an object of a child class is also one of its parent class, with the parent's
// fields too; and a child's constructor runs its parent's first (super).
// OOP: inheritance (a Concept, about inheritance).
class InheritanceConcept extends Concept {

    @Override
    protected boolean relevant(CodeFacts facts) {
        return !facts.superclasses().isEmpty();
    }

    @Override
    protected void find(Trace trace, CodeFacts facts, List<Moment> into) {
        Set<String> shown = new HashSet<>();
        Set<String> chains = new HashSet<>();
        int kinds = 0; // the first two "is a" moments pause; the rest are noted
        for (int s = 0; s < trace.size(); s++) {
            Snapshot now = trace.get(s);
            String file = now.getFrames().isEmpty() ? null : now.getFrames().get(0).getFile();
            for (HeapObject object : now.getHeap().values()) {
                String type = Names.simple(object.getTypeName());
                String parent = facts.superclasses().get(type);
                if (parent != null && shown.add(type)) {
                    String inherited = fieldsOf(parent, facts);
                    String own = fieldsOf(type, facts);
                    String text = Phrases.article(type) + " is also " + Phrases.article(parent) + ": "
                            + (inherited.isEmpty() ? "it can be used wherever " + Phrases.article(parent) + " is wanted"
                            + (own.isEmpty() ? "" : ", and it has its own fields" + own)
                            : "it has " + parent + "'s fields" + inherited + (own.isEmpty() ? "" : " as well as its own" + own)) + ".";
                    boolean ownParent = !fieldsOf(parent, facts).isEmpty() || facts.superclasses().containsKey(parent)
                            || facts.interfaces().containsKey(parent); // a parent class of the program's own
                    into.add(new Moment(s, "is-a", Phrases.article(type) + " is " + Phrases.article(parent),
                            Character.toUpperCase(text.charAt(0)) + text.substring(1), ownParent && ++kinds <= 2, file, now.getLine()));
                }
            }
            if (now.getFrames().size() >= 2) {
                FrameSnapshot top = now.getFrames().get(0);
                FrameSnapshot below = now.getFrames().get(1);
                String child = below.getMethodName();
                if (top.getMethodName().equals(facts.superclasses().get(child)) && below.getClassName().equals(child)
                        && chains.add(child)) {
                    into.add(new Moment(s, "super", child + " runs " + top.getMethodName() + "'s constructor first",
                            child + "'s constructor first runs " + top.getMethodName() + "'s (super), so the "
                                    + top.getMethodName() + " part of the object is set up before the " + child + " part.",
                            true, top.getFile(), top.getLine()));
                }
            }
        }
    }

    // " (name, age)", or nothing if the class has no fields of its own.
    private static String fieldsOf(String type, CodeFacts facts) {
        List<String> names = new ArrayList<>();
        for (CodeFacts.FieldFact field : facts.fields()) {
            if (field.className().equals(type) && !field.isStatic()) {
                names.add(field.name());
            }
        }
        return names.isEmpty() ? "" : " (" + Phrases.list(names) + ")";
    }
}
