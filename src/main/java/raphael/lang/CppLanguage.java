package raphael.lang;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

// C++: C with classes, templates and the standard library.
// OOP: inheritance (a Language, through NotRunnableYet until Stage 15) and overriding.
public class CppLanguage extends NotRunnableYet {

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
    public boolean usesFiles(String code) {
        return code.matches("(?s).*\\b(ifstream|ofstream|fstream|fopen)\\b.*");
    }

    @Override
    public int likeness(String code) {
        return Languages.count(code, "#include <iostream>", "std::", "cout", "cin", "using namespace std", "class ",
                "#include <vector>", "nullptr", "public:", "private:");
    }
}
