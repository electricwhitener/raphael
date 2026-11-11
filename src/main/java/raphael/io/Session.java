package raphael.io;

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

// The tabs that were open when Raphael last quit, to open again at the next start, as VS Code
// does: saved files and folders by their path, examples by their key, and which tab was in front.
// A program never saved isn't here: its unsaved draft is (DraftStore). Kept in the settings as a
// few lines of text ("file<tab>/home/me/Sum.java"), so an old or damaged value is simply skipped.
// OOP: encapsulation (a utility class: the format is private to it); a nested enum and record
// for one tab.
public final class Session {

    public enum Kind { FILE, FOLDER, EXAMPLE }

    // One tab: what it is, and where it comes from (a path, or an example's key).
    public record Tab(Kind kind, String where) {
    }

    // The tabs in order, and the index of the one that was in front (-1 for the Welcome tab).
    public record Saved(List<Tab> tabs, int front) {
    }

    private static final String KEY = "session";
    private static final int MAX_TABS = 30;

    private Session() {
    }

    public static void save(Preferences settings, List<Tab> tabs, int front) {
        String text = write(tabs, front);
        while (text.length() > Preferences.MAX_VALUE_LENGTH && !tabs.isEmpty()) { // very long paths: keep the first tabs
            tabs = tabs.subList(0, tabs.size() - 1);
            text = write(tabs, Math.min(front, tabs.size() - 1));
        }
        settings.put(KEY, text);
    }

    public static Saved load(Preferences settings) {
        return read(settings.get(KEY, ""));
    }

    static String write(List<Tab> tabs, int front) {
        StringBuilder text = new StringBuilder("front\t" + front + "\n");
        for (Tab tab : tabs.subList(0, Math.min(tabs.size(), MAX_TABS))) {
            if (tab.where().indexOf('\n') < 0 && tab.where().indexOf('\t') < 0) {
                text.append(tab.kind().name().toLowerCase(java.util.Locale.ROOT)).append('\t').append(tab.where()).append('\n');
            }
        }
        return text.toString();
    }

    static Saved read(String text) {
        List<Tab> tabs = new ArrayList<>();
        int front = -1;
        for (String line : text.split("\n")) {
            String[] parts = line.split("\t", 2);
            if (parts.length != 2 || parts[1].isEmpty()) {
                continue;
            }
            if (parts[0].equals("front")) {
                try {
                    front = Integer.parseInt(parts[1]);
                } catch (NumberFormatException e) {
                    front = -1;
                }
                continue;
            }
            for (Kind kind : Kind.values()) {
                if (kind.name().toLowerCase(java.util.Locale.ROOT).equals(parts[0])) {
                    tabs.add(new Tab(kind, parts[1]));
                }
            }
        }
        return new Saved(tabs, front < tabs.size() ? front : -1);
    }
}
