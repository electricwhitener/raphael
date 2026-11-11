package raphael.lang;

import java.util.List;
import java.util.Set;
import raphael.compile.CompileResult;
import raphael.compile.CompileSetupException;
import raphael.compile.SourceCompiler;
import raphael.model.SourceFile;
import raphael.trace.JdiTracer;
import raphael.trace.Tracer;

// Java: compiled with the JDK's own javac (SourceCompiler) and traced through JDI (JdiTracer).
// OOP: inheritance (a Language) and overriding.
public class JavaLanguage extends Language {

    private static final RegexLexer LEXER = new RegexLexer("//", true, true, false, Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue",
            "default", "do", "double", "else", "enum", "extends", "false", "final", "finally", "float", "for", "goto",
            "if", "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "null", "package",
            "private", "protected", "public", "record", "return", "short", "static", "strictfp", "super", "switch",
            "synchronized", "this", "throw", "throws", "transient", "true", "try", "var", "void", "volatile", "while"),
            Set.of());

    @Override
    public String id() {
        return "java";
    }

    @Override
    public String name() {
        return "Java";
    }

    @Override
    public List<String> endings() {
        return List.of(".java");
    }

    @Override
    public String lineComment() {
        return "//";
    }

    @Override
    public String template() { // \s keeps the indentation a text block would strip from the empty line
        return """
                public class Main {
                    public static void main(String[] args) {
                       \s
                    }
                }
                """;
    }

    // Its public class (or first class), then .java, since javac wants a public class in a file of its name.
    @Override
    public String suggestedName(String code) {
        java.util.regex.Matcher publicClass = java.util.regex.Pattern
                .compile("public\\s+(?:final\\s+|abstract\\s+)?class\\s+(\\w+)").matcher(code);
        if (publicClass.find()) {
            return publicClass.group(1) + ".java";
        }
        java.util.regex.Matcher anyClass = java.util.regex.Pattern.compile("\\bclass\\s+(\\w+)").matcher(code);
        return anyClass.find() ? anyClass.group(1) + ".java" : "Main.java";
    }

    @Override
    public List<Token> tokens(String code) {
        return LEXER.tokens(code);
    }

    @Override
    public boolean readsInput(String code) {
        return code.contains("System.in");
    }

    @Override
    public boolean readsInputOn(String line) {
        return line.matches("(?s).*\\b(next\\w*|hasNext\\w*|readLine|read)\\s*\\(.*");
    }

    // It names one of Java's file classes. Not just "java.io", which an import of IOException alone
    // would match, or "Path", which a graph program may call a class.
    @Override
    public boolean usesFiles(String code) {
        return code.matches("(?s).*\\b(FileReader|FileWriter|FileInputStream|FileOutputStream|PrintWriter|Files|Paths|File)\\b.*");
    }

    @Override
    public int likeness(String code) {
        return Languages.count(code, "public class", "public static void main", "System.out.print", "String[] args",
                "import java.", "new ArrayList", "private ", "extends ");
    }

    @Override
    public String cantRun() {
        return null; // Raphael itself runs on Java, so the JDK is always there
    }

    @Override
    public CompileResult compile(String code) throws CompileSetupException {
        return new SourceCompiler().compile(code);
    }

    @Override
    public CompileResult compileFolder(List<SourceFile> files, String preferredMain) throws CompileSetupException {
        return new SourceCompiler().compileFolder(files, preferredMain);
    }

    @Override
    public Tracer newTracer() {
        return new JdiTracer();
    }

    @Override
    public String hint(String compilerMessage) {
        return raphael.compile.CompileHints.hint(compilerMessage);
    }
}
