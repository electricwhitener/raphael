package raphael.explain;

import java.util.List;
import raphael.analysis.CodeFacts;
import raphael.model.Trace;

// Typed input, read with the classic surprise: a read that takes only the end of a line. In C,
// scanf("%c") after a scanf("%d") reads the newline the %d left behind; in Java, nextLine() after
// nextInt() reads an empty line for the same reason. Both are explained when they happen.
// OOP: inheritance (a Concept); it overrides relevant() and find(). It holds the run's typed
// input, which the other concepts don't need (composition: it uses Reads to follow it).
public class InputConcept extends Concept {

    private final String input;

    public InputConcept(String input) {
        this.input = input == null ? "" : input.replace("\r\n", "\n");
    }

    @Override
    protected boolean relevant(CodeFacts facts) {
        return facts.readsInput() && !input.isEmpty();
    }

    @Override
    protected void find(Trace trace, CodeFacts facts, List<Moment> into) {
        Reads reads = new Reads(trace, facts, input);
        for (int s = 1; s < trace.size(); s++) {
            for (Reads.Spot spot : reads.justRead(s)) {
                if (!spot.source().equals(Reads.INPUT) || !input.substring(spot.from(), spot.to()).equals("\n")) {
                    continue;
                }
                String who = spot.into() == null ? "the read" : spot.into();
                int line = trace.get(s - 1).getLine();
                String file = trace.get(s - 1).getFrames().isEmpty() ? null : trace.get(s - 1).getFrames().get(0).getFile();
                boolean c = facts.calls().stream().anyMatch(call -> call.method().equals("scanf"));
                into.add(new Moment(s, "read-newline", "Read a line end",
                        c ? who + " got the newline (Enter) that the scanf before it left in the input, not the next "
                                + "character typed. A space before %c in the format (\" %c\") skips it."
                          : who + " got an empty line: the read before it took only the number and left the line's end "
                                + "behind, which this read took. Read that line end first with an extra nextLine().",
                        true, file, line));
            }
        }
    }
}
