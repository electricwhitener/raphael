package raphael.render;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import raphael.analysis.CodeFacts;
import raphael.explain.Reads;
import raphael.model.FrameSnapshot;
import raphael.model.Snapshot;
import raphael.model.ThrownException;
import raphael.model.Trace;

// The files view, for programs that read input or use files. On top, the input (what was typed
// into the stdin box), word by word: what the program has read is faded, what it read just now
// says which variable it went into, and what the next line will read is outlined in blue. Each
// line ends in \n, so a nextLine() that only reads the end of a line can be seen. Below, the
// program's folder, a card per file with its lines: read lines faded, new lines flashing as they
// arrive, and a file the program looked for but wasn't there drawn as a faded ghost.
// OOP: a class that builds Scene objects, like CallsScenes and ObjectsScenes (a SceneSource).
public class FilesScenes implements SceneSource {

    private static final int MAX_LINES = 12;      // a longer file shows its first lines, then "… n more"
    private static final int MAX_CHARS = 44;      // a longer line is cut
    private static final double MIN_CARD_W = 180;
    private static final double GAP = 24;
    private static final double SECTION_GAP = 30;
    private static final int PER_ROW = 1;         // one column, with room on the right for "← read into line"
    private static final double NOTE_ROOM = 150;
    private static final double NUMBER_W = 28;    // room for the line numbers in a file card

    private final Trace trace;
    private final CodeFacts facts;
    private final Reads reads;
    private final String input;
    private final List<String> names = new ArrayList<>();          // every file the run had, in the order they came
    private final Map<String, Integer> missingFrom = new HashMap<>(); // a file looked for and not found → the step
    private final Map<Integer, Scene> built = new HashMap<>();
    private final double charW = Style.textWidth("m", Style.FIELD_FONT);
    private final double inputH;
    private final double cardW;
    private final double cardH;
    private final Dimension size;

    public FilesScenes(Trace trace, CodeFacts facts, String input) {
        this.trace = trace;
        this.facts = facts;
        this.input = input == null ? "" : input.replace("\r\n", "\n");
        this.reads = new Reads(trace, facts, this.input);
        int longest = 0;
        int most = 0;
        for (int s = 0; s < trace.size(); s++) {
            Snapshot step = trace.get(s);
            for (Map.Entry<String, String> file : step.getFiles().entrySet()) {
                if (!names.contains(file.getKey())) {
                    names.add(file.getKey());
                }
                List<String> lines = file.getValue().lines().toList();
                most = Math.max(most, Math.min(lines.size(), MAX_LINES + 1));
                for (String line : lines) {
                    longest = Math.max(longest, Math.min(MAX_CHARS, line.length()));
                }
            }
            ThrownException thrown = step.getThrown();
            if (thrown != null && thrown.message() != null
                    && (thrown.type().equals("FileNotFoundException") || thrown.type().equals("NoSuchFileException"))) {
                String name = missingName(thrown.message());
                if (!names.contains(name)) {
                    names.add(name);
                }
                missingFrom.putIfAbsent(name, s);
            }
        }
        double titleW = 0;
        for (String name : names) {
            titleW = Math.max(titleW, Style.textWidth(name + "  (not there)", Style.TITLE_FONT) + 16);
        }
        cardW = Math.max(MIN_CARD_W, Math.max(titleW, NUMBER_W + longest * charW + 2 * Style.BOX_PAD));
        cardH = Style.TITLE_H + Math.max(1, most) * Style.LINE_H + 4;
        double inputW = 0;
        int inputLines = 0;
        if (facts.readsInput()) {
            for (String line : this.input.split("\n", -1)) {
                inputW = Math.max(inputW, (line.length() + 2) * charW + 2 * Style.BOX_PAD + 140); // and room for "→ n"
                inputLines++;
            }
        }
        inputH = facts.readsInput() ? Style.LINE_H + 4 + Math.max(1, inputLines) * Style.LINE_H + SECTION_GAP : 0;
        int rows = (names.size() + PER_ROW - 1) / PER_ROW;
        double width = Math.max(inputW, Math.min(PER_ROW, Math.max(1, names.size())) * (cardW + GAP) - GAP + NOTE_ROOM);
        double height = inputH + (names.isEmpty() ? 0 : Style.LINE_H + 4 + rows * (cardH + Style.LINE_H + GAP));
        size = new Dimension((int) Math.max(360, width + 8), (int) Math.max(120, height));
    }

    // Whether there's anything to show: input to read, or files.
    public boolean hasContent() {
        return facts.readsInput() || !names.isEmpty();
    }

    @Override
    public Dimension getRunSize() {
        return size;
    }

    @Override
    public Scene sceneAt(int step) {
        return built.computeIfAbsent(step, this::build);
    }

