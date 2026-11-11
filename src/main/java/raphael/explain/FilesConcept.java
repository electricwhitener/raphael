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
                            (flushed ? "The text reaches " + name + " only now: " + (opensWithFopen(facts)
                                    ? "C keeps what's written to a FILE in a buffer, " : "a writer keeps what it's given in a buffer, ")
                                    + "and writes it to the file " + whatWrote(before, facts) + ". " : "")
                                    + "Added: " + shorten(added) + ".",
                            flushed, file, line));
                }
            }
            missingInC(before, now, facts, s, into);
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

    private static boolean opensWithFopen(CodeFacts facts) {
        return facts.calls().stream().anyMatch(call -> call.method().equals("fopen"));
    }

    private static final java.util.Set<String> CLOSERS = java.util.Set.of("close", "flush", "fclose", "fflush");

    // C: fopen gave NULL just now, so the file it was asked to read isn't in the folder. Java
    // throws instead (the missing-file moment above).
    private static void missingInC(Snapshot before, Snapshot now, CodeFacts facts, int s, List<Moment> into) {
        if (before.getFrames().isEmpty() || now.getFrames().isEmpty()) {
            return;
        }
        String file = before.getFrames().get(0).getFile();
        for (CodeFacts.CallFact call : facts.callsAt(file, before.getLine())) {
            if (!call.method().equals("fopen") || call.arguments().isEmpty()) {
                continue;
            }
            for (Map.Entry<String, raphael.model.Value> local : now.getFrames().get(0).getLocals().entrySet()) {
                raphael.model.Value was = before.getFrames().get(0).getLocals().get(local.getKey());
                if (local.getValue() instanceof raphael.model.NullValue && !(was instanceof raphael.model.NullValue)) {
                    String name = call.arguments().get(0);
                    into.add(new Moment(s, "file-missing", "No such file",
                            "fopen(" + name + ") gave NULL: " + name + " isn't in the program's folder, so " + local.getKey()
                                    + " points to no file. Check " + local.getKey() + " == NULL before reading from it.",
                            true, now.getFrames().get(0).getFile(), now.getLine()));
                    return;
                }
            }
        }
    }

    // Whether the line that just ran closed or flushed something (a writer's text then reaches the file).
    private static boolean closedOrFlushed(Snapshot before, CodeFacts facts) {
        String file = before.getFrames().isEmpty() ? null : before.getFrames().get(0).getFile();
        for (CodeFacts.CallFact call : facts.callsAt(file, before.getLine())) {
            if (CLOSERS.contains(call.method())) {
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

    // What made the writer's text reach the file: flush() or close() on the line that just ran,
    // or the end of a try-with-resources, which closes it.
    private static String whatWrote(Snapshot before, CodeFacts facts) {
        String file = before.getFrames().isEmpty() ? null : before.getFrames().get(0).getFile();
        for (CodeFacts.CallFact call : facts.callsAt(file, before.getLine())) {
            if (call.method().equals("flush") || call.method().equals("fflush")) {
                return "when it's flushed, as " + call.method() + "() on line " + before.getLine() + " just did";
            }
            if (call.method().equals("close") || call.method().equals("fclose")) {
                return "when it's closed, as " + call.method() + "() on line " + before.getLine() + " just did";
            }
        }
        for (CodeFacts.TryFact tried : facts.tries()) {
            if (Objects.equals(tried.file(), file) && tried.endLine() == before.getLine() + 1) {
                return "when it's closed, which the end of the try (with resources) just did";
            }
        }
        return "when it's closed, or its buffer fills";
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
