package raphael.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import javax.swing.JTextArea;
import org.junit.jupiter.api.Test;

// Run on Swing's thread, as in the app: the bar refreshes itself there after each edit.
class FindBarTest {

    private final CodePanel code = new CodePanel(new TraceModel());
    private final JTextArea editor = code.getEditor();

    private FindBar bar() {
        for (java.awt.Component part : code.getComponents()) {
            if (part instanceof FindBar) {
                return (FindBar) part;
            }
        }
        throw new AssertionError("no find bar");
    }

    @Test
    void countsMatchesAndMovesBetweenThem() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(this::countsMatchesAndMovesBetweenThemOnSwing); // as in the app, on Swing's thread
    }

    private void countsMatchesAndMovesBetweenThemOnSwing() {
        code.setCode("int count = 0;\ncount++;\nCount the counts");
        FindBar bar = bar();
        code.openFind(false);
        bar.findField().setText("count");
        assertEquals("1 of 4", bar.countText(), "typing selects the first match");
        code.findNext(false);
        assertEquals("2 of 4", bar.countText());
        code.findNext(true);
        code.findNext(true);
        assertEquals("4 of 4", bar.countText(), "going back from the first wraps round to the last");
    }

    @Test
    void matchCaseAndWholeWordNarrowTheMatches() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(this::matchCaseAndWholeWordNarrowTheMatchesOnSwing); // as in the app, on Swing's thread
    }

    private void matchCaseAndWholeWordNarrowTheMatchesOnSwing() {
        code.setCode("count Count counts");
        FindBar bar = bar();
        code.openFind(false);
        bar.findField().setText("count");
        assertEquals("1 of 3", bar.countText());
        option(bar, "Whole word").doClick();
        assertEquals("1 of 2", bar.countText(), "counts is another word");
        option(bar, "Match case").doClick();
        assertEquals("1 of 1", bar.countText(), "Count has a capital");
    }

    private static javax.swing.JToggleButton option(FindBar bar, String name) {
        for (java.awt.Component part : allIn(bar)) {
            if (part instanceof javax.swing.JToggleButton && name.equals(part.getAccessibleContext().getAccessibleName())) {
                return (javax.swing.JToggleButton) part;
            }
        }
        throw new AssertionError("no option " + name);
    }

    // Replace All is one edit: one undo brings the old text back.
    @Test
    void replaceAllIsUndoneInOneStep() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(this::replaceAllIsUndoneInOneStepOnSwing); // as in the app, on Swing's thread
    }

    private void replaceAllIsUndoneInOneStepOnSwing() {
        code.setCode("a = a + a;");
        FindBar bar = bar();
        code.openFind(true);
        bar.findField().setText("a");
        bar.replaceField().setText("b");
        for (java.awt.Component part : allIn(bar)) {
            if (part instanceof javax.swing.JButton && "All".equals(((javax.swing.JButton) part).getText())) {
                ((javax.swing.JButton) part).doClick();
            }
        }
        assertEquals("b = b + b;", editor.getText());
        code.undo();
        assertEquals("a = a + a;", editor.getText());
    }

    private static java.util.List<java.awt.Component> allIn(java.awt.Container container) {
        java.util.List<java.awt.Component> all = new java.util.ArrayList<>();
        for (java.awt.Component part : container.getComponents()) {
            all.add(part);
            if (part instanceof java.awt.Container) {
                all.addAll(allIn((java.awt.Container) part));
            }
        }
        return all;
    }
}
