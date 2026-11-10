package raphael.explain;

import java.util.ArrayList;
import java.util.List;
import raphael.analysis.CodeFacts;
import raphael.model.Trace;

// One programming idea Raphael can explain in a run: recursion, exceptions, files,
// encapsulation, inheritance, polymorphism, static fields, or the author's own moments.
// Each subclass says when it applies (relevant) and finds its moments in the run (find).
// OOP: an abstract class with a Template Method: moments() is the same for every concept (ask
// whether it applies, then collect), and each subclass fills in the two steps; polymorphism: the
// finder holds a list of Concepts and never needs to know which one it's asking.
public abstract class Concept {

    // Whether the program uses this idea at all (from what the code says).
    protected abstract boolean relevant(CodeFacts facts);

    // Adds the moments of this idea in the run, in any order.
    protected abstract void find(Trace trace, CodeFacts facts, List<Moment> into);

    public final List<Moment> moments(Trace trace, CodeFacts facts) {
        List<Moment> found = new ArrayList<>();
        if (trace.size() > 0 && relevant(facts)) {
            find(trace, facts, found);
        }
        return found;
    }
}
