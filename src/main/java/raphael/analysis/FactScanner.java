package raphael.analysis;

import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.LineMap;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.ThrowTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TryTree;
import com.sun.source.tree.UnionTypeTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

// Walks one file's syntax tree, after javac has worked out every name's meaning, and writes what
// it finds into CodeFacts. Each visitXxx method is called for one kind of piece of the code (a
// class, a method, a call...), so this class only says what to do with each kind; TreePathScanner
// does the walking.
// OOP: inheritance and the Visitor pattern (javac's TreePathScanner calls the overridden visit
// method that matches each node); a stack (Deque) remembers which class and method we're inside.
public class FactScanner extends TreePathScanner<Void, Void> {

    private static final Pattern MOMENT = Pattern.compile("//\\s*moment:\\s*(.+?)\\s*$");
    // Java's classes for files, and for reading typed input.
    private static final Set<String> FILE_CLASSES = Set.of("java.io.File", "java.io.FileReader", "java.io.FileWriter",
            "java.io.FileInputStream", "java.io.FileOutputStream", "java.io.PrintWriter", "java.io.RandomAccessFile",
            "java.nio.file.Files", "java.nio.file.Paths", "java.nio.file.Path");

    private final CodeFacts facts;
    private final Trees trees;
    private final CompilationUnitTree unit;
    private final String file;
    private final LineMap lines;
    private final SourcePositions positions;
    private final String source;
    private final Deque<String> classes = new ArrayDeque<>();
    private final Deque<MethodState> methods = new ArrayDeque<>();

    // What's known about the method being walked, filled in on the way through it.
    private static final class MethodState {
        final ExecutableElement element;
        final String name;
        boolean recursive;
        int guardLine = -1;          // the first if in it
        String assignedField;        // the one field it sets from a parameter, for a setter
        int assignments;

        MethodState(ExecutableElement element, String name) {
            this.element = element;
            this.name = name;
        }
    }

    private FactScanner(CodeFacts facts, Trees trees, CompilationUnitTree unit, String file) throws java.io.IOException {
        this.facts = facts;
        this.trees = trees;
        this.unit = unit;
        this.file = file;
        this.lines = unit.getLineMap();
        this.positions = trees.getSourcePositions();
        this.source = unit.getSourceFile().getCharContent(true).toString();
    }

    // Adds one analysed file's facts. file: the program's path for it, or null for a program of one file.
    public static void scan(CodeFacts facts, Trees trees, CompilationUnitTree unit, String file) {
        try {
            FactScanner scanner = new FactScanner(facts, trees, unit, file);
            scanner.scan(new TreePath(unit), null);
            scanner.findMomentComments();
        } catch (java.io.IOException e) {
            // The source can't be read back: no facts for this file, and the run still works.
        }
    }

    // Comments aren't in the tree, so "// moment:" is found in the text, line by line.
    private void findMomentComments() {
        String[] text = source.split("\n", -1);
        for (int i = 0; i < text.length; i++) {
            Matcher m = MOMENT.matcher(text[i]);
            if (m.find()) {
                facts.add(new CodeFacts.MomentComment(file, i + 1, m.group(1)));
            }
        }
    }

    @Override
    public Void visitClass(ClassTree tree, Void nothing) {
        String name = tree.getSimpleName().toString();
        if (name.isEmpty()) { // an anonymous class: its methods belong to the class around it
            return super.visitClass(tree, nothing);
        }
        String parent = tree.getExtendsClause() == null ? null : simple(tree.getExtendsClause().toString());
        List<String> implemented = new ArrayList<>();
        for (Tree type : tree.getImplementsClause()) {
            implemented.add(simple(type.toString()));
        }
        facts.addClass(name, parent, implemented, tree.getModifiers().getFlags().contains(Modifier.ABSTRACT),
                tree.getKind() == Tree.Kind.INTERFACE);
        classes.push(name);
        try {
            for (Tree member : tree.getMembers()) {
                if (member instanceof VariableTree) {
                    VariableTree field = (VariableTree) member;
                    Set<Modifier> flags = field.getModifiers().getFlags();
                    facts.add(new CodeFacts.FieldFact(name, field.getName().toString(), field.getType().toString(),
                            flags.contains(Modifier.PRIVATE), flags.contains(Modifier.STATIC), line(field),
                            field.getInitializer() != null));
                }
            }
            return super.visitClass(tree, nothing);
        } finally {
            classes.pop();
        }
    }

