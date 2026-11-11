package raphael.ui;

import java.util.Locale;

// How a program's tab is laid out, one choice for every tab and window, remembered: which panels
// show (a closed one comes back from View → Panels), whether the picture is beside the code or
// below it, and whether the panels run along the bottom or down the right.
// OOP: an enum of the panels that can be closed; static methods keep the one current layout and
// tell the tabs when it changes (the Observer pattern, with weak watchers as in MomentMode).
public final class PanelLayout {

    // The panels a user may close.
    public enum Panel {
        PICTURE("Picture", "the picture and its views (Calls, Objects, Files)"),
        CALL_STACK("Call stack", "the methods running, newest first"),
        VARIABLES("Variables", "the running method's variables"),
        OUTPUT("Terminal", "the terminal, the key moments and the trace table");

        private final String label;
        private final String description;

        Panel(String label, String description) {
            this.label = label;
            this.description = description;
        }

        public String label() {
            return label;
        }

        public String description() {
            return description;
        }

        private String key() {
            return "panel." + name().toLowerCase(Locale.ROOT);
        }
    }

    // A tab that lays itself out by this.
    public interface Watcher {
        void layoutChanged();
    }

    private static final java.util.Map<Watcher, Boolean> WATCHERS = new java.util.WeakHashMap<>();

    private PanelLayout() {
    }

    public static void watch(Watcher watcher) {
        WATCHERS.put(watcher, Boolean.TRUE);
    }

    public static boolean shown(Panel panel) {
        return raphael.App.SETTINGS.getBoolean(panel.key(), true);
    }

    public static void show(Panel panel, boolean shown) {
        raphael.App.SETTINGS.putBoolean(panel.key(), shown);
        changed();
    }

    // The picture below the code (true) or beside it (false, the default).
    public static boolean pictureBelow() {
        return raphael.App.SETTINGS.getBoolean("layout.pictureBelow", false);
    }

    public static void setPictureBelow(boolean below) {
        raphael.App.SETTINGS.putBoolean("layout.pictureBelow", below);
        changed();
    }

    // The panels down the right (true) or along the bottom (false, the default).
    public static boolean panelsRight() {
        return raphael.App.SETTINGS.getBoolean("layout.panelsRight", false);
    }

    public static void setPanelsRight(boolean right) {
        raphael.App.SETTINGS.putBoolean("layout.panelsRight", right);
        changed();
    }

    // Every panel back, in the usual places.
    public static void reset() {
        for (Panel panel : Panel.values()) {
            raphael.App.SETTINGS.putBoolean(panel.key(), true);
        }
        raphael.App.SETTINGS.putBoolean("layout.pictureBelow", false);
        raphael.App.SETTINGS.putBoolean("layout.panelsRight", false);
        changed();
    }

    private static void changed() {
        for (Watcher watcher : new java.util.ArrayList<>(WATCHERS.keySet())) {
            watcher.layoutChanged();
        }
    }
}
