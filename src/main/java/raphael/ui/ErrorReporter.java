package raphael.ui;

import java.awt.Component;
import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import raphael.io.AppLog;

// The safety net for problems Raphael didn't expect: a bug in Raphael, not in the student's
// code. Without it, an exception on Swing's thread prints to a console nobody sees, and the
// window just stops doing something. With it, the problem is written to the log, and the user
// gets a dialog: what happened, that their code is safe, and where the details are.
// install() makes report() Java's default handler for exceptions that nothing catches, on every thread
// (Swing's thread included). The window also calls report() itself for a run that failed in an
// unexpected way.
// OOP: a utility class (static members, private constructor); a lambda implements Java's
// Thread.UncaughtExceptionHandler interface; exception handling: catch (Throwable) so the
// safety net itself can never fail.
public final class ErrorReporter {

    private static final Logger LOG = Logger.getLogger(ErrorReporter.class.getName());
    private static final long QUIET_MS = 10_000; // a repeating problem shows at most one dialog per 10 seconds

    static boolean dialogs = true; // tests turn the dialogs off: nobody is there to click them
    private static Component owner;
    private static boolean showing;
    private static long lastShown = -QUIET_MS;

    private ErrorReporter() {
    }

    // Called first thing when Raphael starts, so even a problem while the window is being built is caught.
    public static void install() {
        Thread.setDefaultUncaughtExceptionHandler((thread, e) -> report(e));
    }

    // The window the dialogs belong to, once it exists.
    public static void setOwner(Component window) {
        owner = window;
    }

    public static void report(Throwable e) {
        try {
            LOG.log(Level.SEVERE, "Unexpected problem", e);
            if (dialogs && !GraphicsEnvironment.isHeadless()) {
                SwingUtilities.invokeLater(() -> show(e));
            }
        } catch (Throwable ignored) {
            // Logging or queueing failed too. Nothing more can be done, and the safety net must not throw.
        }
    }

    // On Swing's thread.
    private static void show(Throwable e) {
        long now = System.currentTimeMillis();
        if (showing || now - lastShown < QUIET_MS) {
            return; // it's in the log; one dialog at a time
        }
        showing = true;
        try {
            Path logs = AppLog.folder();
            String message = "Raphael ran into a problem it didn't expect.\n"
                    + "Your code is safe: Raphael keeps an autosaved copy.\n\n"
                    + e + (logs != null ? "\n\nThe details are in the log, in\n" + logs : "");
            Object[] options = logs != null
                    ? new Object[] {"copy details", "open log folder", "ok"}
                    : new Object[] {"copy details", "ok"};
            int choice = JOptionPane.showOptionDialog(owner, message, "raphael · something went wrong",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE, null, options, options[options.length - 1]);
            if (choice == 0) {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(details(e)), null);
            } else if (choice == 1 && logs != null) {
                openFolder(logs);
            }
        } finally {
            showing = false;
            lastShown = System.currentTimeMillis();
        }
    }

    // What to paste into a bug report: the versions, then the full stack trace.
    static String details(Throwable e) {
        StringWriter text = new StringWriter();
        text.append("Raphael on Java ").append(System.getProperty("java.version")).append(", ")
                .append(System.getProperty("os.name")).append(' ').append(System.getProperty("os.version"))
                .append(System.lineSeparator());
        e.printStackTrace(new PrintWriter(text));
        return text.toString();
    }

    private static void openFolder(Path folder) {
        try {
            Desktop.getDesktop().open(folder.toFile());
        } catch (IOException | UnsupportedOperationException | IllegalArgumentException e) {
            LOG.log(Level.INFO, "Couldn't open the log folder", e);
            JOptionPane.showMessageDialog(owner, "The log folder is:\n" + folder);
        }
    }
}