    @Override
    public Void visitMethod(MethodTree tree, Void nothing) {
        if (tree.getBody() == null || classes.isEmpty()) {
            if (!classes.isEmpty()) {
                facts.addAbstractMethod(classes.peek(), tree.getName().toString());
            }
            return super.visitMethod(tree, nothing); // abstract or in an interface: no code to run
        }
        Element element = trees.getElement(getCurrentPath());
        boolean constructor = tree.getName().contentEquals("<init>");
        String name = constructor ? classes.peek() : tree.getName().toString();
        MethodState state = new MethodState(element instanceof ExecutableElement ? (ExecutableElement) element : null, name);
        methods.push(state);
        try {
            super.visitMethod(tree, nothing);
        } finally {
            methods.pop();
        }
        boolean returnsValue = !constructor && tree.getReturnType() != null && !tree.getReturnType().toString().equals("void");
        List<String> parameters = new ArrayList<>();
        for (VariableTree parameter : tree.getParameters()) {
            parameters.add(parameter.getName().toString());
        }
        facts.add(new CodeFacts.MethodFact(file, classes.peek(), name, line(tree), endLine(tree), state.recursive,
                returnsValue, parameters));
        if (!constructor && name.startsWith("set") && parameters.size() == 1 && state.assignedField != null
                && state.assignments == 1) {
            facts.add(new CodeFacts.SetterFact(classes.peek(), name, state.assignedField, state.guardLine));
        }
        return null;
    }

    @Override
    public Void visitMethodInvocation(MethodInvocationTree tree, Void nothing) {
        Element called = trees.getElement(getCurrentPath());
        MethodState in = methods.peek();
        if (in != null && in.element != null && in.element.equals(called)) {
            in.recursive = true;
        }
        if (called instanceof ExecutableElement) {
            Element owner = called.getEnclosingElement();
            String declaringClass = owner instanceof TypeElement ? ((TypeElement) owner).getSimpleName().toString() : null;
            String declaredType = null;
            String receiver = null;
            ExpressionTree select = tree.getMethodSelect();
            if (select instanceof MemberSelectTree) {
                ExpressionTree on = ((MemberSelectTree) select).getExpression();
                TypeMirror type = trees.getTypeMirror(new TreePath(getCurrentPath(), on));
                declaredType = type == null || type.getKind() == TypeKind.ERROR ? null : simple(type.toString());
                receiver = textOf(on);
            }
            facts.add(new CodeFacts.CallFact(file, line(tree), called.getSimpleName().toString(), declaredType, declaringClass, receiver));
            if (owner instanceof TypeElement && FILE_CLASSES.contains(((TypeElement) owner).getQualifiedName().toString())) {
                facts.markUsesFiles();
            }
        }
        return super.visitMethodInvocation(tree, nothing);
    }

    @Override
    public Void visitNewClass(NewClassTree tree, Void nothing) {
        TypeMirror type = trees.getTypeMirror(getCurrentPath());
        String name = type == null ? "" : type.toString().replaceAll("<.*", "");
        if (FILE_CLASSES.contains(name)) {
            facts.markUsesFiles();
        }
        if (name.equals("java.util.Scanner") && tree.getArguments().size() == 1
                && tree.getArguments().get(0).toString().equals("System.in")) {
            facts.markReadsInput();
        }
        if (tree.getArguments().size() == 1 && tree.getArguments().get(0).toString().equals("System.in")) {
            // the variable that reads the keyboard: Scanner sc = new Scanner(System.in), or a
            // BufferedReader around an InputStreamReader around System.in
            TreePath up = getCurrentPath().getParentPath();
            while (up != null && up.getLeaf() instanceof NewClassTree) {
                up = up.getParentPath();
            }
            if (up != null && up.getLeaf() instanceof VariableTree) {
                facts.addInputReader(((VariableTree) up.getLeaf()).getName().toString());
            } else if (up != null && up.getLeaf() instanceof AssignmentTree) {
                facts.addInputReader(textOf(((AssignmentTree) up.getLeaf()).getVariable()));
            }
        }
        if (name.equals("java.util.Scanner") && tree.getArguments().size() == 1) {
            TypeMirror argument = trees.getTypeMirror(new TreePath(getCurrentPath(), tree.getArguments().get(0)));
            if (argument != null && FILE_CLASSES.contains(argument.toString())) {
                facts.markUsesFiles(); // new Scanner(new File(...)) or a Path
            }
        }
        return super.visitNewClass(tree, nothing);
    }

