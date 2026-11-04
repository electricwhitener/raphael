package raphael.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;

class ShortcutsTest {

    @Test
    void ctrlEverywhereButMac() {
        assertEquals(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK), Shortcuts.key("control S", false));
        assertEquals(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.META_DOWN_MASK), Shortcuts.key("control S", true));
        assertEquals(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.SHIFT_DOWN_MASK | InputEvent.META_DOWN_MASK),
                Shortcuts.key("shift control Z", true));
    }

    @Test
    void keysWithoutCtrlStayTheSame() {
        assertEquals(KeyStroke.getKeyStroke("alt DOWN"), Shortcuts.key("alt DOWN", true));
        assertEquals(KeyStroke.getKeyStroke("F5"), Shortcuts.key("F5", true));
    }

    @Test
    void labelsUseTheMacKeyNames() {
        assertEquals("save  (Ctrl+S)", Shortcuts.label("save  (Ctrl+S)", false));
        assertEquals("save  (Cmd+S)", Shortcuts.label("save  (Ctrl+S)", true));
        assertEquals("press run (cmd+enter)", Shortcuts.label("press run (ctrl+enter)", true));
        assertEquals("skip call  (Option+↓)", Shortcuts.label("skip call  (Alt+↓)", true));
    }

    // Every description in the keys screen starts in the same column, on a Mac too, where the
    // key names have a different length.
    @Test
    void helpColumnsStayLinedUp() {
        for (boolean mac : new boolean[] {false, true}) {
            String help = MainWindow.helpText(mac);
            int column = help.indexOf("run; while recording"); // where the first description starts
            column -= help.lastIndexOf('\n', column) + 1;
            for (String line : help.split("\n")) {
                if (line.startsWith("  ") && line.trim().contains("  ")) { // a "keys   what they do" line
                    assertTrue(line.charAt(column - 1) == ' ' && line.charAt(column) != ' ', "mac=" + mac + ": " + line);
                }
            }
        }
        assertFalse(MainWindow.helpText(true).contains("Ctrl"));
    }
}
