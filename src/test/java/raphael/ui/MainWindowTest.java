package raphael.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.Container;
import java.awt.GraphicsEnvironment;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;

class MainWindowTest {

    // Swing offers a key to the panels around the focused one before the window's shortcuts. Scroll
    // panes and split panes normally take these keys, and then the shortcuts would never run.
    @Test
    void noPanelAroundThePictureTakesTheShortcutKeys() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a screen");
        MainWindow window = new MainWindow();
        window.newProgram(); // the window opens on the welcome page, which has no picture
        for (Container around = window.getPicture().getParent(); around != null; around = around.getParent()) {
            InputMap keys = around instanceof JComponent
                    ? ((JComponent) around).getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT) : null;
            if (keys != null) { // null means it takes no keys at all
                for (String key : new String[] {"LEFT", "RIGHT", "HOME", "END"}) {
                    assertNull(keys.get(KeyStroke.getKeyStroke(key)), around.getClass().getSimpleName() + " takes " + key);
                }
            }
        }
        window.dispose();
    }

    // Each program gets its own tab. New always adds one; an example opened over an untouched
    // program replaces it; a changed one keeps its tab and its unsaved dot; Ctrl+Shift+T reopens.
    @Test
    void programsOpenInTabs() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a screen");
        MainWindow window = new MainWindow();
        assertEquals(java.util.List.of("Welcome"), window.tabTitles());
        assertNull(window.active(), "nothing to run on the Welcome tab");
        window.newProgram();
        window.newProgram();
        assertEquals(java.util.List.of("Welcome", "Untitled-1", "Untitled-2"), window.tabTitles());
        window.loadExample("BubbleSort"); // Untitled-2 was never typed in, so the example takes its place
        assertEquals(java.util.List.of("Welcome", "Untitled-1", "BubbleSort.java (example)"), window.tabTitles());
        javax.swing.text.Document typed = window.active().getCode().getEditor().getDocument();
        try {
            typed.insertString(0, "// my change\n", null); // as if typed
        } catch (javax.swing.text.BadLocationException e) {
            throw new AssertionError(e);
        }
        window.loadExample("BSTInsert"); // the changed program stays, and the example gets a tab of its own
        assertEquals(4, window.tabTitles().size());
        window.closeProgram(); // BSTInsert, unchanged, closes without asking
        assertEquals(java.util.List.of("Welcome", "Untitled-1", "● BubbleSort.java (example)"), window.tabTitles());
        window.reopenClosedTab();
        assertEquals("BSTInsert.java (example)", window.tabTitles().get(3));
        window.dispose();
    }

    // The files panel is for programs that work with files, not every program that imports IOException.
    @Test
    void theFilesPanelIsForProgramsThatUseFiles() {
        assertTrue(MainWindow.usesFiles("try (PrintWriter out = new PrintWriter(\"a.txt\")) {}"));
        assertTrue(MainWindow.usesFiles("List<String> lines = Files.readAllLines(Path.of(\"a.txt\"));"));
        assertFalse(MainWindow.usesFiles("import java.io.IOException;\nclass A { void f() throws IOException {} }"));
        assertFalse(MainWindow.usesFiles("class Path { int length; }"));
    }

    // Save As offers the public class's name, which Java expects the file to have.
    @Test
    void saveSuggestsThePublicClassName() {
        assertEquals("BSTInsert.java", MainWindow.suggestedName("class Node {}\npublic class BSTInsert { }"));
        assertEquals("Node.java", MainWindow.suggestedName("class Node {}"));
        assertEquals("Main.java", MainWindow.suggestedName("// nothing yet"));
    }

    @Test
    void theLegendDrawsInBothThemes() {
        for (boolean dark : new boolean[] {false, true}) {
            raphael.render.Style.useTheme(dark);
            Legend legend = new Legend();
            legend.setSize(legend.getPreferredSize());
            java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(legend.getWidth(), legend.getHeight(),
                    java.awt.image.BufferedImage.TYPE_INT_RGB);
            legend.paint(image.getGraphics());
            assertTrue(legend.getPreferredSize().width < 520, "the key fits beside the status message");
        }
        raphael.render.Style.useTheme(false);
    }
}
