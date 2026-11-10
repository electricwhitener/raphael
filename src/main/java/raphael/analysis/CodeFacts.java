package raphael.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

// What the code says, as javac's syntax tree tells it: the program's classes, methods, fields,
// calls, try blocks, returns and the author's "// moment:" comments. The concept classes
// (explain/) read these facts together with the recorded run, so every explanation they give is
// about something that is really in the code. FactScanner fills them in while javac compiles.
// file is the program's own path for a file ("shapes/Circle.java"), or null for a program of one file.
// OOP: encapsulation (the lists are filled through package-private methods and read through
// unmodifiable views); small records for the facts themselves.
public class CodeFacts {

    // A method or constructor (a constructor's name is its class's name).
    public record MethodFact(String file, String className, String name, int firstLine, int lastLine,
                             boolean recursive, boolean returnsValue, List<String> parameters) {
    }

    public record FieldFact(String className, String name, String type, boolean isPrivate, boolean isStatic) {
    }

    // A method that sets one field from its parameter. guardLine: the line of a check (an if)
    // before the field is set, or -1 if it sets it every time.
    public record SetterFact(String className, String method, String field, int guardLine) {
    }

    // A call. declaredType is the declared type of what it's called on ("Shape" in s.area()),
    // or null for a call with nothing in front; declaringClass is the class whose method it names.
    public record CallFact(String file, int line, String method, String declaredType, String declaringClass) {
    }

    public record CatchFact(int line, List<String> types) {
    }

    // finallyLine is -1 when there's no finally.
    public record TryFact(String file, int tryLine, int endLine, List<CatchFact> catches, int finallyLine) {
    }

    // A return statement: its expression as written ("n * factorial(n - 1)"), and whether it calls
    // the method it's in (a return without that call, in a recursive method, is a base case).
    public record ReturnFact(String file, int line, String className, String method, String expression, boolean recursive) {
    }

    // "// moment: text" on a line: the author's own key moment for that line.
    public record MomentComment(String file, int line, String text) {
    }

    private final List<MethodFact> methods = new ArrayList<>();
    private final List<FieldFact> fields = new ArrayList<>();
    private final List<SetterFact> setters = new ArrayList<>();
    private final List<CallFact> calls = new ArrayList<>();
    private final List<TryFact> tries = new ArrayList<>();
    private final List<ReturnFact> returns = new ArrayList<>();
    private final List<MomentComment> moments = new ArrayList<>();
    private final Map<String, String> superclasses = new LinkedHashMap<>();   // class → its parent (student classes only)
    private final Map<String, List<String>> interfaces = new LinkedHashMap<>(); // class → what it implements
    private final Set<String> abstractClasses = new HashSet<>();
    private final Set<String> interfaceTypes = new HashSet<>();
    private final List<Integer> throwLines = new ArrayList<>();
    private boolean usesFiles;
    private boolean readsInput;

    // ---- reading ----

    public List<MethodFact> methods() {
        return Collections.unmodifiableList(methods);
    }

    public List<FieldFact> fields() {
        return Collections.unmodifiableList(fields);
    }

    public List<SetterFact> setters() {
        return Collections.unmodifiableList(setters);
    }

    public List<CallFact> calls() {
        return Collections.unmodifiableList(calls);
    }

    public List<TryFact> tries() {
        return Collections.unmodifiableList(tries);
    }

    public List<ReturnFact> returns() {
        return Collections.unmodifiableList(returns);
    }

    public List<MomentComment> momentComments() {
        return Collections.unmodifiableList(moments);
    }

    public Map<String, String> superclasses() {
        return Collections.unmodifiableMap(superclasses);
    }

    public Map<String, List<String>> interfaces() {
        return Collections.unmodifiableMap(interfaces);
    }

    public boolean isAbstract(String className) {
        return abstractClasses.contains(className);
    }

    public boolean isInterface(String className) {
        return interfaceTypes.contains(className);
    }

    public List<Integer> throwLines() {
        return Collections.unmodifiableList(throwLines);
    }

    // Whether the program works with files (it uses one of Java's file classes).
    public boolean usesFiles() {
        return usesFiles;
    }

    // Whether it reads typed input (a Scanner on System.in, or System.in itself).
    public boolean readsInput() {
        return readsInput;
    }

    public boolean isRecursive(String className, String method) {
        for (MethodFact fact : methods) {
            if (fact.recursive() && fact.name().equals(method) && fact.className().equals(className)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasRecursion() {
        for (MethodFact fact : methods) {
            if (fact.recursive()) {
                return true;
            }
        }
        return false;
    }

    // The method a line is in, or null.
    public MethodFact methodAt(String file, int line) {
        MethodFact found = null;
        for (MethodFact fact : methods) {
            if (Objects.equals(fact.file(), file) && fact.firstLine() <= line && line <= fact.lastLine()
                    && (found == null || fact.firstLine() >= found.firstLine())) {
                found = fact; // the innermost one
            }
        }
        return found;
    }

    public ReturnFact returnAt(String file, int line) {
        for (ReturnFact fact : returns) {
            if (Objects.equals(fact.file(), file) && fact.line() == line) {
                return fact;
            }
        }
        return null;
    }

    public MomentComment momentAt(String file, int line) {
        for (MomentComment comment : moments) {
            if (Objects.equals(comment.file(), file) && comment.line() == line) {
                return comment;
            }
        }
        return null;
    }

    public List<CallFact> callsAt(String file, int line) {
        List<CallFact> found = new ArrayList<>();
        for (CallFact call : calls) {
            if (Objects.equals(call.file(), file) && call.line() == line) {
                found.add(call);
            }
        }
        return found;
    }

    public SetterFact setter(String className, String method) {
        for (SetterFact fact : setters) {
            if (fact.className().equals(className) && fact.method().equals(method)) {
                return fact;
            }
        }
        return null;
    }

    public FieldFact field(String className, String name) {
        for (FieldFact fact : fields) {
            if (fact.className().equals(className) && fact.name().equals(name)) {
                return fact;
            }
        }
        return null;
    }

    // The try whose catch begins on this line, or null.
    public TryFact tryCatchingAt(String file, int catchLine) {
        for (TryFact fact : tries) {
            if (!Objects.equals(fact.file(), file)) {
                continue;
            }
            for (CatchFact c : fact.catches()) {
                if (c.line() == catchLine || c.line() == catchLine - 1) { // the step lands on the catch's first line inside
                    return fact;
                }
            }
        }
        return null;
    }

    // ---- filled in by FactScanner ----

    void add(MethodFact fact) {
        methods.add(fact);
    }

    void add(FieldFact fact) {
        fields.add(fact);
    }

    void add(SetterFact fact) {
        setters.add(fact);
    }

    void add(CallFact fact) {
        calls.add(fact);
    }

    void add(TryFact fact) {
        tries.add(fact);
    }

    void add(ReturnFact fact) {
        returns.add(fact);
    }

    void add(MomentComment comment) {
        moments.add(comment);
    }

    void addClass(String name, String parent, List<String> implemented, boolean isAbstract, boolean isInterface) {
        if (parent != null) {
            superclasses.put(name, parent);
        }
        interfaces.put(name, List.copyOf(implemented));
        if (isAbstract) {
            abstractClasses.add(name);
        }
        if (isInterface) {
            interfaceTypes.add(name);
        }
    }

    void addThrow(int line) {
        throwLines.add(line);
    }

    void markUsesFiles() {
        usesFiles = true;
    }

    void markReadsInput() {
        readsInput = true;
    }
}
