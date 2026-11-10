package raphael.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import raphael.RaphaelException;
import raphael.compile.CompileHints;
import raphael.compile.CompileResult;
import raphael.compile.SourceCompiler;
import raphael.io.RunFile;
import raphael.io.SafeFiles;
import raphael.io.StepLog;
import raphael.model.EndReason;
import raphael.model.Snapshot;
import raphael.model.ThrownException;
import raphael.model.Trace;
import raphael.render.SceneBuilder;
import raphael.render.SceneList;
import raphael.render.Style;
import raphael.trace.JdiTracer;
import raphael.trace.RunLimits;
import raphael.trace.Tracer;

// One program and everything that belongs to it: its code, its recorded run, the picture, the call
// stack, variables, console and files panels, and its own tracer. The window holds one Workbench
// per open program (a tab) and sends each command to the one in front. A run finishes into the
// Workbench that started it, so switching to another program while one is recording is safe.
// OOP: composition (a Workbench is made of panels that each do one job); the Observer pattern
// (each panel listens to this program's TraceModel); an interface (Host) as the only way back to
// the window, so a Workbench doesn't depend on MainWindow and can be tested on its own.
public class Workbench extends JPanel {

    // What a Workbench needs from the window around it.
    public interface Host {
        // The status text, the run button, the title or the legend may need redrawing for this program.
        void stateChanged(Workbench workbench);

        // The computer's Open or Save dialog. Null if cancelled.
        File chooseFile(boolean forSaving, FileKind kind, String suggested);

        // A file was opened or saved: add it to the recent files.
        void remember(File file);

        // A file was dropped on the code or the picture.
        void openDropped(File file);

        // An example was picked from the picture's guide, or the Examples menu should open.
        void loadExample(String name);

        void showExamples();

        // Ctrl + mouse wheel over the code.
        void changeTextSize(int change);

        // The limits the next run should use (from the settings).
        RunLimits limits();
    }

    private static final Logger LOG = Logger.getLogger(Workbench.class.getName());
    private static final int COLLAPSED_ROW_H = 28; // the bottom panels with only their headings showing
    // The examples the empty picture offers with one click; the rest are in the Examples menu.
    private static final String[] FEATURED = {"BubbleSort", "ReverseList", "BSTInsert", "Inheritance", "BFS", "MergeSort"};

    private final Host host;
    private final TraceModel model = new TraceModel();
    private final CodePanel code = new CodePanel(model);
    private final VisualPanel visual = new VisualPanel(model);
    private final ControlsBar controls = new ControlsBar(model);
    private final CallStackPanel callStack = new CallStackPanel();
    private final LocalsPanel locals = new LocalsPanel();
    private final ConsolePanel console = new ConsolePanel();
    private final FilesPanel files = new FilesPanel(this::addInputFile, this::clearInputFiles);
    private final List<File> inputFiles = new ArrayList<>(); // copied into the program's folder for each run
    private final Tracer tracer = new JdiTracer();
    // The picture area shows either the picture or, before the first run, a short guide.
    private final CardLayout pictureCards = new CardLayout();
    private final JPanel pictureArea = new JPanel(pictureCards);
    private final EmptyState emptyState;
    private final JSplitPane top;    // the code on the left, the picture on the right
    private final JSplitPane middle; // that above; the call stack, variables and console below

    private RunFile.Saved lastRun; // the run on show, with the code and input it ran with, for Save recording
    private Trace fromFile;        // the recording read from a file, while it's on show
    private String recordingText = "";
    private File currentFile;
    private String documentName = "new program";
    private boolean running;
    private Timer progress;
    private volatile int recordedSteps; // set by the background run, read on Swing's thread
    private String said = "";      // this program's last status message
    private Color saidColor = Style.INK;
    private boolean legendWanted = true; // false while the status bar shows an error

