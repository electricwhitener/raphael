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
    void keyCapsShowTheMacSymbols() {
        assertEquals("⌘↩ or F5", Shortcuts.keycap("Ctrl+Enter or F5", true));
        assertEquals("⌥↓, ⌥↑", Shortcuts.keycap("Alt+Down, Alt+Up", true));
        assertEquals("⇧⌘Z", Shortcuts.keycap("Shift+Ctrl+Z", true));
        assertEquals("Ctrl+Enter or F5", Shortcuts.keycap("Ctrl+Enter or F5", false));
    }

    @Test
    void labelsUseTheMacKeyNames() {
        assertEquals("save  (Ctrl+S)", Shortcuts.label("save  (Ctrl+S)", false));
        assertEquals("save  (Cmd+S)", Shortcuts.label("save  (Ctrl+S)", true));
        assertEquals("press run (cmd+enter)", Shortcuts.label("press run (ctrl+enter)", true));
        assertEquals("skip call  (Option+↓)", Shortcuts.label("skip call  (Alt+↓)", true));
    }

    // The keys screen lists every shortcut once, with the Mac's key names on a Mac, and the note
    // about the editor as a note, not a row.
    @Test
    void theKeysScreenUsesThisComputersKeyNames() {
        for (boolean mac : new boolean[] {false, true}) {
            java.util.List<String[]> rows = MainWindow.helpRows(mac);
            assertTrue(rows.stream().anyMatch(r -> r[0].equals("note") && r[1].startsWith("In the editor")));
            assertTrue(rows.stream().filter(r -> r[0].equals("key")).allMatch(r -> r.length == 3 && !r[2].isBlank()));
            boolean saysCtrl = rows.stream().anyMatch(r -> String.join(" ", r).contains("Ctrl"));
            assertTrue(mac != saysCtrl, "mac=" + mac);
            assertTrue(mac == rows.stream().anyMatch(r -> r[0].equals("key") && r[1].contains("⌘")), "mac=" + mac);
        }
    }
}
