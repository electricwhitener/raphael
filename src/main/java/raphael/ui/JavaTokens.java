package raphael.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Splits Java code into the pieces that get a colour in the editor: comments, strings, numbers,
// keywords, type names (they start with a capital letter) and method names (followed by a "(").
// The order inside the pattern matters: a comment or string is matched first and whole, so words
// inside it aren't mistaken for code.
// OOP: a utility class with a nested enum and a nested record; the private constructor stops
// anyone making an object of it.
public final class JavaTokens {

    public enum Kind { COMMENT, STRING, NUMBER, KEYWORD, TYPE, METHOD }

    // One coloured piece: where it starts and where it ends in the text.
    public record Token(Kind kind, int start, int end) {
    }

    private static final Pattern PIECE = Pattern.compile(
            "(?<comment>//[^\\n]*|/\\*.*?(?:\\*/|\\z))"
            + "|(?<string>\"\"\".*?(?:\"\"\"|\\z)"
            + "|\"[^\"\\\\\\n]*+(?:\\\\.[^\"\\\\\\n]*+)*+\"?"
            + "|'[^'\\\\\\n]*+(?:\\\\.[^'\\\\\\n]*+)*+'?)"
            + "|(?<word>[A-Za-z_$][\\w$]*)"
            + "|(?<number>\\d[\\w.]*)",
            Pattern.DOTALL);

    private static final Set<String> KEYWORDS = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue",
            "default", "do", "double", "else", "enum", "extends", "false", "final", "finally", "float", "for", "goto",
            "if", "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "null", "package",
            "private", "protected", "public", "record", "return", "short", "static", "strictfp", "super", "switch",
            "synchronized", "this", "throw", "throws", "transient", "true", "try", "var", "void", "volatile", "while");

    private JavaTokens() {
    }

    public static List<Token> of(String code) {
        List<Token> tokens = new ArrayList<>();
        Matcher m = PIECE.matcher(code);
        while (m.find()) {
            Kind kind;
            if (m.group("comment") != null) {
                kind = Kind.COMMENT;
            } else if (m.group("string") != null) {
                kind = Kind.STRING;
            } else if (m.group("number") != null) {
                kind = Kind.NUMBER;
            } else {
                kind = kindOfWord(m.group("word"), code, m.end());
            }
            if (kind != null) {
                tokens.add(new Token(kind, m.start(), m.end()));
            }
        }
        return tokens;
    }

    // What a word is, or null for a plain name like a variable, which keeps the normal colour.
    private static Kind kindOfWord(String word, String code, int end) {
        if (KEYWORDS.contains(word)) {
            return Kind.KEYWORD;
        }
        if (Character.isUpperCase(word.charAt(0))) {
            return Kind.TYPE;
        }
        int next = end;
        while (next < code.length() && code.charAt(next) == ' ') {
            next++;
        }
        return next < code.length() && code.charAt(next) == '(' ? Kind.METHOD : null;
    }
}
