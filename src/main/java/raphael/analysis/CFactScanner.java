package raphael.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Reads the facts of a C file into CodeFacts, as FactScanner does for Java from javac's syntax
// tree: its functions (with their lines, parameters, whether they return a value and whether they
// call themselves), the calls in them with their arguments, their return statements, whether the
// program reads input or opens files, and "// moment:" comments. C has no tree Raphael can ask
// for, so this works on tokens: comments are dropped, string and char literals are kept whole,
// and # lines are skipped.
// A function's "class" is its file's name without the ending (main.c → main), as CTracer names
// its frames, so the facts and the recorded run name things alike.
// OOP: encapsulation (the tokens and the position are private; scan() is the one way in); a
// nested record (Token).
public final class CFactScanner {

    private static final Pattern MOMENT = Pattern.compile("//\\s*moment:\\s*(.+?)\\s*$");
    private static final Set<String> NOT_CALLS = Set.of("if", "while", "for", "switch", "return", "sizeof", "do",
            "else", "case", "_Alignof", "defined");
    private static final Set<String> INPUT = Set.of("scanf", "getchar", "gets", "getline", "fgetc", "getc");
    private static final Set<String> SPECIFIERS = Set.of("static", "inline", "extern", "const", "register");

    private record Token(String text, int line, int start, int end) {
    }

    private final String source;
    private final String file;
    private final String unit;
    private final CodeFacts facts;
    private final List<Token> tokens = new ArrayList<>();
    private final java.util.Set<String> structs = new java.util.HashSet<>(); // struct names, and typedef names for structs

    private CFactScanner(CodeFacts facts, String source, String file, String unit) {
        this.facts = facts;
        this.source = source;
        this.file = file;
        this.unit = unit;
    }

    // Adds one file's facts. file: its path in the program, or null for the editor's one text;
    // unit: its name without the ending.
    public static void scan(CodeFacts facts, String source, String file, String unit) {
        CFactScanner scanner = new CFactScanner(facts, source, file, unit);
        scanner.tokenize();
        scanner.functions();
        scanner.momentComments();
    }

    // ---- tokens ----

