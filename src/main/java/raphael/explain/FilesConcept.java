package raphael.explain;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import raphael.analysis.CodeFacts;
import raphael.model.Snapshot;
import raphael.model.ThrownException;
import raphael.model.Trace;

// Files: a file created in the program's folder, text written to it, text that only appears
// when the writer is closed (a buffer), and a file that isn't there.
// OOP: inheritance (a Concept).
class FilesConcept extends Concept {

    @Override
    protected boolean relevant(CodeFacts facts) {
        return facts.usesFiles();
    }

    @Override
    protected void find(Trace trace, CodeFacts facts, List<Moment> into) {
        java.util.Map<String, Integer> createdAt = new java.util.HashMap<>();
        for (int s = 1; s < trace.size(); s++) {
            Snapshot before = trace.get(s - 1);
            Snapshot now = trace.get(s);
            String file = now.getFrames().isEmpty() ? null : now.getFrames().get(0).getFile();
            int line = now.getLine();
            Map<String, String> was = before.getFiles();
            for (Map.Entry<String, String> f : now.getFiles().entrySet()) {
                String name = f.getKey();
                String text = f.getValue();
                if (!was.containsKey(name)) {
                    createdAt.put(name, s);
                    into.add(new Moment(s, "file-created", name + " created",
                            name + " is now in the program's folder" + (text.isEmpty() ? ", still empty." : ", with " + lines(text) + "."),
                            true, file, line));
                } else if (!Objects.equals(was.get(name), text) && text.length() > was.get(name).length()) {
                    String added = text.substring(was.get(name).length()).strip();
                    // Empty since it was made, and the text comes only now, steps later: the writer
                    // held it in its buffer until it was closed (or flushed, or the buffer filled).
                    boolean flushed = was.get(name).isEmpty() && (s - createdAt.getOrDefault(name, s) > 2 || closedOrFlushed(before, facts));
                    into.add(new Moment(s, "file-written", "Written to " + name,
                            (flushed ? "The text reaches " + name + " only now: a writer keeps what it's given in a buffer, "
                                    + "and writes it to the file when it's closed or flushed. " : "")
                                    + "Added: " + shorten(added) + ".",
                            flushed, file, line));
                }
            }
            ThrownException thrown = now.getThrown();
            if (thrown != null && ExceptionConcept.MISSING_FILE.contains(thrown.type())) {
                into.add(new Moment(s, "file-missing", "No such file",
                        "The program tried to open " + (thrown.message() == null ? "a file" : quoted(thrown.message()))
                                + ", but it isn't in the program's folder, so Java threw " + Phrases.article(thrown.type())
                                + (thrown.throwLine() > 0 ? " at line " + thrown.throwLine() : "")
                                + ". The catch at line " + thrown.catchLine() + " deals with it.",
                        true, file, thrown.catchLine()));
            }
        }
    }

    // Whether the line that just ran closed or flushed something (a writer's text then reaches the file).
    private static boolean closedOrFlushed(Snapshot before, CodeFacts facts) {
        String file = before.getFrames().isEmpty() ? null : before.getFrames().get(0).getFile();
        for (CodeFacts.CallFact call : facts.callsAt(file, before.getLine())) {
            if (call.method().equals("close") || call.method().equals("flush")) {
                return true;
            }
        }
        for (CodeFacts.TryFact tried : facts.tries()) { // the end of a try-with-resources closes it too
            if (Objects.equals(tried.file(), file) && tried.endLine() == before.getLine() + 1) {
                return true;
            }
        }
        return false;
    }

    // FileNotFoundException's message is "name (No such file or directory)": just the name.
    private static String quoted(String message) {
        return "\"" + message.replaceAll("\\s*\\(.*\\)$", "") + "\"";
    }

    private static String lines(String text) {
        long count = text.lines().count();
        return count + (count == 1 ? " line" : " lines");
    }

    private static String shorten(String text) {
        String one = text.replace("\n", " / ");
        return "\"" + (one.length() > 60 ? one.substring(0, 57) + "…" : one) + "\"";
    }
}
