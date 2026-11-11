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
        assertEquals(java.util.List.of("Welcome", "Untitled-1", "Bubble sort"), window.tabTitles());
        javax.swing.text.Document typed = window.active().getCode().getEditor().getDocument();
        try {
            typed.insertString(0, "// my change\n", null); // as if typed
        } catch (javax.swing.text.BadLocationException e) {
            throw new AssertionError(e);
        }
        window.loadExample("BSTInsert"); // the changed program stays, and the example gets a tab of its own
        assertEquals(4, window.tabTitles().size());
        window.closeProgram(); // BSTInsert, unchanged, closes without asking
        assertEquals(java.util.List.of("Welcome", "Untitled-1", "● Bubble sort"), window.tabTitles());
        window.reopenClosedTab();
        assertEquals("BST insert", window.tabTitles().get(3), "reopened with its title");
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

    // One click on an example previews it in a tab the next click reuses (its title in italics);
    // a double click keeps it, and the next preview gets a tab of its own.
    @Test
    void examplesPreviewThenKeep() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a screen");
        MainWindow window = new MainWindow();
        window.previewExample(raphael.io.ExampleCatalog.find("BubbleSort"));
        window.previewExample(raphael.io.ExampleCatalog.find("QuickSort"));
        assertEquals(java.util.List.of("Welcome", "<html><i>Quick sort</i></html>"), window.tabTitles(), "the preview was reused");
        window.keepExample(raphael.io.ExampleCatalog.find("QuickSort"));
        assertFalse(window.active().isPreview());
        window.previewExample(raphael.io.ExampleCatalog.find("MergeSort"));
        assertEquals(java.util.List.of("Welcome", "Quick sort", "<html><i>Merge sort</i></html>"), window.tabTitles());
        window.dispose();
    }

    // New in a language gives a tab in that language, from its template, and Ctrl+N repeats it.
    @Test
    void newProgramsHaveALanguage() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a screen");
        MainWindow window = new MainWindow();
        window.newProgram(raphael.lang.Languages.C);
        assertEquals(raphael.lang.Languages.C, window.active().getLanguage());
        assertTrue(window.active().getCode().getCode().contains("#include <stdio.h>"));
        window.newProgram(); // Ctrl+N: the last language again
        assertEquals(raphael.lang.Languages.C, window.active().getLanguage());
        window.newProgram(raphael.lang.Languages.JAVA); // leaves Java as the last one, for the other tests
        window.dispose();
    }

    // The examples side bar stays off the Welcome tab, which has its own example browser, and
    // comes back, as it was, when a program is in front.
    @Test
    void theSideBarIsOnlyBesideAProgram() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a screen");
        raphael.App.SETTINGS.put("language", "java"); // a language chosen, so the side bar is offered
        raphael.App.SETTINGS.putBoolean("examplesShown", true);
        MainWindow window = new MainWindow();
        assertFalse(window.examplesShowing(), "the Welcome tab is in front");
        window.toggleExamples(); // Ctrl+B does nothing there
        assertTrue(raphael.App.SETTINGS.getBoolean("examplesShown", false), "the choice is kept");
        window.newProgram();
        assertTrue(window.examplesShowing());
        window.showWelcome();
        assertFalse(window.examplesShowing());
        window.toggleExamples();
        window.newProgram();
        assertTrue(window.examplesShowing(), "Ctrl+B on the Welcome tab didn't change the choice");
        window.toggleExamples();
        assertFalse(window.examplesShowing());
        window.dispose();
    }

    // The Welcome tab shows the examples of the language chosen, or says it has none yet.
    @Test
    void theWelcomePageShowsEachLanguagesOwnExamples() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a screen");
        WelcomePage page = new WelcomePage(new WelcomePage.Actions(() -> { }, () -> { }, () -> { }, e -> { }, f -> { }, l -> { }));
        page.showExamplesOf(raphael.lang.Languages.byId("cpp"));
        assertEquals("cpp", page.shownLanguage().id());
        assertFalse(page.showsExampleList(), "C++ has no examples yet: a note, not Java's list");
        page.showExamplesOf(raphael.lang.Languages.byId("c"));
        assertTrue(page.showsExampleList());
    }

    // The key-moment choice is one for every tab: changing it in a tab's playback bar changes the
    // Run menu and every other tab, and it's remembered.
    @Test
    void theMomentChoiceIsSharedAndRemembered() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a screen");
        MomentMode before = MomentMode.current();
        MainWindow window = new MainWindow();
        try {
            window.newProgram();
            window.newProgram();
            MomentMode.set(MomentMode.OFF);
            assertTrue(window.momentChoice(MomentMode.OFF).isSelected());
            assertEquals("off", raphael.App.SETTINGS.get("moments", ""));
            MomentMode.set(MomentMode.PAUSE);
            assertTrue(window.momentChoice(MomentMode.PAUSE).isSelected());
            assertEquals(MomentMode.PAUSE, window.active().getControls().momentChoice());
        } finally {
            MomentMode.set(before);
            window.dispose();
        }
    }

    // A panel closed (by its × or View → Panels) is closed in every tab, and the menu says so;
    // the layout is remembered; Reset layout brings everything back.
    @Test
    void panelsCloseAndComeBackInEveryTab() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a screen");
        PanelLayout.reset();
        MainWindow window = new MainWindow();
        try {
            window.newProgram();
            Workbench first = window.active();
            window.newProgram();
            Workbench second = window.active();
            PanelLayout.show(PanelLayout.Panel.CALL_STACK, false);
            PanelLayout.show(PanelLayout.Panel.PICTURE, false);
            for (Workbench tab : java.util.List.of(first, second)) {
                assertFalse(tab.panelShowing(PanelLayout.Panel.CALL_STACK));
                assertFalse(tab.panelShowing(PanelLayout.Panel.PICTURE));
                assertTrue(tab.panelShowing(PanelLayout.Panel.VARIABLES));
            }
            assertFalse(window.panelCheck(PanelLayout.Panel.CALL_STACK).isSelected(), "the menu follows");
            assertFalse(raphael.App.SETTINGS.getBoolean("panel.call_stack", true), "remembered");
            PanelLayout.setPanelsRight(true);
            assertTrue(PanelLayout.panelsRight());
            PanelLayout.reset();
            for (PanelLayout.Panel panel : PanelLayout.Panel.values()) {
                assertTrue(second.panelShowing(panel), panel + " is back");
                assertTrue(window.panelCheck(panel).isSelected());
            }
            assertFalse(PanelLayout.panelsRight());
        } finally {
            PanelLayout.reset();
            window.dispose();
        }
    }
}