    @Override
    public Void visitMemberSelect(MemberSelectTree tree, Void nothing) {
        if (tree.toString().equals("System.in")) {
            facts.markReadsInput();
        }
        return super.visitMemberSelect(tree, nothing);
    }

    @Override
    public Void visitIf(IfTree tree, Void nothing) {
        MethodState in = methods.peek();
        if (in != null && in.guardLine < 0) {
            in.guardLine = line(tree);
        }
        return super.visitIf(tree, nothing);
    }

    @Override
    public Void visitAssignment(AssignmentTree tree, Void nothing) {
        MethodState in = methods.peek();
        if (in != null) {
            String field = fieldOf(tree.getVariable());
            if (field != null) {
                in.assignedField = field;
                in.assignments++;
            }
        }
        return super.visitAssignment(tree, nothing);
    }

    // The field an assignment's left side names (this.x or a bare x that is a field), or null.
    private String fieldOf(ExpressionTree variable) {
        Element element = trees.getElement(new TreePath(getCurrentPath(), variable));
        if (element != null && element.getKind() == javax.lang.model.element.ElementKind.FIELD
                && (variable instanceof IdentifierTree || variable.toString().startsWith("this."))) {
            return element.getSimpleName().toString();
        }
        return null;
    }

    @Override
    public Void visitTry(TryTree tree, Void nothing) {
        List<CodeFacts.CatchFact> catches = new ArrayList<>();
        for (CatchTree c : tree.getCatches()) {
            Tree type = c.getParameter().getType();
            List<String> types = new ArrayList<>();
            if (type instanceof UnionTypeTree) { // catch (A | B e)
                for (Tree alternative : ((UnionTypeTree) type).getTypeAlternatives()) {
                    types.add(simple(alternative.toString()));
                }
            } else {
                types.add(simple(type.toString()));
            }
            catches.add(new CodeFacts.CatchFact(line(c), types));
        }
        int finallyLine = tree.getFinallyBlock() == null ? -1 : line(tree.getFinallyBlock());
        facts.add(new CodeFacts.TryFact(file, line(tree), endLine(tree), catches, finallyLine));
        return super.visitTry(tree, nothing);
    }

    @Override
    public Void visitThrow(ThrowTree tree, Void nothing) {
        facts.addThrow(line(tree));
        return super.visitThrow(tree, nothing);
    }

    @Override
    public Void visitReturn(ReturnTree tree, Void nothing) {
        MethodState in = methods.peek();
        if (in != null) {
            boolean callsItself = tree.getExpression() != null && callsMethod(tree.getExpression(), in.element);
            facts.add(new CodeFacts.ReturnFact(file, line(tree), classes.peek(), in.name,
                    tree.getExpression() == null ? null : textOf(tree.getExpression()), callsItself));
        }
        return super.visitReturn(tree, nothing);
    }

    // Whether an expression contains a call to this method.
    private boolean callsMethod(ExpressionTree expression, ExecutableElement method) {
        if (method == null) {
            return false;
        }
        boolean[] found = new boolean[1];
        new TreePathScanner<Void, Void>() {
            @Override
            public Void visitMethodInvocation(MethodInvocationTree tree, Void nothing) {
                if (method.equals(trees.getElement(getCurrentPath()))) {
                    found[0] = true;
                }
                return super.visitMethodInvocation(tree, nothing);
            }
        }.scan(new TreePath(getCurrentPath(), expression), null);
        return found[0];
    }

    // The code of a piece of the tree, exactly as written.
    private String textOf(Tree tree) {
        long start = positions.getStartPosition(unit, tree);
        long end = positions.getEndPosition(unit, tree);
        return start >= 0 && end <= source.length() && end > start ? source.substring((int) start, (int) end) : tree.toString();
    }

    private int line(Tree tree) {
        return (int) lines.getLineNumber(positions.getStartPosition(unit, tree));
    }

    private int endLine(Tree tree) {
        long end = positions.getEndPosition(unit, tree);
        return end < 0 ? line(tree) : (int) lines.getLineNumber(Math.max(0, end - 1));
    }

    // "java.util.List<String>" → "List"; "shapes.Shape" → "Shape".
    private static String simple(String type) {
        String plain = type.replaceAll("<.*", "");
        return plain.substring(plain.lastIndexOf('.') + 1);
    }
}
