package raphael.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// The example programs that come with Raphael, in the tree the examples panel shows: for each
// language a title (Data structures), a category (Trees), perhaps a subcategory (Red-black tree),
// then the cases. Each language's list is the text file examples/<language>/catalog.txt, so
// adding an example means adding a file and one line there, not changing code:
//   # Data structures                                     a title
//   ## Linked lists                                        a category in it
//   ### Singly linked list                                 a subcategory (optional)
//   lists/InsertAtHead | Insert at the head | The new node points at the old head.
//   // a comment
// OOP: records as small data holders (Example, Group); a utility class (static methods, private
// constructor) that reads each list once and keeps it; exception handling: a missing resource
// is a bug in Raphael, so it's rethrown unchecked.
public final class ExampleCatalog {

    private static final String FOLDER = "/examples/";

    // One example: its language, its file in that language's folder ("lists/InsertAtHead"), its
    // title, a sentence on what it shows, and its subcategory ("Singly linked list", or null).
    public record Example(raphael.lang.Language language, String path, String title, String blurb, String subcategory) {
        // The file's name without its folder: "InsertAtHead" (for Java, also its class name).
        public String name() {
            return path.substring(path.lastIndexOf('/') + 1);
        }

        // Unique among every language's examples: "lists/InsertAtHead" for Java, "c/lists/insert_head" for the rest.
        public String key() {
            return language == raphael.lang.Languages.JAVA ? path : language.id() + "/" + path;
        }
    }

    // A category of examples ("Linked lists") under a title ("Data structures").
    public record Group(String section, String name, List<Example> examples) {
    }

    private static final java.util.Map<raphael.lang.Language, List<Group>> GROUPS = new java.util.HashMap<>();

    private ExampleCatalog() {
    }

    // Java's categories, in the catalog's order.
    public static List<Group> groups() {
        return groups(raphael.lang.Languages.JAVA);
    }

    // One language's categories, in its catalog's order (none if it has no examples yet).
    public static synchronized List<Group> groups(raphael.lang.Language language) {
        return GROUPS.computeIfAbsent(language, l -> Collections.unmodifiableList(read(l)));
    }

    // Every example of every language, in order.
    public static List<Example> all() {
        List<Example> all = new ArrayList<>();
        for (raphael.lang.Language language : raphael.lang.Languages.all()) {
            for (Group group : groups(language)) {
                all.addAll(group.examples());
            }
        }
        return all;
    }

    // The Java example with this name ("BubbleSort"), else any language's, or null.
    public static Example find(String name) {
        Example other = null;
        for (Example example : all()) {
            if (example.name().equals(name)) {
                if (example.language() == raphael.lang.Languages.JAVA) {
                    return example;
                }
                other = other == null ? example : other;
            }
        }
        return other;
    }

    // The example's code.
    // The input an example comes with, from its "// stdin: 3 | Asha 19" line (one line of
    // input between each |), for the stdin box. "" if it has none.
    public static String input(String source) {
        for (String line : source.split("\n")) {
            for (String start : new String[] {"// stdin: ", "# stdin: "}) { // a Python example's comment starts with #
                if (line.startsWith(start)) {
                    return String.join("\n", line.substring(start.length()).trim().split("\\s*\\|\\s*")) + "\n";
                }
            }
        }
        return "";
    }

    public static String source(Example example) {
        String file = FOLDER + example.language().id() + "/" + example.path() + example.language().endings().get(0);
        try (InputStream in = ExampleCatalog.class.getResourceAsStream(file)) {
            if (in == null) {
                throw new IllegalStateException("The example " + example.path() + " is in the catalog but not in Raphael");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<Group> read(raphael.lang.Language language) {
        List<Group> read = new ArrayList<>();
        InputStream in = ExampleCatalog.class.getResourceAsStream(FOLDER + language.id() + "/catalog.txt");
        if (in == null) {
            return read; // no examples in this language yet
        }
        String title = "";
        String subcategory = null;
        try (BufferedReader lines = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = lines.readLine()) != null) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("//")) {
                    continue;
                }
                if (line.startsWith("### ")) {
                    subcategory = line.substring(4);
                } else if (line.startsWith("## ")) {
                    read.add(new Group(title, line.substring(3), new ArrayList<>()));
                    subcategory = null;
                } else if (line.startsWith("# ")) {
                    title = line.substring(2);
                    subcategory = null;
                } else {
                    String[] parts = line.split("\\s*\\|\\s*", 3);
                    read.get(read.size() - 1).examples().add(new Example(language, parts[0], parts[1],
                            parts.length > 2 ? parts[2] : "", subcategory));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return read;
    }
}
