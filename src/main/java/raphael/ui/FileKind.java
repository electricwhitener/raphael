package raphael.ui;

import java.util.List;
import java.util.Locale;

// What a file dialog is for: the kinds of file it shows (by their endings; none means any file).
// When saving, a name typed without an ending gets the first one.
// OOP: a record (a small unchangeable data class) with shared constants, like an enum with data.
public record FileKind(String description, List<String> endings) {

    // Code in any language Raphael knows, or a recording: what Open shows.
    public static final FileKind CODE_OR_RUN = new FileKind("Code and Raphael recordings", allCodeEndings(true));
    public static final FileKind CODE = codeOf(raphael.lang.Languages.JAVA);
    public static final FileKind RUN = new FileKind("Raphael recordings", List.of("raphael"));
    public static final FileKind CSV = new FileKind("CSV files (a spreadsheet opens them)", List.of("csv"));
    public static final FileKind ANY = new FileKind("All files", List.of());

    // One language's files, for saving: "C files" (.c, .h).
    public static FileKind codeOf(raphael.lang.Language language) {
        return new FileKind(language.name() + " files", language.endings().stream().map(e -> e.substring(1)).toList());
    }

    private static List<String> allCodeEndings(boolean withRecordings) {
        List<String> endings = new java.util.ArrayList<>();
        for (raphael.lang.Language language : raphael.lang.Languages.all()) {
            language.endings().forEach(e -> endings.add(e.substring(1)));
        }
        if (withRecordings) {
            endings.add("raphael");
        }
        return List.copyOf(endings);
    }

    public boolean accepts(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return endings.isEmpty() || endings.stream().anyMatch(ending -> lower.endsWith("." + ending));
    }

    // The ending a saved file gets ("java"), or null for any file.
    public String mainEnding() {
        return endings.isEmpty() ? null : endings.get(0);
    }

    // The patterns, as Windows writes them: "*.java;*.raphael", or "*.*".
    public String patterns() {
        return endings.isEmpty() ? "*.*" : String.join(";", endings.stream().map(e -> "*." + e).toList());
    }

    // The filter of a Windows Forms dialog: "Java files (*.java)|*.java|All files (*.*)|*.*".
    public String windowsFilter() {
        String these = description + " (" + patterns() + ")|" + patterns();
        return endings.isEmpty() ? these : these + "|All files (*.*)|*.*";
    }
}
