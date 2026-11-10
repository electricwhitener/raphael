package raphael.explain;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import raphael.analysis.CodeFacts;
import raphael.model.FieldWrite;
import raphael.model.Snapshot;
import raphael.model.Trace;

// Static fields: one copy for the whole class, shared by every object, unlike an instance field.
// OOP: inheritance (a Concept).
class StaticConcept extends Concept {

    @Override
    protected boolean relevant(CodeFacts facts) {
        for (CodeFacts.FieldFact field : facts.fields()) {
            if (field.isStatic()) {
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
            for (FieldWrite write : now.getWrites()) {
                CodeFacts.FieldFact field = facts.field(write.className(), write.field());
                if (write.objectId() < 0 && field != null && field.isStatic() && !write.method().equals("static init")
                        && !write.method().equals("<clinit>") && explained.add(write.className() + "." + write.field())) {
                    String file = now.getFrames().isEmpty() ? null : now.getFrames().get(0).getFile();
                    into.add(new Moment(s, "static", write.field() + " is static",
                            write.field() + " is static: there is one copy for the whole " + write.className()
                                    + " class, shared by every object, and it's now " + Phrases.value(write.value(), now) + ".",
                            true, file, write.line()));
                }
            }
        }
    }
}
