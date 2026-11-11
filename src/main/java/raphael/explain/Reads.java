package raphael.explain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import raphael.analysis.CodeFacts;
import raphael.model.FrameSnapshot;
import raphael.model.PrimitiveValue;
import raphael.model.Snapshot;
import raphael.model.Trace;
import raphael.model.Value;

// How far the program had read its input (the stdin text) and each file, at every step, and
// what it read just before that step. Worked out the way a Scanner reads: nextInt() and next()
// skip spaces and newlines, then take one word; nextLine() and readLine() take the rest of the
// line (which can be nothing, right after a nextInt()). For the keyboard, each such call on the
// line that just ran reads once; for a file, the value that landed in a variable says which file.
// OOP: a class that hides the working out; Spot is a tiny record of what was read.
public class Reads {

    public static final String INPUT = "stdin"; // the source name for the keyboard

    private static final Set<String> WORD_READS = Set.of("next", "nextInt", "nextDouble", "nextLong", "nextBoolean",
            "nextFloat", "nextShort", "nextByte");
    private static final Set<String> LINE_READS = Set.of("nextLine", "readLine");

    // One read: in this source, the text from character `from` up to `to`, which went into `into`
    // (a variable's name, or null when it wasn't kept in a variable that changed).
    public record Spot(String source, int from, int to, String into) {
    }

    private final List<Map<String, Integer>> read = new ArrayList<>();  // per step: source → characters read
    private final List<List<Spot>> justRead = new ArrayList<>();       // per step: the reads since the step before
    private final List<Map<String, Spot>> last = new ArrayList<>();    // per step: source → the latest read from it

    public Reads(Trace trace, CodeFacts facts, String input) {
        Map<String, Integer> at = new HashMap<>();
        Map<String, Spot> latest = new HashMap<>();
        String typed = input == null ? "" : input.replace("\r\n", "\n");
        for (int s = 0; s < trace.size(); s++) {
            List<Spot> spots = new ArrayList<>();
            if (s > 0) {
                Snapshot before = trace.get(s - 1);
                Snapshot now = trace.get(s);
                if (!before.getFrames().isEmpty()) {
                    FrameSnapshot ran = before.getFrames().get(0);
                    Map<String, Value> changed = changed(before, now);
                    for (CodeFacts.CallFact call : facts.callsAt(ran.getFile(), ran.getLine())) {
                        if (C_READS.contains(call.method())) {
                            readC(call, typed, at, changed, before.getFiles(), spots);
                            continue;
                        }
                        boolean word = WORD_READS.contains(call.method());
                        if (!word && !LINE_READS.contains(call.method())) {
                            continue;
                        }
                        if (call.receiver() != null && facts.isInputReader(call.receiver())) {
                            int from = at.getOrDefault(INPUT, 0);
                            int[] got = readFrom(typed, from, word);
                            if (got != null) {
                                at.put(INPUT, got[1]);
                                spots.add(new Spot(INPUT, got[0], got[1], into(changed, typed.substring(got[0], got[1]), word)));
                            }
                        } else {
                            Spot spot = fileRead(before.getFiles(), at, changed, word);
                            if (spot != null) {
                                at.put(spot.source(), spot.to());
                                spots.add(spot);
                            }
                        }
                    }
                }
            }
            for (Spot spot : spots) {
                latest.put(spot.source(), spot);
            }
            read.add(new HashMap<>(at));
            justRead.add(spots);
            last.add(spots.isEmpty() && s > 0 ? last.get(s - 1) : new HashMap<>(latest));
        }
    }

    // ---- C ----

    private static final Set<String> C_READS = Set.of("scanf", "fscanf", "getchar", "fgetc", "getc", "fgets", "gets");

