package raphael.lang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LexerTest {

    // Each coloured piece as "KIND:text".
    private static List<String> pieces(String code) {
        List<String> pieces = new ArrayList<>();
        for (Token t : Languages.JAVA.tokens(code)) {
            pieces.add(t.kind() + ":" + code.substring(t.start(), t.end()));
        }
        return pieces;
    }

    @Test
    void keywordsTypesMethodsAndNumbersEachGetTheirKind() {
        assertEquals(List.of("KEYWORD:int", "NUMBER:5"), pieces("int x = 5;"));
        assertEquals(List.of("TYPE:Node", "KEYWORD:new", "TYPE:Node", "NUMBER:1"), pieces("Node n = new Node(1);"));
        assertEquals(List.of("METHOD:insert"), pieces("root = insert (root);"));
    }

    @Test
    void wordsInsideCommentsAndStringsAreNotCode() {
        assertEquals(List.of("COMMENT:// int x"), pieces("// int x"));
        assertEquals(List.of("STRING:\"int \\\" x\""), pieces("\"int \\\" x\""));
        assertEquals(List.of("STRING:'\\n'"), pieces("'\\n'"));
        assertEquals(List.of("COMMENT:/* int\n x */", "KEYWORD:int"), pieces("/* int\n x */ int"));
    }

    @Test
    void unfinishedTextWhileTypingIsStillHandled() {
        assertEquals(List.of("COMMENT:/* int"), pieces("/* int"));
        assertEquals(List.of("STRING:\"abc", "KEYWORD:int"), pieces("\"abc\nint"));
        assertTrue(pieces("").isEmpty());
    }

    @Test
    void aNameWithDigitsStaysOneName() {
        assertEquals(List.of(), pieces("x1 = y2;"));
    }

    @Test
    void aVeryLongStringDoesNotOverflow() {
        assertEquals(1, Languages.JAVA.tokens("\"" + "\\n".repeat(50000) + "\"").size());
    }
}
