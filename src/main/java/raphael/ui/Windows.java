package raphael.ui;

import java.awt.Desktop;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;

// Raphael's open windows. File → New Window opens another one; closing one closes only it, and
// closing the last one (or Quit) ends Raphael, after every window has asked about unsaved work.
// The things there's one of for the whole app go through here: the Mac's Quit, About and
// Settings in the app menu, files the system asks Raphael to open, and changes that every
// window must follow (the theme, the text size, the recent files).
// OOP: a utility class (static members, private constructor) holding the one list of windows, so
// any window can reach the others.
public final class Windows {

    private static final Logger LOG = Logger.getLogger(Windows.class.getName());
    private static final List<MainWindow> OPEN = new ArrayList<>();
    private static MainWindow front; // the window used last, which the app menu talks to

    private Windows() {
    }

    // A new window, shown a little below and to the right of the one in front.
    public static MainWindow openNew() {
        MainWindow window = new MainWindow();
        if (front != null) {
            window.setLocation(front.getX() + 32, front.getY() + 32);
        }
        OPEN.add(window);
        front = window;
        window.setVisible(true);
        return window;
    }

    // The window in front, or null if none is open.
    public static MainWindow front() {
        return front;
    }

    // The open windows, oldest first.
    static List<MainWindow> all() {
        return new ArrayList<>(OPEN);
    }

    // A window came to the front: the app menu and the crash dialog belong to it now.
    static void focused(MainWindow window) {
        if (OPEN.contains(window)) {
            front = window;
        }
        ErrorReporter.setOwner(window);
    }

    // A window was closed (its unsaved work already asked about). The last one ends Raphael.
    static void closed(MainWindow window) {
        OPEN.remove(window);
        window.forgetDrafts();
        window.dispose();
        if (OPEN.isEmpty()) {
            exit();
        } else if (front == window) {
            front = OPEN.get(OPEN.size() - 1);
            front.toFront();
        }
    }

    // Quit: each window asks about its unsaved work; if every one is fine, Raphael ends.
    // False if the user kept one open (Cancel).
    public static boolean quitAll() {
        for (MainWindow window : all()) {
            if (!window.okToLoseAll()) {
                return false;
            }
        }
        for (MainWindow window : all()) {
            window.forgetDrafts();
        }
        exit();
        return true;
    }

    // Forgets every window without ending anything, for the tests.
    static void forgetAllForTests() {
        for (MainWindow window : all()) {
            window.dispose();
        }
        OPEN.clear();
        front = null;
    }

    private static void exit() {
        LOG.info("Raphael quit");
        System.exit(0);
    }

    // Every window follows a change that's for all of them (the theme, the text size, the recent files).
    static void forEach(java.util.function.Consumer<MainWindow> change) {
        for (MainWindow window : all()) {
            change.accept(window);
        }
    }

    // On a Mac, Quit (Cmd+Q), Settings… and About live in the app's own menu, and double-clicked
    // files arrive here, not in main. There's one app menu, so these are set once, for the app,
    // and go to the window in front.
    public static void installAppMenu() {
        if (!Desktop.isDesktopSupported()) {
            return;
        }
        Desktop desktop = Desktop.getDesktop();
        if (desktop.isSupported(Desktop.Action.APP_QUIT_HANDLER)) {
            desktop.setQuitHandler((event, response) -> {
                if (!quitAll()) {
                    response.cancelQuit();
                }
            });
        }
        if (desktop.isSupported(Desktop.Action.APP_OPEN_FILE)) {
            desktop.setOpenFileHandler(event -> SwingUtilities.invokeLater(() -> {
                MainWindow window = front != null ? front : openNew();
                for (File file : event.getFiles()) {
                    window.openFromSystem(file);
                }
            }));
        }
        if (desktop.isSupported(Desktop.Action.APP_PREFERENCES)) {
            desktop.setPreferencesHandler(event -> {
                if (front != null) {
                    front.showSettings();
                }
            });
        }
        if (desktop.isSupported(Desktop.Action.APP_ABOUT)) {
            desktop.setAboutHandler(event -> {
                if (front != null) {
                    front.showAbout();
                }
            });
        }
    }
}
