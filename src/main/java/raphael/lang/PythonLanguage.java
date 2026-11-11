package raphael.lang;

import java.util.List;
import java.util.Set;

// Python: blocks by indentation, # comments, no braces.
// OOP: inheritance (a Language, through NotRunnableYet until Stage 16) and overriding.
public class PythonLanguage extends NotRunnableYet {

    private static final RegexLexer LEXER = new RegexLexer("#", false, true, false, Set.of(
            "False", "None", "True", "and", "as", "assert", "async", "await", "break", "class", "continue", "def",
            "del", "elif", "else", "except", "finally", "for", "from", "global", "if", "import", "in", "is",
            "lambda", "nonlocal", "not", "or", "pass", "raise", "return", "try", "while", "with", "yield", "self"),
            Set.of("int", "float", "str", "bool", "list", "dict", "set", "tuple"));

    @Override
    public String id() {
        return "python";
    }

    @Override
    public String name() {
        return "Python";
    }

    @Override
    public List<String> endings() {
        return List.of(".py");
    }

    @Override
    public String lineComment() {
        return "#";
    }

    @Override
    public boolean blocksByIndent() {
        return true;
    }

    @Override
    public String template() { // \s keeps the indentation a text block would strip from the empty line
        return """
                def main():
                   \s


                main()
                """;
    }

    @Override
    public List<Token> tokens(String code) {
        return LEXER.tokens(code);
    }

    @Override
    public boolean readsInput(String code) {
        return code.matches("(?s).*\\binput\\s*\\(.*");
    }

    @Override
    public boolean usesFiles(String code) {
        return code.matches("(?s).*\\bopen\\s*\\(.*");
    }

    @Override
    public int likeness(String code) {
        return Languages.count(code, "def ", "print(", "self", "elif ", "import ", "None", "__init__", "range(", "):\n")
                - 3 * Languages.count(code, ";\n", "{\n");
    }
}
