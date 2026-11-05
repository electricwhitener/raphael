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
import raphael.ui.SystemTheme;

// Starts Raphael: sets up the look, then opens the main window on Swing's event dispatch thread
// (the one thread that may touch Swing components).
public class App {

    // Remembered between runs: dark mode and text size. This is a JDK class.
    public static final Preferences SETTINGS = Preferences.userNodeForPackage(App.class);

    public static void main(String[] args) {
        AppLog.setup();          // the log file first, so everything after can write to it
        ErrorReporter.install(); // then the safety net, so even a problem while starting up is caught
        setupLook();
        SwingUtilities.invokeLater(() -> {
            MainWindow window = new MainWindow();
            ErrorReporter.setOwner(window);
            window.setVisible(true);
            window.offerDraft();
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
        FlatLaf.setGlobalExtraDefaults(Map.of(
                "@background", String.format("#%06x", Style.BACKGROUND.getRGB() & 0xFFFFFF),
                "@accentColor", String.format("#%06x", Style.ACCENT.getRGB() & 0xFFFFFF)));
        UIManager.put("defaultFont", Style.UI_FONT);
        if (dark) {
            FlatDarkLaf.setup();
        } else {
            FlatLightLaf.setup();
        }
        UIManager.put("Component.arc", 0);
        UIManager.put("Button.arc", 0);
        UIManager.put("TextComponent.arc", 0);
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("ScrollBar.thumbArc", 0);
        UIManager.put("ScrollBar.showButtons", false);
        UIManager.put("SplitPane.dividerSize", 6);
        UIManager.put("SplitPaneDivider.style", "plain");
        UIManager.put("ToolTip.font", Style.UI_FONT);
        UIManager.put("Table.intercellSpacing", new Dimension(0, 0));
    }
}
