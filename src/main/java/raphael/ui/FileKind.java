package raphael.ui;

import java.util.List;
import java.util.Locale;

// What a file dialog is for: the kinds of file it shows (by their endings; none means any file).
// When saving, a name typed without an ending gets the first one.
// OOP: a record (a small unchangeable data class) with shared constants, like an enum with data.
public record FileKind(String description, List<String> endings) {

    public static final FileKind CODE_OR_RUN = new FileKind("Java code and Raphael recordings", List.of("java", "raphael"));
    public static final FileKind CODE = new FileKind("Java files", List.of("java"));
    public static final FileKind RUN = new FileKind("Raphael recordings", List.of("raphael"));
    public static final FileKind CSV = new FileKind("CSV files (a spreadsheet opens them)", List.of("csv"));
    public static final FileKind ANY = new FileKind("All files", List.of());

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