    private Scene build(int step) {
        Scene scene = new Scene();
        Snapshot now = trace.get(step);
        List<Reads.Spot> justRead = new java.util.ArrayList<>(); // the latest read from each source, kept until the next
        for (String source : names) {
            if (reads.lastRead(step, source) != null) {
                justRead.add(reads.lastRead(step, source));
            }
        }
        if (reads.lastRead(step, Reads.INPUT) != null) {
            justRead.add(reads.lastRead(step, Reads.INPUT));
        }
        int[] next = nextRead(now); // what the line about to run will read: {is the keyboard (1) or a file (0), is a word (1)}
        if (facts.readsInput()) {
            addInput(scene, step, justRead, next);
        }
        if (names.isEmpty()) {
            return scene;
        }
        double top = inputH;
        scene.add(new SceneNode("files:heading", SceneNode.Kind.TEXT, 0, top, textW("the program's folder"), Style.LINE_H,
                "the program's folder", null));
        top += Style.LINE_H + 4;
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            double x = (i % PER_ROW) * (cardW + GAP);
            double y = top + (i / PER_ROW) * (cardH + Style.LINE_H + GAP);
            String text = now.getFiles().get(name);
            Integer missing = missingFrom.get(name);
            if (text == null && missing != null && missing <= step) {
                // looked for, and not there: a ghost of the file it wanted
                scene.add(new SceneNode("file:" + name, SceneNode.Kind.OBJECT, x, y, cardW, Style.TITLE_H + Style.LINE_H + 4, "",
                        name + "  (not there)", SceneNode.Look.DIM));
                scene.add(new SceneNode("file:" + name + ".line:0", SceneNode.Kind.FIELD, x + 1, y + Style.TITLE_H + 2, cardW - 1,
                        Style.LINE_H, "no such file in the folder", null, SceneNode.Look.DIM));
            } else if (text != null) {
                addFile(scene, name, text.replace("\r\n", "\n"), x, y, step, justRead, next != null && next[0] == 0);
            }
        }
        return scene;
    }

    // The input, word by word, each at its own column (the font is monospace), and \n at each line's end.
    private void addInput(Scene scene, int step, List<Reads.Spot> justRead, int[] next) {
        scene.add(new SceneNode("input:heading", SceneNode.Kind.TEXT, 0, 0, textW("input (typed into stdin)"), Style.LINE_H,
                "input (typed into stdin)", null));
        int done = reads.readUpTo(step, Reads.INPUT);
        int[] coming = next != null && next[0] == 1 ? nextSpan(input, done, next[1] == 1) : null;
        String[] lines = input.split("\n", -1);
        int offset = 0;
        double y = Style.LINE_H + 4;
        for (int row = 0; row < lines.length; row++) {
            String line = lines[row];
            double lineEnd = (line.length() + 2) * charW + 2 * Style.BOX_PAD; // past the \n
            int col = 0;
            while (col <= line.length()) {
                if (col == line.length()) { // the end of the line: the newline a nextLine() reads
                    int at = offset + col;
                    if (row < lines.length - 1) {
                        addWord(scene, "input:" + at, "\\n", at, at + 1, col, y, step, done, justRead, coming, lineEnd);
                    }
                    break;
                }
                if (Character.isWhitespace(line.charAt(col))) {
                    col++;
                    continue;
                }
                int end = col;
                while (end < line.length() && !Character.isWhitespace(line.charAt(end))) {
                    end++;
                }
                addWord(scene, "input:" + (offset + col), line.substring(col, end), offset + col, offset + end, col, y, step, done,
                        justRead, coming, lineEnd);
                col = end;
            }
            offset += line.length() + 1;
            y += Style.LINE_H;
        }
    }

    private void addWord(Scene scene, String key, String word, int from, int to, int col, double y, int step, int done,
                         List<Reads.Spot> justRead, int[] coming, double lineEnd) {
        double x = col * charW;
        double w = word.length() * charW + 2 * Style.BOX_PAD;
        Reads.Spot spot = null;
        for (Reads.Spot read : justRead) {
            if (read.source().equals(Reads.INPUT) && from < read.to() && to > read.from()) {
                spot = read;
            }
        }
        boolean read = to <= done && spot == null;
        scene.add(new SceneNode(key, SceneNode.Kind.FIELD, x, y, w, Style.LINE_H, word, null,
                read ? SceneNode.Look.DIM : SceneNode.Look.STILL));
        if (spot != null && spot.to() <= to + 1 && spot.into() != null) { // after the line: where the latest read went
            String note = "← read into " + spot.into();
            scene.add(new SceneNode("input:into", SceneNode.Kind.TEXT, lineEnd + 2 * charW, y, textW(note), Style.LINE_H, note, null));
        }
        if (coming != null && from < coming[1] && to > coming[0]) {
            scene.add(new SceneNode(key + ".next", SceneNode.Kind.FOCUS_READ, x, y + 1, w, Style.LINE_H - 2, "", null));
        }
    }

    // A file's card: its name, then its lines, numbered. Lines read already are faded.
    private void addFile(Scene scene, String name, String text, double x, double y, int step, List<Reads.Spot> justRead,
                         boolean fileReadComing) {
        String key = "file:" + name;
        List<String> lines = text.lines().toList();
        int shown = Math.min(lines.size(), lines.size() > MAX_LINES + 1 ? MAX_LINES : MAX_LINES + 1);
        double height = Style.TITLE_H + Math.max(1, shown + (lines.size() > shown ? 1 : 0)) * Style.LINE_H + 4;
        scene.add(new SceneNode(key, SceneNode.Kind.OBJECT, x, y, cardW, height, "", name));
        if (lines.isEmpty()) {
            scene.add(new SceneNode(key + ".empty", SceneNode.Kind.FIELD, x + 1, y + Style.TITLE_H + 2, cardW - 1, Style.LINE_H,
                    "(empty)", null, SceneNode.Look.DIM));
            return;
        }
        int done = reads.readUpTo(step, name);
        int offset = 0;
        boolean nextMarked = false;
        for (int i = 0; i < shown; i++) {
            String line = lines.get(i);
            int end = offset + line.length() + 1;
            double ly = y + Style.TITLE_H + 2 + i * Style.LINE_H;
            Reads.Spot spot = null;
            for (Reads.Spot read : justRead) {
                if (read.source().equals(name) && offset < read.to() && end > read.from()) {
                    spot = read;
                }
            }
            boolean read = end <= done && spot == null;
            String shownText = String.format("%2d  %s", i + 1, line.length() > MAX_CHARS ? line.substring(0, MAX_CHARS - 1) + "…" : line);
            scene.add(new SceneNode(key + ".line:" + i, SceneNode.Kind.FIELD, x + 1, ly, cardW - 1, Style.LINE_H, shownText, null,
                    read ? SceneNode.Look.DIM : SceneNode.Look.STILL));
            if (spot != null && spot.into() != null) {
                String note = "← read into " + spot.into();
                scene.add(new SceneNode(key + ".into", SceneNode.Kind.TEXT, x + cardW + 6, ly, textW(note), Style.LINE_H, note, null));
            }
            if (fileReadComing && !nextMarked && done > 0 && end > done && spot == null) {
                nextMarked = true; // the line the next read will take, once this file is being read
                scene.add(new SceneNode(key + ".next", SceneNode.Kind.FOCUS_READ, x + 2, ly + 1, cardW - 4, Style.LINE_H - 2, "", null));
            }
            offset = end;
        }
        if (lines.size() > shown) {
            scene.add(new SceneNode(key + ".more", SceneNode.Kind.FIELD, x + 1, y + Style.TITLE_H + 2 + shown * Style.LINE_H, cardW - 1,
                    Style.LINE_H, "… " + (lines.size() - shown) + " more lines", null, SceneNode.Look.DIM));
        }
    }

    // What the line about to run reads, if anything: {1 for the keyboard or 0 for a file, 1 for one word or 0 for a line}.
    private int[] nextRead(Snapshot now) {
        if (now.getFrames().isEmpty()) {
            return null;
        }
        FrameSnapshot frame = now.getFrames().get(0);
        for (CodeFacts.CallFact call : facts.callsAt(frame.getFile(), frame.getLine())) {
            boolean word = call.method().startsWith("next") && !call.method().equals("nextLine");
            if (word || call.method().equals("nextLine") || call.method().equals("readLine")) {
                boolean keyboard = call.receiver() != null && facts.isInputReader(call.receiver());
                return new int[] {keyboard ? 1 : 0, word ? 1 : 0};
            }
        }
        return null;
    }

    // Where the next read takes its text from, the way a Scanner would: {from, to}, or null.
    private static int[] nextSpan(String text, int from, boolean word) {
        if (from >= text.length()) {
            return null;
        }
        if (word) {
            int start = from;
            while (start < text.length() && Character.isWhitespace(text.charAt(start))) {
                start++;
            }
            int end = start;
            while (end < text.length() && !Character.isWhitespace(text.charAt(end))) {
                end++;
            }
            return start < end ? new int[] {start, end} : null;
        }
        int end = text.indexOf('\n', from);
        return new int[] {from, end < 0 ? text.length() : end + 1};
    }

    // FileNotFoundException says "notes.txt (No such file or directory)": just the name.
    private static String missingName(String message) {
        String name = message.replaceAll("\\s*\\(.*\\)$", "");
        return name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
    }

    private static double textW(String text) {
        return Style.textWidth(text, Style.CAPTION_FONT) + 4;
    }
}
