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

// The example programs that come with Raphael, in the groups the Examples menu shows. The list
// is the text file examples/catalog.txt, so adding an example means adding a .java file and one
// line there, not changing code:
//   ## Data structures                                       a section
//   # Linked lists                                           a group in it
//   lists/InsertAtHead | Insert at the head | The new node points at the old head.
// OOP: records as small data holders (Example, Group); a utility class (static methods, private
// constructor) that reads the list once and keeps it; exception handling: a missing resource is
// a bug in Raphael, so it's rethrown unchecked.
public final class ExampleCatalog {

    private static final String FOLDER = "/examples/";

    // One example: its file ("lists/InsertAtHead"), its title in the menu, and a sentence on what it shows.
    public record Example(String path, String title, String blurb) {
        // The file's name without its folder, which is also its class name: "InsertAtHead".
        public String name() {
            return path.substring(path.lastIndexOf('/') + 1);
        }
    }

    // A group of examples ("Linked lists") in a section ("Data structures").
    public record Group(String section, String name, List<Example> examples) {
    }

    private static List<Group> groups;

    private ExampleCatalog() {
    }

    // Every group, in the catalog's order.
    public static synchronized List<Group> groups() {
        if (groups == null) {
            groups = Collections.unmodifiableList(read());
        }
        return groups;
    }

    // Every example, in order.
    public static List<Example> all() {
        List<Example> all = new ArrayList<>();
        for (Group group : groups()) {
            all.addAll(group.examples());
        }
        return all;
    }

    // The example with this name ("BubbleSort"), or null.
    public static Example find(String name) {
        for (Example example : all()) {
            if (example.name().equals(name)) {
                return example;
            }
        }
        return null;
    }

    // The example's code.
    // The input an example comes with, from its "// stdin: 3 | Asha 19" line (one line of
    // input between each |), for the stdin box. "" if it has none.
    public static String input(String source) {
        for (String line : source.split("\n")) {
            if (line.startsWith("// stdin: ")) {
                return String.join("\n", line.substring("// stdin: ".length()).trim().split("\\s*\\|\\s*")) + "\n";
            }
        }
        return "";
    }

    public static String source(Example example) {
        try (InputStream in = ExampleCatalog.class.getResourceAsStream(FOLDER + example.path() + ".java")) {
            if (in == null) {
                throw new IllegalStateException("The example " + example.path() + " is in the catalog but not in Raphael");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<Group> read() {
        List<Group> read = new ArrayList<>();
        String section = "";
        try (InputStream in = ExampleCatalog.class.getResourceAsStream(FOLDER + "catalog.txt");
             BufferedReader lines = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = lines.readLine()) != null) {
                line = line.strip();
                if (line.startsWith("## ")) {
                    section = line.substring(3);
                } else if (line.startsWith("# ")) {
                    read.add(new Group(section, line.substring(2), new ArrayList<>()));
                } else if (!line.isEmpty()) {
                    String[] parts = line.split("\\s*\\|\\s*", 3);
                    read.get(read.size() - 1).examples().add(new Example(parts[0], parts[1], parts.length > 2 ? parts[2] : ""));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return read;
    }
}