    // One of C's reads on the line that just ran. From the keyboard, scanf's format says what it
    // took: %d, %s and %f skip spaces and newlines, then take a word; %c takes the very next
    // character (even the newline a %d left behind) unless a space comes before it in the format;
    // a space skips any spaces. getchar() takes one character, fgets() and gets() the rest of the
    // line. From a file, as for Java: the file whose next word or line just landed in a variable.
    private static void readC(CodeFacts.CallFact call, String typed, Map<String, Integer> at, Map<String, Value> changed,
                              Map<String, String> files, List<Spot> spots) {
        List<String> args = call.arguments();
        String method = call.method();
        String stream = switch (method) {
            case "fscanf", "fgetc", "getc" -> args.isEmpty() ? null : args.get(0);
            case "fgets" -> args.size() < 3 ? null : args.get(2);
            default -> "stdin";
        };
        if (!"stdin".equals(stream)) {
            boolean line = method.equals("fgets");
            int reads = method.equals("fscanf") && args.size() > 1 ? conversions(unquote(args.get(1))) : 1;
            for (int i = 0; i < reads; i++) {
                Spot spot = fileRead(files, at, changed, !line);
                if (spot == null) {
                    return;
                }
                at.put(spot.source(), spot.to());
                spots.add(spot);
            }
            return;
        }
        int from = at.getOrDefault(INPUT, 0);
        if (method.equals("getchar") || method.equals("fgetc") || method.equals("getc")) {
            if (from < typed.length()) {
                at.put(INPUT, from + 1);
                spots.add(new Spot(INPUT, from, from + 1, changed.size() == 1 ? changed.keySet().iterator().next() : null));
            }
            return;
        }
        if (method.equals("fgets") || method.equals("gets")) {
            int[] got = readFrom(typed, from, false);
            if (got != null) {
                at.put(INPUT, got[1]);
                spots.add(new Spot(INPUT, got[0], got[1], args.isEmpty() ? null : args.get(0)));
            }
            return;
        }
        int format = method.equals("fscanf") ? 1 : 0;
        if (args.size() <= format) {
            return;
        }
        String f = unquote(args.get(format));
        int target = format + 1;
        int pos = from;
        for (int i = 0; i < f.length(); i++) {
            char c = f.charAt(i);
            if (Character.isWhitespace(c)) {
                while (pos < typed.length() && Character.isWhitespace(typed.charAt(pos))) {
                    pos++;
                }
                continue;
            }
            if (c != '%') { // a character the input must have: taken if it's there, else scanf stops
                if (pos < typed.length() && typed.charAt(pos) == c) {
                    pos++;
                    continue;
                }
                break;
            }
            int k = i + 1;
            boolean kept = true;
            if (k < f.length() && f.charAt(k) == '*') {
                kept = false;
                k++;
            }
            while (k < f.length() && (Character.isDigit(f.charAt(k)) || "hlLqjzt".indexOf(f.charAt(k)) >= 0)) {
                k++;
            }
            if (k >= f.length()) {
                break;
            }
            char conversion = f.charAt(k);
            i = conversion == '[' ? Math.max(k, f.indexOf(']', k + 2)) : k;
            if (conversion == '%') {
                pos += pos < typed.length() && typed.charAt(pos) == '%' ? 1 : 0;
                continue;
            }
            if (conversion == 'n') {
                target += kept ? 1 : 0;
                continue;
            }
            int start = pos;
            int end;
            if (conversion == 'c') {
                if (start >= typed.length()) {
                    break;
                }
                end = start + 1;
            } else {
                int[] got = readFrom(typed, pos, true);
                if (got == null) {
                    break;
                }
                start = got[0];
                end = got[1];
            }
            String into = kept && target < args.size() ? args.get(target).replaceFirst("^&\\s*", "") : null;
            target += kept ? 1 : 0;
            spots.add(new Spot(INPUT, start, end, into));
            pos = end;
        }
        if (pos > from) {
            at.put(INPUT, pos);
        }
    }

    // How many values a scanf format reads (%d, %s…; not %% or %n, nor %*d's skipped one).
    private static int conversions(String format) {
        int count = 0;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("%(\\*?)[0-9hlLqjzt]*([a-zA-Z\\[%])").matcher(format);
        while (m.find()) {
            count += m.group(1).isEmpty() && !"%".equals(m.group(2)) && !"n".equals(m.group(2)) ? 1 : 0;
        }
        return count;
    }

