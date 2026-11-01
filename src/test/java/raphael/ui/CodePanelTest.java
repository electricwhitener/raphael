package raphael.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CodePanelTest {

    @Test
    void undoAndRedoStepThroughEditsButNotThroughLoading() {
        CodePanel panel = new CodePanel(new TraceModel());
        panel.setCode("loaded");
        panel.getEditor().append(" typed");
        assertEquals("loaded typed", panel.getCode());

        panel.undo();
        assertEquals("loaded", panel.getCode());
        panel.redo();
        assertEquals("loaded typed", panel.getCode());

        panel.undo();
        panel.undo(); // nothing left to undo: the loaded text stays
        assertEquals("loaded", panel.getCode());
    }

    @Test
    void undoDoesNothingWhileTheEditorIsLocked() {
        CodePanel panel = new CodePanel(new TraceModel());
        panel.setCode("loaded");
        panel.getEditor().append(" typed");
        panel.setEditable(false); // a run is in progress
        panel.undo();
        assertEquals("loaded typed", panel.getCode());
    }

    @Test
    void dirtyMeansChangedSinceLoadingOrSaving() {
        CodePanel panel = new CodePanel(new TraceModel());
        panel.setCode("loaded");
        assertFalse(panel.isDirty());
        panel.getEditor().append(" typed");
        assertTrue(panel.isDirty());
        panel.markSaved();
        assertFalse(panel.isDirty());
    }

    @Test
    void aLongerLineNeedsMoreWidth() {
        CodePanel panel = new CodePanel(new TraceModel());
        panel.setCode("short");
        int shortWidth = panel.widthForLongestLine();
        panel.setCode("short\n" + "x".repeat(80) + "\nshort");
        assertTrue(panel.widthForLongestLine() > shortWidth + 70);
    }
}
