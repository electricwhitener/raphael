package raphael.explain;

import java.util.List;
import raphael.analysis.CodeFacts;
import raphael.model.FrameSnapshot;
import raphael.model.HeapObject;
import raphael.model.Names;
import raphael.model.Snapshot;
import raphael.model.Trace;

// Polymorphism: a variable declared as a parent type (or an interface) holds a child's object,
// and the child's version of the method runs. The declared type comes from the code (the call's
// CodeFacts), what it holds and which method ran come from the run.
// OOP: inheritance (a Concept, about polymorphism).
class PolymorphismConcept extends Concept {

    @Override
    protected boolean relevant(CodeFacts facts) {
        return !facts.superclasses().isEmpty() || !facts.interfaces().isEmpty();
    }

    @Override
    protected void find(Trace trace, CodeFacts facts, List<Moment> into) {
        int shown = 0;
        java.util.Set<String> pairs = new java.util.HashSet<>(); // declared type → object's type, once each
        for (int s = 1; s < trace.size(); s++) {
            Snapshot now = trace.get(s);
            if (now.getFrames().size() < 2 || now.getFrames().size() <= trace.get(s - 1).getFrames().size()) {
                continue; // only the first line of a new call
            }
            FrameSnapshot top = now.getFrames().get(0);
            FrameSnapshot caller = now.getFrames().get(1);
            HeapObject self = Phrases.self(top, now);
            if (self == null) {
                continue;
            }
            String holds = Names.simple(self.getTypeName());
            List<CodeFacts.CallFact> calls = facts.callsAt(caller.getFile(), caller.getLine());
            if (calls.stream().filter(c -> c.method().equals(top.getMethodName())).count() != 1) {
                continue; // two calls of the same name on one line: which one this is can't be told
            }
            for (CodeFacts.CallFact call : calls) {
                String declared = call.declaredType();
                if (!call.method().equals(top.getMethodName()) || declared == null || declared.equals(holds)) {
                    continue;
                }
                String ran = top.getClassName();
                String what = call.receiver() != null && call.receiver().matches("[\\w.\\[\\]]{1,30}") ? call.receiver() : "This variable";
                boolean first = shown == 0;
                boolean newPair = pairs.add(declared + ">" + holds);
                String text = what + " is declared " + declared + ", but the object in it is " + Phrases.article(holds) + ". "
                        + (ran.equals(holds) ? "So " + holds + "'s own " + call.method() + "() runs."
                        : holds + " has no " + call.method() + "() of its own, so the one it inherits from " + ran + " runs.")
                        + (first ? " Which method runs is decided by the object, while the program runs, not by the variable's type." : "");
                into.add(new Moment(s, "polymorphism", ran + "." + call.method() + "() runs", text, newPair && ++shown <= 3,
                        caller.getFile(), caller.getLine())); // each pair of types pauses once, up to three in a run
                break;
            }
        }
    }
}