    // "\"%d %d\"" → %d %d (with \n and \t as the characters).
    private static String unquote(String literal) {
        String inner = literal.length() >= 2 && literal.startsWith("\"") && literal.endsWith("\"")
                ? literal.substring(1, literal.length() - 1) : literal;
        return inner.replace("\\n", "\n").replace("\\t", "\t");
    }

    // How many characters of this source had been read by this step (0 if none).
    public int readUpTo(int step, String source) {
        return read.isEmpty() ? 0 : read.get(Math.max(0, Math.min(step, read.size() - 1))).getOrDefault(source, 0);
    }

    // What was read between the step before and this one.
    public List<Spot> justRead(int step) {
        return step < 0 || step >= justRead.size() ? List.of() : justRead.get(step);
    }

    // The latest read from this source by this step (it stays until the next one), or null.
    public Spot lastRead(int step, String source) {
        return step < 0 || step >= last.size() ? null : last.get(step).get(source);
    }

    // Reading once from `from`: {start, end} of the text taken (end is past a line's newline,
    // but the text itself stops before it), or null when there's nothing left to read.
    static int[] readFrom(String text, int from, boolean word) {
        if (from >= text.length()) {
            return null;
        }
        if (word) {
            int start = from;
            while (start < text.length() && Character.isWhitespace(text.charAt(start))) {
                start++;
            }
            if (start >= text.length()) {
                return null;
            }
            int end = start;
            while (end < text.length() && !Character.isWhitespace(text.charAt(end))) {
                end++;
            }
            return new int[] {start, end};
        }
        int end = text.indexOf('\n', from);
        return new int[] {from, end < 0 ? text.length() : end + 1};
    }

    // A file read: the file whose next word or line is the value that just landed in a variable.
    private static Spot fileRead(Map<String, String> files, Map<String, Integer> at, Map<String, Value> changed, boolean word) {
        for (Map.Entry<String, String> file : files.entrySet()) {
            String text = file.getValue().replace("\r\n", "\n");
            int[] got = readFrom(text, at.getOrDefault(file.getKey(), 0), word);
            if (got != null) {
                String into = into(changed, text.substring(got[0], got[1]), word);
                if (into != null) {
                    return new Spot(file.getKey(), got[0], got[1], into);
                }
            }
        }
        return null;
    }

    // The variable that changed to this text ("apples" for a String, 42 for an int), or null.
    private static String into(Map<String, Value> changed, String text, boolean word) {
        String plain = text.endsWith("\n") ? text.substring(0, text.length() - 1) : text;
        for (Map.Entry<String, Value> variable : changed.entrySet()) {
            String shown = variable.getValue().display();
            if (shown.equals("\"" + plain + "\"") || (shown.endsWith("…\"") && ("\"" + plain).startsWith(shown.substring(0, shown.length() - 2)))
                    || (word && sameNumber(shown, plain))) {
                return variable.getKey();
            }
        }
        return null;
    }

    private static boolean sameNumber(String shown, String text) {
        try {
            return Double.parseDouble(shown) == Double.parseDouble(text);
        } catch (NumberFormatException notANumber) {
            return false;
        }
    }

    // The plain values in the running call that are new or different at this step.
    private static Map<String, Value> changed(Snapshot before, Snapshot now) {
        Map<String, Value> found = new LinkedHashMap<>();
        if (now.getFrames().isEmpty()) {
            return found;
        }
        FrameSnapshot frame = now.getFrames().get(0);
        Map<String, Value> was = before.getFrames().isEmpty() ? Map.of() : before.getFrames().get(0).getLocals();
        for (Map.Entry<String, Value> local : frame.getLocals().entrySet()) {
            if (local.getValue() instanceof PrimitiveValue && !Objects.equals(local.getValue(), was.get(local.getKey()))) {
                found.put(local.getKey(), local.getValue());
            }
        }
        return found;
    }
}
