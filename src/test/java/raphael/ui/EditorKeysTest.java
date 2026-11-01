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
}
