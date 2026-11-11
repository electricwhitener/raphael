package raphael.lang;

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

// C: the language most data-structures courses are taught in. Compiled with gcc (CCompiler) and
// traced through gdb (CTracer).
// OOP: inheritance (a Language) and overriding.
public class CLanguage extends Language {

    static final Set<String> KEYWORDS = Set.of("auto", "break", "case", "char", "const", "continue", "default", "do",
            "double", "else", "enum", "extern", "float", "for", "goto", "if", "inline", "int", "long", "register",
            "restrict", "return", "short", "signed", "sizeof", "static", "struct", "switch", "typedef", "union",
            "unsigned", "void", "volatile", "while", "bool", "true", "false", "NULL");
    static final Set<String> TYPES = Set.of("size_t", "FILE", "int8_t", "int16_t", "int32_t", "int64_t", "uint8_t",
            "uint16_t", "uint32_t", "uint64_t");
    private static final RegexLexer LEXER = new RegexLexer("//", true, false, true, KEYWORDS, TYPES);

    @Override
    public String id() {
        return "c";
    }

    @Override
    public String name() {
        return "C";
    }

    @Override
    public List<String> endings() {
        return List.of(".c", ".h");
    }

    @Override
    public String lineComment() {
        return "//";
    }

    @Override
    public String template() { // \s keeps the indentation a text block would strip from the empty line
        return """
                #include <stdio.h>

                int main(void) {
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
        return code.matches("(?s).*\\b(scanf|getchar|fgets|gets|getline)\\s*\\(.*");
    }

    @Override
    public boolean usesFiles(String code) {
        return code.matches("(?s).*\\bfopen\\s*\\(.*");
    }

    // Null when the compiler and a debugger with Python are here (gcc and gdb; on a Mac, Apple's
    // clang, which answers to gcc too, and lldb).
    @Override
    public String cantRun() {
        if (Toolchains.find("gcc") == null) {
            return Toolchains.howToInstall("gcc to compile C");
        }
        if (!Toolchains.debuggerHasPython()) {
            return Toolchains.howToInstall(Toolchains.debugger() + " (with Python) to run C");
        }
        return null;
    }

    @Override
    public Tracer newTracer() {
        return new CTracer();
    }

    @Override
    public CompileResult compile(String code) throws CompileSetupException {
        return new CCompiler().compile(code);
    }

    @Override
    public CompileResult compileFolder(List<SourceFile> files, String preferredMain) throws CompileSetupException {
        return new CCompiler().compileFolder(files, preferredMain);
    }

    @Override
    public String hint(String compilerMessage) {
        return GccHints.hint(compilerMessage);
    }

    @Override
    public int likeness(String code) {
        return Languages.count(code, "#include <stdio.h>", "#include <stdlib.h>", "printf(", "scanf(", "malloc(",
                "struct ", "->", "int main(") - 3 * Languages.count(code, "cout", "std::", "class ", "#include <iostream>");
    }
}
