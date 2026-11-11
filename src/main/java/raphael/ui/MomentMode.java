package raphael.ui;

import java.util.Locale;

// What the key moments do while a run plays: nothing (off), show their cards and marks (show, the
// default), or also stop the playing at each one (pause). Remembered in the settings; one choice
// for every window and tab.
// OOP: an enum, a fixed set of named values, each knowing the words it's shown with; static
// methods keep the one current choice.
public enum MomentMode {
    OFF("Moments off", "no key moments: no cards, no marks"),
    SHOW("Show moments", "key moments show a card and a mark on the slider; playing goes on"),
    PAUSE("Pause at moments", "playing stops at each key moment; press play to go on");

    private static MomentMode current = read();

    private final String label;
    private final String description;

    MomentMode(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public static MomentMode current() {
        return current;
    }

    // Something that shows the choice or depends on it: told when it changes.
    public interface Watcher {
        void momentModeChanged(MomentMode mode);
    }

    // Weak: a closed tab's controls can still be garbage-collected.
    private static final java.util.Map<Watcher, Boolean> WATCHERS = new java.util.WeakHashMap<>();

    public static void watch(Watcher watcher) {
        WATCHERS.put(watcher, Boolean.TRUE);
    }

    public static void set(MomentMode mode) {
        current = mode;
        raphael.App.SETTINGS.put("moments", mode.name().toLowerCase(Locale.ROOT));
        for (Watcher watcher : new java.util.ArrayList<>(WATCHERS.keySet())) {
            watcher.momentModeChanged(mode);
        }
    }

    private static MomentMode read() {
        String saved = raphael.App.SETTINGS.get("moments", "show");
        for (MomentMode mode : values()) {
            if (mode.name().equalsIgnoreCase(saved)) {
                return mode;
            }
        }
        return SHOW;
    }

    @Override
    public String toString() { // as a combo box shows it
        return label;
    }
}
