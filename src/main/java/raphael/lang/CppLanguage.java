package raphael.lang;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import raphael.compile.CCompiler;
import raphael.compile.CompileResult;
import raphael.compile.CompileSetupException;
import raphael.compile.GccHints;
import raphael.model.SourceFile;
import raphael.toolchain.Toolchains;
import raphael.trace.CTracer;
import raphael.trace.Tracer;

// C++: C with classes, templates and the standard library. Compiled with g++ (CCompiler in its
// C++ mode) and traced through gdb, or lldb on a Mac, by the same tracer as C (CTracer).
// OOP: inheritance (a Language) and overriding; composition (the C compiler and tracer reused).
public class CppLanguage extends Language {

    private static final Set<String> KEYWORDS = new HashSet<>(CLanguage.KEYWORDS);

    static {
        KEYWORDS.addAll(Set.of("class", "namespace", "template", "typename", "public", "private", "protected",
                "virtual", "override", "final", "new", "delete", "this", "nullptr", "using", "operator", "friend",
                "try", "catch", "throw", "explicit", "mutable", "noexcept", "constexpr", "static_cast",
                "dynamic_cast", "const_cast", "reinterpret_cast", "auto"));
    }

    private static final RegexLexer LEXER = new RegexLexer("//", true, false, true, KEYWORDS,
            Set.of("std", "string", "vector", "map", "set", "list", "stack", "queue", "pair", "size_t"));

    @Override
    public String id() {
        return "cpp";
    }

    @Override
    public String name() {
        return "C++";
    }

    @Override
    public List<String> endings() {
        return List.of(".cpp", ".cc", ".cxx", ".hpp", ".hh");
    }

    @Override
    public String lineComment() {
        return "//";
    }

    @Override
    public String template() { // \s keeps the indentation a text block would strip from the empty line
        return """
                #include <iostream>
                using namespace std;

                int main() {
                   \s
                    return 0;
                }
                """;
    }

    @Override
    public List<Token> tokens(String code) {
        return LEXER.tokens(code);
    }

    @Override
    public boolean readsInput(String code) {
        return code.matches("(?s).*\\b(cin|getline|scanf)\\b.*");
    }

    @Override
    public boolean readsInputOn(String line) {
        return line.matches("(?s).*(\\bcin\\b|\\b(getline|scanf|getchar)\\s*\\().*");
    }

    @Override
    public boolean usesFiles(String code) {
        return code.matches("(?s).*\\b(ifstream|ofstream|fstream|fopen)\\b.*");
    }

    @Override
    public int likeness(String code) {
        return Languages.count(code, "#include <iostream>", "std::", "cout", "cin", "using namespace std", "class ",
                "#include <vector>", "nullptr", "public:", "private:");
    }

    // Null when g++ and a debugger with Python are here (on a Mac, Apple's clang++, which answers to g++, and lldb).
    @Override
    public String cantRun() {
        if (Toolchains.find("g++") == null) {
            return Toolchains.howToInstall("g++ to compile C++");
        }
        if (!Toolchains.debuggerHasPython()) {
            return Toolchains.howToInstall(Toolchains.debugger() + " (with Python) to run C++");
        }
        return null;
    }

    @Override
    public Tracer newTracer() {
        return new CTracer();
    }

    @Override
    public CompileResult compile(String code) throws CompileSetupException {
        return new CCompiler(true).compile(code);
    }

    @Override
    public CompileResult compileFolder(List<SourceFile> files, String preferredMain) throws CompileSetupException {
        return new CCompiler(true).compileFolder(files, preferredMain);
    }

    @Override
    public String hint(String compilerMessage) {
        return GccHints.hint(compilerMessage);
    }
}
