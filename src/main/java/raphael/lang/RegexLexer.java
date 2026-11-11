package raphael.lang;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Splits code into the pieces that get a colour: comments, strings, numbers, keywords, type
// names (they start with a capital letter, or are the language's own types) and function names
// (followed by a "("). One regular expression per language, built from its parts: a comment or
// string is matched first and whole, so words inside it aren't mistaken for code.
// OOP: one class, configured differently for each language (composition instead of a subclass
// per lexer); immutable after it's made.
public final class RegexLexer {

    private final Pattern piece;
    private final Set<String> keywords;
    private final Set<String> types;

    // lineComment: "//" or "#". blockComments: /* … */. tripleQuotes: """…""" (Java text blocks,
    // Python) and '''…''' (Python). preprocessor: a line starting with # (C, C++) is one piece.
    public RegexLexer(String lineComment, boolean blockComments, boolean tripleQuotes, boolean preprocessor,
                      Set<String> keywords, Set<String> types) {
        this.keywords = keywords;
        this.types = types;
        StringBuilder comment = new StringBuilder(Pattern.quote(lineComment) + "[^\\n]*");
        if (blockComments) {
            comment.append("|/\\*.*?(?:\\*/|\\z)");
        }
        StringBuilder string = new StringBuilder();
        if (tripleQuotes) {
            string.append("\"\"\".*?(?:\"\"\"|\\z)|'''.*?(?:'''|\\z)|");
        }
        string.append("\"[^\"\\\\\\n]*+(?:\\\\.[^\"\\\\\\n]*+)*+\"?|'[^'\\\\\\n]*+(?:\\\\.[^'\\\\\\n]*+)*+'?");
        String directive = preprocessor ? "(?<directive>(?m:^[ \\t]*#[ \\t]*\\w+))|" : "";
        piece = Pattern.compile(directive + "(?<comment>" + comment + ")|(?<string>" + string + ")"
                + "|(?<word>[A-Za-z_$][\\w$]*)|(?<number>\\d[\\w.]*)", Pattern.DOTALL);
    }

    public List<Token> tokens(String code) {
        List<Token> tokens = new ArrayList<>();
        Matcher m = piece.matcher(code);
        boolean directives = piece.pattern().contains("?<directive>");
        while (m.find()) {
            Token.Kind kind;
            if (directives && m.group("directive") != null) {
                kind = Token.Kind.KEYWORD; // #include, #define
            } else if (m.group("comment") != null) {
                kind = Token.Kind.COMMENT;
            } else if (m.group("string") != null) {
                kind = Token.Kind.STRING;
            } else if (m.group("number") != null) {
                kind = Token.Kind.NUMBER;
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
    private Token.Kind kindOfWord(String word, String code, int end) {
        if (keywords.contains(word)) {
            return Token.Kind.KEYWORD;
        }
        if (types.contains(word) || Character.isUpperCase(word.charAt(0))) {
            return Token.Kind.TYPE;
        }
        int next = end;
        while (next < code.length() && code.charAt(next) == ' ') {
            next++;
        }
        return next < code.length() && code.charAt(next) == '(' ? Token.Kind.METHOD : null;
    }
}
