package raphael.explain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import raphael.analysis.CodeFacts;
import raphael.model.Trace;

// Finds every key moment of a run: asks each concept, puts the moments in step order, and keeps
// the pauses from becoming noise: a moment of one kind on one line pauses at most twice (the
// third base case of Fibonacci is only noted).
// OOP: composition (it holds the list of concepts) and polymorphism (each is just a Concept).
public class MomentFinder {

    static final int PAUSES_PER_LINE = 2;

    private final List<Concept> concepts = List.of(
            new AuthorMoments(),
            new RecursionConcept(),
            new ExceptionConcept(),
            new FilesConcept(),
            new EncapsulationConcept(),
            new InheritanceConcept(),
            new PolymorphismConcept(),
            new StaticConcept(),
            new MemoryConcept());

    public List<Moment> find(Trace trace, CodeFacts facts) {
        return find(trace, facts, "");
    }

    // input: what was typed into the program's stdin box, for the moments about reading it.
    public List<Moment> find(Trace trace, CodeFacts facts, String input) {
        List<Moment> all = new ArrayList<>();
        for (Concept concept : concepts) {
            all.addAll(concept.moments(trace, facts));
        }
        all.addAll(new InputConcept(input).moments(trace, facts));
        all.sort(Comparator.comparingInt(Moment::step)); // stable: on one step, the concepts' order
        Map<String, Integer> paused = new HashMap<>();
        List<Moment> kept = new ArrayList<>();
        for (Moment moment : all) {
            if (moment.pauses()) {
                String where = moment.kind() + "@" + moment.file() + ":" + moment.line();
                int times = paused.merge(where, 1, Integer::sum);
                kept.add(times > PAUSES_PER_LINE ? moment.noted() : moment);
            } else {
                kept.add(moment);
            }
        }
        return kept;
    }
}
