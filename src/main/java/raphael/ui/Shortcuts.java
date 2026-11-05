package raphael.ui;

import java.awt.event.InputEvent;
import java.util.Locale;

import javax.swing.KeyStroke;

// The keys shortcuts use on this computer. Everywhere but macOS they use Ctrl. A Mac uses Cmd
// (Swing calls it "meta") for the same shortcuts, so Cmd+S saves there, as Mac users expect.
// Shortcuts are written once, the Windows way ("control S"), and translated here.
// OOP: a utility class (static methods, private constructor), with overloading: key(spec) and
// key(spec, mac) share a name and differ in their parameters.
public final class Shortcuts {

    private static final boolean MAC = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("mac");
    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");

    private Shortcuts() {
    }

    public static boolean isMac() {
        return MAC;
    }

    public static boolean isWindows() {
        return WINDOWS;
    }

    // "control S" → the KeyStroke for Ctrl+S here, or Cmd+S on a Mac.
    public static KeyStroke key(String spec) {
        return key(spec, MAC);
    }

    static KeyStroke key(String spec, boolean mac) {
        return KeyStroke.getKeyStroke(mac ? spec.replace("control", "meta") : spec);
    }

    // Text shown to the user. On a Mac, "Ctrl+S" becomes "Cmd+S" ("ctrl+s" becomes "cmd+s"), and
    // "Alt" becomes "Option", which is what the Mac keyboard calls that key.
    public static String label(String text) {
        return label(text, MAC);
    }

    static String label(String text, boolean mac) {
        if (!mac) {
            return text;
        }
        return text.replace("Ctrl", "Cmd").replace("ctrl", "cmd").replace("Alt", "Option");
    }

    // Keys inside a key-cap chip. On a Mac they're the symbols on the keys: "Ctrl+Enter" becomes
    // ⌘↩ and "Alt+Down" becomes ⌥↓, with no "+". Elsewhere they're as written. (Sentences keep the
    // words, through label().)
    public static String keycap(String keys) {
        return keycap(keys, MAC);
    }

    static String keycap(String keys, boolean mac) {
        if (!mac) {
            return keys;
        }
        return keys.replace("Ctrl+", "⌘").replace("Alt+", "⌥").replace("Shift+", "⇧")
                .replace("Ctrl ", "⌘ ").replace("Enter", "↩")
                .replace("Down", "↓").replace("Up", "↑").replace("Right", "→").replace("Left", "←");
    }

    // Whether the shortcut key is held during a mouse event, for Ctrl + mouse wheel. A Mac
    // accepts both Cmd and Ctrl.
    public static boolean isDown(InputEvent e) {
        return e.isControlDown() || (MAC && e.isMetaDown());
    }
}
