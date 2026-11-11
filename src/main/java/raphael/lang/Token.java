package raphael.lang;

// One coloured piece of code in the editor: what kind it is, and where it starts and ends.
// OOP: a record with a nested enum, shared by every language's lexer.
public record Token(Kind kind, int start, int end) {

    public enum Kind { COMMENT, STRING, NUMBER, KEYWORD, TYPE, METHOD }
}
