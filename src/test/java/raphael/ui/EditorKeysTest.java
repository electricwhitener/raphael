package raphael.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.swing.JTextArea;
import org.junit.jupiter.api.Test;

class EditorKeysTest {

    private final JTextArea editor = new JTextArea();
    private final EditorKeys keys = new EditorKeys(editor);

    private void set(String text, int selectionStart, int selectionEnd) {
        editor.setText(text);
        editor.select(selectionStart, selectionEnd);
    }

    @Test
    void enterKeepsTheIndentationAndGoesDeeperAfterABrace() {
        set("    int x;", 10, 10);
        keys.newLine();
        assertEquals("    int x;\n    ", editor.getText());
        set("void f() {", 10, 10);
        keys.newLine();
        assertEquals("void f() {\n    ", editor.getText());
    }

    @Test
    void aClosingBraceLinesUpWithItsBlock() {
        set("if (a) {\n    ", 13, 13);
        keys.closeBrace();
        assertEquals("if (a) {\n}", editor.getText());
        set("int[] a = {1", 12, 12);
        keys.closeBrace();
        assertEquals("int[] a = {1}", editor.getText());
    }

    @Test
    void tabMovesSelectedLinesInAndShiftTabMovesThemBack() {
        set("a\nb\nc", 0, 3);
        keys.indent();
        assertEquals("    a\n    b\nc", editor.getText());
        keys.unindent();
        assertEquals("a\nb\nc", editor.getText());
    }

    @Test
    void tabWithNoSelectionTypesAnIndent() {
        set("x", 0, 0);
        keys.indent();
        assertEquals("    x", editor.getText());
    }

    @Test
    void ctrlSlashCommentsAndUncomments() {
        set("    a();\n\n    b();", 0, 18);
        keys.toggleComment();
        assertEquals("    // a();\n\n    // b();", editor.getText());
        keys.toggleComment();
        assertEquals("    a();\n\n    b();", editor.getText());
        set("a();\nb();", 1, 1); // no selection: only the cursor's line
        keys.toggleComment();
        assertEquals("// a();\nb();", editor.getText());
    }

    @Test
    void findMovesToTheNextMatchAndWrapsRound() {
        set("Node a; node b;", 0, 0);
        assertTrue(keys.find("node"));
        assertEquals(0, editor.getSelectionStart());
        assertTrue(keys.find("node"));
        assertEquals(8, editor.getSelectionStart());
        assertTrue(keys.find("node"));
        assertEquals(0, editor.getSelectionStart());
        assertFalse(keys.find("missing"));
    }

    // A bracket brings its partner (not in front of a word), and typing the partner steps over it.
    @Test
    void bracketsAndQuotesCloseThemselves() {
        set("f", 1, 1);
        keys.typeOpener('(', ')');
        assertEquals("f()", editor.getText());
        assertEquals(2, editor.getCaretPosition(), "the cursor goes between");
        keys.typeCloser(')');
        assertEquals("f()", editor.getText(), "typing the closer steps over it");
        assertEquals(3, editor.getCaretPosition());
        set("word", 0, 0);
        keys.typeOpener('(', ')');
        assertEquals("(word", editor.getText(), "no closer in front of a word");
        set("x = ", 4, 4);
        keys.typeQuote('"');
        assertEquals("x = \"\"", editor.getText());
        keys.typeQuote('"');
        assertEquals("x = \"\"", editor.getText(), "the second quote steps over the first's partner");
        set("don", 3, 3);
        keys.typeQuote('\'');
        assertEquals("don'", editor.getText(), "an apostrophe after a letter stays alone");
    }

    // Enter between { and } opens the block: the cursor indented inside, the } on its own line.
    @Test
    void enterBetweenBracesOpensTheBlock() {
        set("void f() {}", 10, 10);
        keys.newLine();
        assertEquals("void f() {\n    \n}", editor.getText());
        assertEquals(15, editor.getCaretPosition());
    }

    @Test
    void aLineCanBeDuplicated() {
        set("a\nb\n", 2, 2);
        keys.duplicateLines();
        assertEquals("a\nb\nb\n", editor.getText());
        assertEquals(4, editor.getCaretPosition(), "the cursor is on the copy");
    }
}
