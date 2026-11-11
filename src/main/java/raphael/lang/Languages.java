package raphael.lang;

import java.util.List;
import java.util.prefs.Preferences;

// Every language Raphael knows, and the one the user is learning (remembered between runs).
// OOP: a registry (like StructureRenderer's): the rest of Raphael finds a Language here by id,
// by file name or by what pasted code looks like, never by naming a subclass.
public final class Languages {

    public static final Language JAVA = new JavaLanguage();
    public static final Language C = new CLanguage();
    public static final Language CPP = new CppLanguage();
    public static final Language PYTHON = new PythonLanguage();

    private static final List<Language> ALL = List.of(JAVA, C, CPP, PYTHON);
    private static final Preferences SETTINGS = raphael.App.SETTINGS;

    private Languages() {
    }

    public static List<Language> all() {
        return ALL;
    }

    // By id ("c"), or Java for an unknown or missing one (recordings made before there were languages).
    public static Language byId(String id) {
        for (Language language : ALL) {
            if (language.id().equals(id)) {
                return language;
            }
        }
        return JAVA;
    }

    // The language a file of this name is in, or null if none of them.
    public static Language forFile(String fileName) {
        for (Language language : ALL) {
            if (language.owns(fileName)) {
                return language;
            }
        }
        return null;
    }

    // Which language pasted code is in, or null if it's too short or unclear to tell.
    public static Language recognise(String code) {
        Language best = null;
        int bestScore = 1; // at least two clues
        for (Language language : ALL) {
            int score = language.likeness(code);
            if (score > bestScore) {
                best = language;
                bestScore = score;
            }
        }
        return best;
    }

    // The language the user chose to learn (New uses it, and the examples panel shows it first).
    public static Language preferred() {
        return byId(SETTINGS.get("language", "java"));
    }

    public static void setPreferred(Language language) {
        SETTINGS.put("language", language.id());
    }

    // Whether the user has chosen yet (the first run asks).
    public static boolean chosen() {
        return SETTINGS.get("language", null) != null;
    }

    // How many of these clues the code contains.
    static int count(String code, String... clues) {
        int found = 0;
        for (String clue : clues) {
            if (code.contains(clue)) {
                found++;
            }
        }
        return found;
    }
}