    public Workbench(Host host) {
        super(new BorderLayout());
        this.host = host;
        model.addListener(code);
        model.addListener(visual);
        model.addListener(controls);
        model.addListener(callStack);
        model.addListener(locals);
        model.addListener(console);
        model.addListener(files);

        JScrollPane picture = new JScrollPane(visual);
        picture.setBorder(BorderFactory.createEmptyBorder());
        picture.getViewport().setBackground(Style.BACKGROUND);
        pictureArea.setBorder(Section.hairline(0, 1, 0, 0));
        pictureArea.add(picture, "picture");
        emptyState = new EmptyState(FEATURED, !WindowsFileDialog.available(), host::loadExample, host::showExamples);
        JScrollPane guide = new JScrollPane(emptyState, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        guide.setBorder(BorderFactory.createEmptyBorder());
        pictureArea.add(guide, "empty");
        model.addListener(m -> showPictureOrGuide());
        model.addListener(m -> host.stateChanged(this)); // the status bar follows the step (a caught exception)
        // Drop a .java file on the code or the picture to open it.
        visual.setTransferHandler(new FileDrop(null, host::openDropped));
        code.getEditor().setTransferHandler(new FileDrop(code.getEditor().getTransferHandler(), host::openDropped));

        top = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, code, pictureArea);
        top.setResizeWeight(0.4);
        top.setBorder(BorderFactory.createEmptyBorder());
        JPanel bottom = new JPanel(new GridBagLayout());
        bottom.setBackground(Style.BACKGROUND);
        bottom.setBorder(Section.hairline(1, 0, 0, 0));
        addBottomPanel(bottom, callStack, 0.25);
        addBottomPanel(bottom, locals, 0.45); // values can be long, so variables get the most room
        addBottomPanel(bottom, console, 0.30);
        addBottomPanel(bottom, files, 0.30);
        files.setVisible(false);
        bottom.setPreferredSize(new Dimension(100, Style.BOTTOM_ROW_H));
        bottom.setMinimumSize(new Dimension(0, COLLAPSED_ROW_H));
        middle = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, bottom);
        middle.setResizeWeight(1); // the top gets all the spare room
        // Whenever the program's area changes size, including when it first shows, the bottom row keeps its height.
        middle.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                fitBottomRow();
            }
        });
        middle.setBorder(Section.hairline(1, 0, 0, 0));
        add(middle, BorderLayout.CENTER);

        // Scroll panes and split panes take the arrow keys, Home and End for themselves whenever
        // something inside them has focus. Without these lines the window's shortcuts would never hear them.
        for (JComponent claimsKeys : new JComponent[] {picture, top, middle}) {
            claimsKeys.setInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, null);
        }

        // The stdin box only appears for a program that can read input.
        code.setOnCodeChanged(() -> {
            console.showInput(code.getCode().contains("System.in"));
            showFilesIfUsed();
            if (model.getTrace() == null) { // an old run or compile error no longer applies
                visual.clearMessage();
                console.showMessage("");
                showPictureOrGuide();
                if (!running) {
                    say(Shortcuts.label("code changed · press run (ctrl+enter)"), Style.INK_SOFT);
                }
            }
            host.stateChanged(this); // the title's unsaved dot
        });
        code.setOnTextSizeWheel(host::changeTextSize);
        showPictureOrGuide();
    }

    // Adds a panel that takes this share of the row's width. With no preferred width of its own,
    // the share is all it gets, so long values can't push its neighbours around.
    private static void addBottomPanel(JPanel row, JComponent panel, double share) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.BOTH;
        constraints.weightx = share;
        constraints.weighty = 1;
        panel.setPreferredSize(new Dimension(0, 0));
        row.add(panel, constraints);
    }

    public TraceModel getModel() {
        return model;
    }

    public CodePanel getCode() {
        return code;
    }

    public VisualPanel getVisual() {
        return visual;
    }

    public ControlsBar getControls() {
        return controls;
    }

    public boolean isRunning() {
        return running;
    }

    public File getCurrentFile() {
        return currentFile;
    }

    public String getDocumentName() {
        return documentName;
    }

    public boolean isDirty() {
        return code.isDirty();
    }

    // A program nobody has typed in, run or saved: a new program as it came, or an example just
    // opened. Opening something else replaces it rather than adding a tab.
    public boolean isUntouched() {
        return !code.isDirty() && currentFile == null && model.getTrace() == null && !running && !visual.hasMessage();
    }

    // Whether the status bar should show the picture's key (not while it shows an error).
    public boolean wantsLegend() {
        return legendWanted;
    }

    // "saved recording · 163 steps" while a recording read from a file is on show, otherwise null.
    public String recordingChip() {
        return fromFile != null && model.getTrace() == fromFile ? recordingText : null;
    }

    // Whether the run on show can be saved as a recording.
    public boolean hasRecording() {
        return lastRun != null && model.getTrace() == lastRun.trace();
    }

    // Puts code in the editor: a new program, an example, a pasted one, an opened file, a restored
    // draft. file is where it's saved (null if nowhere yet); name is what the title shows.
    public void showCode(String text, File file, String name) {
        code.setCode(text);
        top.setDividerLocation(dividerForCode());
        currentFile = file;
        documentName = name;
        host.stateChanged(this);
    }

    // The keyboard goes to the editor, ready to type or paste.
    public void focusEditor() {
        code.getEditor().requestFocusInWindow();
    }

    // ---- the status bar ----

    public void say(String message, Color color) {
        said = message;
        saidColor = color;
        legendWanted = !Style.ERROR.equals(color); // during an error, the message outranks the key
        host.stateChanged(this);
    }

    // What the status bar shows for this program now. On the step where a caught exception lands in
    // its catch, it says so; on the other steps it goes back to what was last said (usually the run's summary).
    public String statusText() {
        ThrownException thrown = caughtNow();
        if (thrown == null) {
            return said;
        }
        return "[caught] " + thrown.type() + (thrown.throwLine() > 0 ? " · thrown at line " + thrown.throwLine() + "," : " ·")
                + " caught at line " + thrown.catchLine();
    }

    public Color statusColor() {
        return caughtNow() == null ? saidColor : Style.INK;
    }

    private ThrownException caughtNow() {
        Snapshot snapshot = model.getSnapshot();
        return snapshot == null ? null : snapshot.getThrown();
    }

    // The theme changed: the colours were replaced, so the saved status colour is set again, and
    // the code's marks are drawn in the new colours.
    public void themeChanged() {
        boolean saidError = !legendWanted;
        saidColor = saidError ? Style.ERROR : Style.INK;
        code.restoreMarks();
    }

    // The text size changed: the code follows, and the divider makes room for the longest line.
    public void textSizeChanged() {
        code.applyFont();
        if (top.getWidth() > 0 && top.getDividerLocation() < dividerForCode()) {
            top.setDividerLocation(dividerForCode());
        }
    }

    // ---- running ----

    // Compile, trace and draw on a background thread, then show the result on the Swing thread.
    // While recording, the run button is the stop button.
    public void run() {
        if (running) {
            tracer.stop();
            return;
        }
        String source = code.getCode();
        String input = console.getInput();
        if (top.getDividerLocation() < dividerForCode()) {
            top.setDividerLocation(dividerForCode()); // the code may have grown since it was loaded
        }
        tracer.setLimits(host.limits()); // the settings may have changed since the last run
        running = true;
        visual.setRecording(true);
        showPictureOrGuide();
        progress = new Timer(200, e -> {
            int steps = tracer.stepsSoFar(); // 0 once recording has finished and drawing has begun
            if (steps > 0) {
                visual.setRecordedSteps(steps);
                say("recording your program: " + steps + " steps · esc stops", Style.INK_SOFT);
            }
        });
        progress.start();
        code.setEditable(false);
        model.clear();
        visual.clearMessage();
        say("compiling…", Style.INK_SOFT); // also tells the window that the run button is now Stop

        // An anonymous subclass of SwingWorker: doInBackground() runs on a worker thread,
        // process() and done() run on the Swing thread.
        new SwingWorker<RunResult, String>() {
            @Override
            protected RunResult doInBackground() throws Exception {
                CompileResult program = new SourceCompiler().compile(source);
                if (!program.success()) {
                    return new RunResult(program, null, null, source, input);
                }
                Trace trace = tracer.trace(program, input);
                recordedSteps = trace.size();
                publish("drawing " + trace.size() + " steps…");
                return new RunResult(program, trace, new SceneBuilder(source).build(trace), source, input);
            }

            @Override
            protected void process(List<String> messages) {
                visual.setRecordedSteps(recordedSteps); // the picture's count agrees with the status
                say(messages.get(messages.size() - 1), Style.INK_SOFT);
            }

            @Override
            protected void done() {
                running = false;
                progress.stop();
                visual.setRecording(false);
                code.setEditable(true);
                try {
                    show(get());
                } catch (ExecutionException e) {
                    runFailed(e.getCause());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                showPictureOrGuide();
                host.stateChanged(Workbench.this);
            }
        }.execute();
    }

    // Esc: stop a recording, or pause the playing.
    public void stopOrPause() {
        if (running) {
            tracer.stop();
        } else {
            controls.pause();
        }
    }

    // Asks a recording in progress to end (the program is being closed).
    public void stop() {
        if (running) {
            tracer.stop();
        }
        controls.pause();
    }

    // The picture, or the guide when there's nothing to show yet: no recorded run, nothing being
    // recorded, and no message (a compile error or a crash belongs in the picture area).
    private void showPictureOrGuide() {
        boolean nothingYet = !model.hasSteps() && !running && !visual.hasMessage();
        pictureCards.show(pictureArea, nothingYet ? "empty" : "picture");
        fitBottomRow();
    }

    // The bottom row keeps its height, except before the first run in a small window (or at
    // 200% scaling): then the empty panels shrink to their headings so the guide fits. Not while
    // the stdin box is showing, which is needed before running.
    void fitBottomRow() {
        if (middle == null || middle.getHeight() == 0) {
            return;
        }
        int bottom = Style.BOTTOM_ROW_H;
        boolean guide = !model.hasSteps() && !running && !visual.hasMessage();
        if (guide && !console.isInputShown()
                && middle.getHeight() - Style.BOTTOM_ROW_H < emptyState.getPreferredSize().height + 2 * Style.SPACE_6) {
            bottom = COLLAPSED_ROW_H;
        }
        middle.setDividerLocation(middle.getHeight() - middle.getDividerSize() - bottom);
    }

    // A run that ended with an exception instead of a result. A RaphaelException is a problem
    // Raphael knows how to explain (no compiler, the program's JVM wouldn't start); anything else
    // is a bug in Raphael, which ErrorReporter logs and reports.
    private void runFailed(Throwable cause) {
        if (cause instanceof RaphaelException) {
            LOG.log(Level.WARNING, "A run couldn't start", cause);
            visual.setMessage("couldn't run", cause.getMessage(), null, null);
            say("[error] " + cause.getMessage(), Style.ERROR);
        } else {
            ErrorReporter.report(cause);
            visual.setMessage("something went wrong inside raphael", cause.toString(), null,
                    "The details are in its log (Help → Open log folder).");
            say("[error] something went wrong inside raphael · the details are in its log", Style.ERROR);
        }
    }

    private void show(RunResult result) {
        if (result.trace() == null) {
            showCompileErrors(result.program().errors());
            return;
        }
        Trace trace = result.trace();
        LOG.info("Ran: " + trace.size() + " steps, " + trace.getEndReason());
        lastRun = new RunFile.Saved(result.source(), result.input(), trace);
        model.load(trace, result.scenes(), result.scenes().getRunSize());
        if (isShowing()) {
            visual.requestFocusInWindow(); // so the arrow keys step the program, not the editor's cursor
        }
        controls.play();
        String steps = "recorded " + trace.size() + " steps · space pauses";
        if (trace.getEndReason() == EndReason.FINISHED) {
            say("[ok] " + steps, Style.INK);
        } else if (trace.getEndMessage().startsWith("NoSuchElementException") && result.source().contains("System.in")) {
            visual.setMessage("needs input", "This program reads input, and it ran out.", null,
                    "Type its input into the stdin box below, then press run.");
            say("[needs input] type it into the stdin box, then run again", Style.ERROR);
        } else {
            say("[" + trace.getEndReason().name().toLowerCase(Locale.ROOT).replace('_', ' ') + "] " + trace.getEndMessage()
                    + " · " + steps, Style.ERROR);
        }
    }

    private void showCompileErrors(List<CompileResult.CompileError> errors) {
        LOG.info("Didn't compile: " + errors.size() + " error(s)");
        code.showErrors(errors);
        StringBuilder text = new StringBuilder();
        for (CompileResult.CompileError error : errors) {
            text.append(error.line() > 0 ? "line " + error.line() + ": " : "").append(error.message()).append("\n");
            String hint = CompileHints.hint(error.message());
            if (hint != null) {
                text.append("  hint: ").append(hint).append("\n");
            }
            text.append("\n");
        }
        console.showMessage(text.toString());
        CompileResult.CompileError first = errors.get(0);
        String where = (first.line() > 0 ? "line " + first.line() + ": " : "") + first.message();
        String more = errors.size() > 1 ? " (+" + (errors.size() - 1) + " more in the console)" : "";
        String next = null;
        if (errors.size() > 1) {
            CompileResult.CompileError second = errors.get(1);
            next = "next: " + (second.line() > 0 ? "line " + second.line() + " · " : "")
                    + second.message().lines().findFirst().orElse("") + " (see the console)";
        }
        visual.setMessage("didn't compile · " + errors.size() + (errors.size() == 1 ? " error" : " errors"),
                where.lines().findFirst().orElse(""), next, CompileHints.hint(first.message()));
        say("[error] " + where.lines().findFirst().orElse("") + more, Style.ERROR);
    }

    // A recording saved earlier: its code goes in the editor and its steps play, with nothing run.
    public void showRecording(File file, RunFile.Saved saved) {
        showCode(saved.source(), null, file.getName()); // Ctrl+S saves the code as a .java file, never over the recording
        console.setInput(saved.input());
        SceneList scenes = new SceneBuilder(saved.source()).build(saved.trace());
        lastRun = saved;
        fromFile = saved.trace();
        recordingText = "saved recording · " + saved.trace().size() + " steps";
        model.load(saved.trace(), scenes, scenes.getRunSize());
        if (isShowing()) {
            visual.requestFocusInWindow();
        }
        say("[recording] " + saved.trace().size() + " steps, played back without running · space plays", Style.INK);
    }

    // ---- saving ----

    // Before this program is closed or replaced: if it has unsaved changes, offer to save them.
    // True if it's fine to go on.
    public boolean okToLoseCode() {
        if (!code.isDirty()) {
            return true;
        }
        String answer = Dialogs.question(this, "Save changes to " + documentName + "?")
                .body("Your changes will be lost if you don't save them.")
                .buttons("Save", "Cancel", "Don't save")
                .ask();
        if ("Save".equals(answer)) {
            return save(); // go on only if it really was saved
        }
        return "Don't save".equals(answer);
    }

    // Saves to the current file, or asks where if there isn't one yet. True if it was saved.
    public boolean save() {
        return currentFile != null ? saveTo(currentFile) : saveAs();
    }

    public boolean saveAs() {
        File file = host.chooseFile(true, FileKind.CODE, currentFile != null ? currentFile.getName() : suggestedName(code.getCode()));
        return file != null && saveTo(file);
    }

    private boolean saveTo(File file) {
        try {
            SafeFiles.write(file.toPath(), code.getCode());
            currentFile = file;
            code.markSaved();
            documentName = file.getName();
            host.remember(file);
            say("[saved] " + file.getName(), Style.INK_SOFT);
            return true;
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Couldn't save " + file, e);
            Dialogs.tell(this, "Couldn't save " + file.getName(), "Because " + SafeFiles.describe(e)
                    + ". Your code is still here; try saving it somewhere else.");
            currentFile = null; // so the next save asks where
            host.stateChanged(this);
            return false;
        }
    }

    // File → Save recording: the program, its input and every step, in a .raphael file.
    public void saveRecording() {
        if (!hasRecording()) {
            say("run the program first, then save its recording", Style.INK_SOFT);
            return;
        }
        String name = (currentFile != null ? currentFile.getName() : suggestedName(lastRun.source())).replaceAll("\\.java$", "");
        File file = host.chooseFile(true, FileKind.RUN, name + RunFile.EXTENSION);
        if (file == null) {
            return;
        }
        try {
            RunFile.write(file.toPath(), lastRun);
            host.remember(file);
            say("[saved] " + file.getName() + " · " + lastRun.trace().size() + " steps", Style.INK_SOFT);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Couldn't save the recording " + file, e);
            Dialogs.tell(this, "Couldn't save " + file.getName(), "Because " + SafeFiles.describe(e) + ".");
        }
    }

    // File → Export steps as CSV: the run as a table for a spreadsheet.
    public void exportSteps() {
        Trace trace = model.getTrace();
        if (trace == null) {
            return;
        }
        String name = (currentFile != null ? currentFile.getName() : suggestedName(code.getCode())).replaceAll("\\.java$", "");
        File file = host.chooseFile(true, FileKind.CSV, name + "-steps.csv");
        if (file == null) {
            return;
        }
        try {
            StepLog.write(file.toPath(), trace);
            say("[saved] " + file.getName() + " · " + trace.size() + " rows", Style.INK_SOFT);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Couldn't write " + file, e);
            Dialogs.tell(this, "Couldn't save " + file.getName(), "Because " + SafeFiles.describe(e) + ".");
        }
    }

    // ---- files the program reads ----

    // The Files panel shows when the program could use files, or the user has given it some.
    private void showFilesIfUsed() {
        boolean used = !inputFiles.isEmpty() || usesFiles(code.getCode());
        if (files.isVisible() != used) {
            files.setVisible(used);
            files.getParent().revalidate();
        }
    }

    // Whether code works with files: it names one of Java's file classes. Not just "java.io", which
    // an import of IOException alone would match, or "Path", which a graph program may call a class.
    static boolean usesFiles(String code) {
        return code.matches("(?s).*\\b(FileReader|FileWriter|FileInputStream|FileOutputStream|PrintWriter|Files|Paths|File)\\b.*");
    }

    private void addInputFile() {
        File file = host.chooseFile(false, FileKind.ANY, null);
        if (file == null) {
            return;
        }
        if (file.length() > 10_000_000) {
            Dialogs.tell(this, file.getName() + " is too big", "Input files can be up to 10 MB.");
            return;
        }
        inputFiles.removeIf(f -> f.getName().equals(file.getName())); // one file per name in the folder
        inputFiles.add(file);
        useInputFiles();
    }

    private void clearInputFiles() {
        inputFiles.clear();
        useInputFiles();
    }

    private void useInputFiles() {
        List<java.nio.file.Path> paths = inputFiles.stream().map(File::toPath).toList();
        tracer.setInputFiles(paths);
        files.setInputs(paths);
        showFilesIfUsed();
        say(inputFiles.isEmpty() ? "no input files" : "input files: " + String.join(", ", inputFiles.stream().map(File::getName).toList())
                + Shortcuts.label(" · copied in before each run (ctrl+enter)"), Style.INK_SOFT);
    }

    // ---- sizes and names ----

    // Where to put the divider so the longest line isn't cut off, keeping room for the picture.
    private int dividerForCode() {
        int width = top.getWidth();
        if (width <= 0) { // not laid out yet: use the window's width
            Window window = SwingUtilities.getWindowAncestor(this);
            width = window != null && window.getWidth() > 0 ? window.getWidth() : 1300;
        }
        return Math.max((int) (width * 0.4), Math.min(code.widthForLongestLine(), (int) (width * 0.55)));
    }

    // A file name for code that has none yet: its public class (or first class), then .java.
    static String suggestedName(String code) {
        java.util.regex.Matcher publicClass = java.util.regex.Pattern
                .compile("public\\s+(?:final\\s+|abstract\\s+)?class\\s+(\\w+)").matcher(code);
        if (publicClass.find()) {
            return publicClass.group(1) + ".java";
        }
        java.util.regex.Matcher anyClass = java.util.regex.Pattern.compile("\\bclass\\s+(\\w+)").matcher(code);
        return anyClass.find() ? anyClass.group(1) + ".java" : "Main.java";
    }

    // What one press of run produced: the compile result, and the trace and pictures if it ran.
    private record RunResult(CompileResult program, Trace trace, SceneList scenes, String source, String input) {
    }
}
