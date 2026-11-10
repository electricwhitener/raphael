package raphael.explain;

import java.util.List;
import java.util.Set;
import raphael.analysis.CodeFacts;
import raphael.model.Snapshot;
import raphael.model.ThrownException;
import raphael.model.Trace;

// Exceptions: what was thrown, where, which methods it left, and which catch took it and why
// ("catch (Exception e) takes it because IllegalArgumentException is a kind of Exception").
// One that nothing caught ends the run, and gets its own moment. A missing file is FilesConcept's.
// OOP: inheritance (a Concept).
class ExceptionConcept extends Concept {

    // Exceptions about files that aren't there, which FilesConcept explains instead.
    static final Set<String> MISSING_FILE = Set.of("FileNotFoundException", "NoSuchFileException");

    @Override
    protected boolean relevant(CodeFacts facts) {
        return true; // any program can throw
    }

    @Override
    protected void find(Trace trace, CodeFacts facts, List<Moment> into) {
        for (int s = 0; s < trace.size(); s++) {
            Snapshot step = trace.get(s);
            String file = step.getFrames().isEmpty() ? null : step.getFrames().get(0).getFile();
            ThrownException thrown = step.getThrown();
            if (thrown != null && !(facts.usesFiles() && MISSING_FILE.contains(thrown.type()))) {
                into.add(new Moment(s, "caught", thrown.type() + " caught", caught(thrown, facts), true, file, thrown.catchLine()));
            }
            if (step.getException() != null) {
                String type = step.getException().replaceAll(":.*", "");
                into.add(new Moment(s, "crash", type + " not caught",
                        "Nothing caught the " + type + " at line " + step.getLine() + ", so the program stopped here."
                                + (facts.tries().isEmpty() ? " A try and catch around the line would let it carry on." : ""),
                        true, file, step.getLine()));
            }
        }
    }

    private static String caught(ThrownException thrown, CodeFacts facts) {
        StringBuilder text = new StringBuilder(Phrases.article(thrown.type()));
        if (thrown.message() != null && !thrown.message().isBlank()) {
            text.append(" (\"").append(thrown.message()).append("\")");
        }
        text.append(" was thrown").append(thrown.throwLine() > 0 ? " at line " + thrown.throwLine() : "");
        if (!thrown.leftMethods().isEmpty()) {
            text.append(". It left ").append(Phrases.list(thrown.leftMethods().stream().map(m -> m + "()").toList()))
                    .append(" in the middle, skipping the rest of ").append(thrown.leftMethods().size() == 1 ? "it" : "them");
        }
        CodeFacts.TryFact tried = facts.tryCatchingAt(thrown.catchFile(), thrown.catchLine());
        String by = tried == null ? null : catchType(tried, thrown.catchLine(), thrown.type());
        if (by == null) {
            text.append(", and was caught at line ").append(thrown.catchLine()).append('.');
        } else if (by.equals(thrown.type())) {
            text.append(", and catch (").append(by).append(" …) at line ").append(thrown.catchLine())
                    .append(" takes it: the types match.");
        } else {
            text.append(", and catch (").append(by).append(" …) at line ").append(thrown.catchLine())
                    .append(" takes it, because ").append(Phrases.article(thrown.type())).append(" is a kind of ").append(by).append('.');
        }
        if (tried != null && tried.finallyLine() > 0) {
            text.append(" The finally at line ").append(tried.finallyLine()).append(" runs next, as it always does.");
        }
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    // The type named by the catch that took it: the thrown type itself if that catch names it,
    // otherwise the one catch at that line (which must name a parent of it, since it caught it).
    private static String catchType(CodeFacts.TryFact tried, int catchLine, String thrownType) {
        for (CodeFacts.CatchFact c : tried.catches()) {
            if (c.line() == catchLine || c.line() == catchLine - 1) {
                return c.types().contains(thrownType) ? thrownType : c.types().size() == 1 ? c.types().get(0) : null;
            }
        }
        return null;
    }
}
