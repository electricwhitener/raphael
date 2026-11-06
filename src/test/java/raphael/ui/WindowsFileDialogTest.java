package raphael.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WindowsFileDialogTest {

    @Test
    void quotesSurviveInPowerShellStrings() {
        assertEquals("'O''Brien.java'", WindowsFileDialog.quote("O'Brien.java"));
    }

    @Test
    void saveSuggestsANameAndAddsDotJava() {
        String script = WindowsFileDialog.script(true, FileKind.CODE, "C:\\Users\\ada\\Desktop", "BSTInsert.java");
        assertTrue(script.contains("SaveFileDialog"));
        assertTrue(script.contains("$d.FileName = 'BSTInsert.java'"));
        assertTrue(script.contains("$d.DefaultExt = 'java'"));
        assertTrue(script.contains("$d.InitialDirectory = 'C:\\Users\\ada\\Desktop'"));
    }

    @Test
    void openShowsJavaFilesAndCanStartAnywhere() {
        String script = WindowsFileDialog.script(false, FileKind.CODE_OR_RUN, null, "ignored.java");
        assertTrue(script.contains("OpenFileDialog"));
        assertTrue(script.contains("$d.Filter = 'Java code and Raphael recordings (*.java;*.raphael)|*.java;*.raphael|All files (*.*)|*.*'"));
        assertFalse(script.contains("InitialDirectory"), "no folder: Windows picks its usual one");
    }
}