    private void tokenize() {
        int line = 1;
        int i = 0;
        boolean lineStart = true;
        int n = source.length();
        while (i < n) {
            char c = source.charAt(i);
            if (c == '\n') {
                line++;
                i++;
                lineStart = true;
                continue;
            }
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if (lineStart && c == '#') { // a preprocessor line, with its \ continuations
                while (i < n && (source.charAt(i) != '\n' || source.charAt(i - 1) == '\\')) {
                    if (source.charAt(i) == '\n') {
                        line++;
                    }
                    i++;
                }
                continue;
            }
            lineStart = false;
            if (source.startsWith("//", i)) {
                while (i < n && source.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }
            if (source.startsWith("/*", i)) {
                int end = source.indexOf("*/", i + 2);
                end = end < 0 ? n : end + 2;
                for (int k = i; k < end; k++) {
                    line += source.charAt(k) == '\n' ? 1 : 0;
                }
                i = end;
                continue;
            }
            int start = i;
            if (c == '"' || c == '\'') {
                i++;
                while (i < n && source.charAt(i) != c && source.charAt(i) != '\n') {
                    i += source.charAt(i) == '\\' ? 2 : 1;
                }
                i = Math.min(n, i + 1);
            } else if (Character.isJavaIdentifierStart(c)) {
                while (i < n && Character.isJavaIdentifierPart(source.charAt(i))) {
                    i++;
                }
            } else if (Character.isDigit(c)) {
                while (i < n && (Character.isLetterOrDigit(source.charAt(i)) || source.charAt(i) == '.')) {
                    i++;
                }
            } else if (source.startsWith("->", i)) {
                i += 2;
            } else {
                i++;
            }
            tokens.add(new Token(source.substring(start, i), line, start, i));
        }
    }

    private String text(int t) {
        return t < tokens.size() ? tokens.get(t).text() : "";
    }

    private boolean isName(int t) {
        String s = text(t);
        return !s.isEmpty() && Character.isJavaIdentifierStart(s.charAt(0));
    }

    // The token that closes the bracket at t ("(" or "{"), or the last token.
    private int closing(int t) {
        String open = text(t);
        String close = open.equals("(") ? ")" : open.equals("[") ? "]" : "}";
        int depth = 0;
        for (int k = t; k < tokens.size(); k++) {
            if (text(k).equals(open)) {
                depth++;
            } else if (text(k).equals(close) && --depth == 0) {
                return k;
            }
        }
        return tokens.size() - 1;
    }

    // The source text between two tokens (both included), as written.
    private String slice(int from, int to) {
        return from > to ? "" : source.substring(tokens.get(from).start(), tokens.get(to).end()).replaceAll("\\s+", " ").strip();
    }

    // ---- functions ----

    private void functions() {
        int t = 0;
        while (t < tokens.size()) {
            String s = text(t);
            if (s.equals("typedef") && (text(t + 1).equals("struct") || text(t + 1).equals("union")) && isName(t + 2)
                    && isName(t + 3) && text(t + 4).equals(";")) {
                structs.add(text(t + 2)); // typedef struct Node Node;
                structs.add(text(t + 3));
                t += 5;
                continue;
            }
            if ((s.equals("struct") || s.equals("union")) && isName(t + 1) && text(t + 2).equals("{")) {
                structs.add(text(t + 1));
                int end = struct(text(t + 1), t + 2); // struct Node { … } (also inside a typedef)
                if (isName(end + 1)) {
                    structs.add(text(end + 1)); // typedef struct Node { … } Node_t;
                }
                t = end + 1;
                continue;
            }
            if ((s.equals("struct") || s.equals("union")) && text(t + 1).equals("{")) {
                int end = closing(t + 1);
                if (isName(end + 1)) {
                    structs.add(text(end + 1));
                    struct(text(end + 1), t + 1); // typedef struct { … } Point;
                }
                t = end + 1;
                continue;
            }
            if (s.equals("{")) { // an enum or an initializer at the top level
                t = closing(t) + 1;
                continue;
            }
            if (isName(t) && text(t + 1).equals("(") && !NOT_CALLS.contains(s)) {
                int close = closing(t + 1);
                if (text(close + 1).equals("{")) {
                    t = function(t, close) + 1;
                    continue;
                }
                t = close + 1;
                continue;
            }
            t++;
        }
    }

    // A function definition: its name at t, its parameters' ")" at close. Returns its body's "}".
    private int function(int t, int close) {
        String name = text(t);
        int bodyEnd = closing(close + 1);
        List<String> types = new ArrayList<>(); // what comes before the name, back to the last declaration's end
        for (int k = t - 1; k >= 0 && !Set.of(";", "}", "{").contains(text(k)); k--) {
            if (!SPECIFIERS.contains(text(k))) {
                types.add(0, text(k));
            }
        }
        boolean returnsValue = !(types.size() == 1 && types.get(0).equals("void"));
        List<String> parameters = new ArrayList<>();
        for (List<Integer> part : splitAtCommas(t + 2, close - 1)) {
            String last = null;
            for (int k : part) {
                if (text(k).equals("[") || text(k).equals("(")) {
                    break;
                }
                if (isName(k)) {
                    last = text(k);
                }
            }
            if (last != null && !(part.size() == 1 && last.equals("void"))) {
                parameters.add(last);
            }
        }
        boolean recursive = false;
        int callsBefore = facts.calls().size();
        for (int k = close + 2; k < bodyEnd; k++) {
            if (isName(k) && text(k + 1).equals("(") && !NOT_CALLS.contains(text(k))
                    && !text(k - 1).equals(".") && !text(k - 1).equals("->")) {
                int end = closing(k + 1);
                List<String> arguments = new ArrayList<>();
                for (List<Integer> part : splitAtCommas(k + 2, end - 1)) {
                    arguments.add(slice(part.get(0), part.get(part.size() - 1)));
                }
                facts.add(new CodeFacts.CallFact(file, tokens.get(k).line(), text(k), null, null, null, arguments));
                recursive |= text(k).equals(name);
                if (text(k).equals("fopen")) {
                    facts.markUsesFiles();
                }
                if (INPUT.contains(text(k)) || ((text(k).equals("fgets") || text(k).equals("fscanf"))
                        && arguments.contains("stdin"))) {
                    facts.markReadsInput();
                }
            }
        }
        for (int k = close + 2; k < bodyEnd; k++) {
            if (text(k).equals("return")) {
                int end = k + 1;
                while (end < bodyEnd && !text(end).equals(";")) {
                    end = text(end).equals("(") ? closing(end) + 1 : end + 1;
                }
                String expression = end > k + 1 ? slice(k + 1, end - 1) : null;
                boolean calls = expression != null && Pattern.compile("\\b" + Pattern.quote(name) + "\\s*\\(").matcher(expression).find();
                facts.add(new CodeFacts.ReturnFact(file, tokens.get(k).line(), unit, name, expression, calls));
            }
        }
        facts.add(new CodeFacts.MethodFact(file, unit, name, tokens.get(t).line(), tokens.get(bodyEnd).line(),
                recursive, returnsValue, parameters));
        return bodyEnd;
    }

    // A struct's fields, as FieldFacts of a "class" named after it: struct Node *next is a field
    // of type Node (a link, as CTracer names it), struct Node *kids[4] one of type Node[], int
    // keys[3] one of type int[]. open: its "{". Returns its "}".
    private int struct(String name, int open) {
        int close = closing(open);
        int start = open + 1;
        for (int k = open + 1; k <= close; k++) {
            if (text(k).equals("{")) { // a struct or union inside: its fields aren't this one's
                k = closing(k);
                continue;
            }
            if (!text(k).equals(";") && k != close) {
                continue;
            }
            field(name, start, k - 1);
            start = k + 1;
        }
        return close;
    }

    // One declaration inside a struct, from `from` to `to`: int x, y; struct Node *next; char name[8];
    private void field(String struct, int from, int to) {
        List<List<Integer>> parts = splitAtCommas(from, to);
        if (parts.isEmpty() || parts.get(0).isEmpty()) {
            return;
        }
        List<Integer> first = parts.get(0);
        String base;
        int declarator; // where the first declarator begins
        if ((text(first.get(0)).equals("struct") || text(first.get(0)).equals("union")) && first.size() > 1
                && text(first.get(1)).equals("{")) {
            base = text(first.get(0)); // union { … } u: an unnamed one, its declarator after the }
            declarator = first.indexOf(closing(first.get(1))) + 1;
        } else if ((text(first.get(0)).equals("struct") || text(first.get(0)).equals("union")) && first.size() > 1) {
            base = text(first.get(1)); // struct Node → Node
            declarator = 2;
        } else {
            declarator = nameIndex(first); // the first declarator starts at its first * or else at its name
            for (int k = 0; k < declarator; k++) {
                if (text(first.get(k)).equals("*")) {
                    declarator = k;
                    break;
                }
            }
            StringBuilder type = new StringBuilder();
            for (int k = 0; k < declarator; k++) {
                if (!SPECIFIERS.contains(text(first.get(k)))) {
                    type.append(type.length() == 0 ? "" : " ").append(text(first.get(k)));
                }
            }
            base = type.toString();
        }
        boolean structType = text(first.get(0)).equals("struct") || text(first.get(0)).equals("union")
                || structs.contains(base) || base.equals(struct); // Node *next, with typedef struct Node Node
        for (int p = 0; p < parts.size(); p++) {
            List<Integer> part = p == 0 ? first.subList(Math.min(declarator, first.size()), first.size()) : parts.get(p);
            boolean pointer = false;
            boolean array = false;
            String fieldName = null;
            for (int k : part) {
                String s = text(k);
                if (s.equals("(")) {
                    fieldName = null; // a function pointer: not a field Raphael draws
                    break;
                }
                pointer |= s.equals("*");
                array |= s.equals("[");
                if (isName(k) && !array && fieldName == null) {
                    fieldName = s;
                }
            }
            if (fieldName == null || base.isEmpty()) {
                continue;
            }
            String type = structType ? base : base + (pointer ? " *" : "");
            facts.add(new CodeFacts.FieldFact(struct, fieldName, array ? type + "[]" : type, false, false,
                    tokens.get(part.isEmpty() ? from : part.get(0)).line(), false));
        }
    }

    // The index (in the part) of the first declarator's name: the last name before any [ or =.
    private int nameIndex(List<Integer> part) {
        int found = part.size() - 1;
        for (int k = 0; k < part.size(); k++) {
            String s = text(part.get(k));
            if (s.equals("[") || s.equals("=")) {
                break;
            }
            if (isName(part.get(k))) {
                found = k;
            }
        }
        return found;
    }

    // The tokens from `from` to `to`, in groups split at the commas outside any brackets.
    private List<List<Integer>> splitAtCommas(int from, int to) {
        List<List<Integer>> parts = new ArrayList<>();
        List<Integer> part = new ArrayList<>();
        int depth = 0;
        for (int k = from; k <= to; k++) {
            String s = text(k);
            if (s.equals("(") || s.equals("[") || s.equals("{")) {
                depth++;
            } else if (s.equals(")") || s.equals("]") || s.equals("}")) {
                depth--;
            }
            if (s.equals(",") && depth == 0) {
                parts.add(part);
                part = new ArrayList<>();
            } else {
                part.add(k);
            }
        }
        if (!part.isEmpty()) {
            parts.add(part);
        }
        return parts;
    }

    private void momentComments() {
        String[] lines = source.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            Matcher m = MOMENT.matcher(lines[i]);
            if (m.find()) {
                facts.add(new CodeFacts.MomentComment(file, i + 1, m.group(1)));
            }
        }
    }
}
