package raphael;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import java.awt.Dimension;
import java.util.Map;
import java.util.prefs.Preferences;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import raphael.io.AppLog;
import raphael.render.Style;
import raphael.ui.ErrorReporter;
import raphael.ui.MainWindow;
import raphael.ui.Windows;
import raphael.ui.Shortcuts;
import raphael.ui.SystemTheme;

// Starts Raphael: sets up the look, then opens the main window on Swing's event dispatch thread
// (the one thread that may touch Swing components).
public class App {

    // Remembered between runs: dark mode and text size. This is a JDK class.
    public static final Preferences SETTINGS = Preferences.userNodeForPackage(App.class);

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--self-test")) { // used to check the installers
            System.setProperty("java.awt.headless", "true");
            System.exit(SelfTest.run());
        }
        if (Shortcuts.isMac()) { // read by Java when the window system starts, so first of all
            System.setProperty("apple.awt.application.appearance", "system"); // the title bar follows dark mode
            System.setProperty("apple.awt.application.name", "Raphael");
            System.setProperty("apple.laf.useScreenMenuBar", "true"); // the menus go in the bar at the top of the screen
        }
        AppLog.setup();          // the log file first, so everything after can write to it
        ErrorReporter.install(); // then the safety net, so even a problem while starting up is caught
        setupLook();
        SwingUtilities.invokeLater(() -> {
            MainWindow window = Windows.openNew();
            Windows.installAppMenu(); // the Mac's Quit, About and Settings, and double-clicked files
            window.offerDraft();
            // A file to open: given by the system when a .raphael file is double-clicked, or
            // typed after the command (Raphael scores.java).
            if (args.length > 0 && !args[0].startsWith("-")) {
                window.openFromSystem(new java.io.File(args[0]));
            }
        });
    }

    // The look the user last chose. The first time, before they have chosen, match the computer.
    public static void setupLook() {
        String chosen = SETTINGS.get("dark", null);
        setupLook(chosen != null ? Boolean.parseBoolean(chosen) : SystemTheme.isDark());
        Style.setCodeSize(SETTINGS.getInt("textSize", Style.DEFAULT_CODE_SIZE));
    }

    // FlatLaf, adjusted to Raphael's terminal-like look: a plain background, square corners,
    // thin lines, one monospace font, and the blue accent.
    public static void setupLook(boolean dark) {
        Style.useTheme(dark);
        // Map.ofEntries, because Map.of takes at most 10 pairs.
        FlatLaf.setGlobalExtraDefaults(Map.ofEntries(
                Map.entry("@background", hex(Style.BACKGROUND)),
                Map.entry("@accentColor", hex(Style.ACCENT)),
                Map.entry("Component.borderColor", hex(Style.CONTROL_BORDER)),
                Map.entry("Button.toolbar.hoverBackground", hex(Style.HOVER)),
                Map.entry("Button.toolbar.pressedBackground", hex(Style.PRESSED)),
                // The main button of every dialog is filled blue in both themes (FlatLaf's light
                // theme only outlines it), so the one obvious action is obvious.
                Map.entry("Button.default.background", hex(Style.ACCENT)),
                Map.entry("Button.default.focusedBackground", hex(Style.ACCENT)),
                Map.entry("Button.default.borderColor", hex(Style.ACCENT)),
                Map.entry("Button.default.foreground", hex(Style.ON_ACCENT)),
                Map.entry("Button.default.borderWidth", "1"),
                Map.entry("TitlePane.background", hex(Style.BAR)), // Windows: the title bar matches the toolbar
                Map.entry("TitlePane.inactiveBackground", hex(Style.BAR)),
                Map.entry("TitlePane.foreground", hex(Style.INK)),
                Map.entry("TitlePane.inactiveForeground", hex(Style.MUTED)),
                Map.entry("MenuBar.background", hex(Style.BAR)),
                // The program tabs are part of the window's frame, like the toolbar: the strip on
                // BAR, the selected tab raised to the code's colour with an ink underline (blue
                // means "about to happen", so it isn't used for "selected"), the others muted.
                Map.entry("TabbedPane.background", hex(Style.BAR)),
                Map.entry("TabbedPane.selectedBackground", hex(Style.BACKGROUND)),
                Map.entry("TabbedPane.foreground", hex(Style.MUTED)),
                Map.entry("TabbedPane.selectedForeground", hex(Style.INK)),
                Map.entry("TabbedPane.underlineColor", hex(Style.INK)),
                Map.entry("TabbedPane.inactiveUnderlineColor", hex(Style.MUTED)),
                Map.entry("TabbedPane.hoverColor", hex(Style.HOVER)),
                Map.entry("TabbedPane.tabSeparatorColor", hex(Style.HAIRLINE)),
                Map.entry("TabbedPane.contentAreaColor", hex(Style.HAIRLINE)),
                Map.entry("TabbedPane.tabSelectionHeight", "2"),
                // A switched-on toolbar toggle (the find bar's Aa, W and .*) is filled grey.
                Map.entry("ToggleButton.toolbar.selectedBackground", hex(Style.PRESSED))));
        if (Style.defaultUiFont() != null) { // Linux: the bundled Inter; elsewhere FlatLaf's system font
            UIManager.put("defaultFont", Style.defaultUiFont());
        }
        if (dark) {
            FlatDarkLaf.setup();
        } else {
            FlatLightLaf.setup();
        }
        UIManager.put("Component.arc", Style.ARC);
        UIManager.put("Button.arc", Style.ARC);
        UIManager.put("TextComponent.arc", Style.ARC);
        UIManager.put("Component.focusWidth", Style.FOCUS_WIDTH); // a visible ring on the focused control
        UIManager.put("ScrollBar.width", 12);
        UIManager.put("ScrollBar.thumbArc", 0);
        UIManager.put("ScrollBar.showButtons", false);
        UIManager.put("SplitPane.dividerSize", 8);
        UIManager.put("SplitPaneDivider.style", "plain");
        UIManager.put("Table.intercellSpacing", new Dimension(0, 0));
    }

    private static String hex(java.awt.Color color) {
        return String.format("#%06x", color.getRGB() & 0xFFFFFF);
    }
}
