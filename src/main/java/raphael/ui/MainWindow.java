package raphael.ui;

import com.formdev.flatlaf.FlatLaf;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.JToolBar;
import javax.swing.SwingWorker;
import javax.swing.text.JTextComponent;
import raphael.App;
import raphael.RaphaelException;
import raphael.compile.CompileResult;
import raphael.compile.SourceCompiler;
import raphael.io.AppFolders;
import raphael.io.DraftStore;
import raphael.io.SafeFiles;
import raphael.model.EndReason;
import raphael.model.Trace;
import raphael.render.SceneList;
import raphael.render.SceneBuilder;
import raphael.render.Style;
import raphael.trace.JdiTracer;
import raphael.trace.RunLimits;
import raphael.trace.Tracer;

// Raphael's main window. It builds the panels, connects them to the TraceModel, sets up the
// keyboard shortcuts, and runs the student's program when "run" is pressed.
// The slow work (compiling, tracing, building the pictures) runs on a background thread
// (a SwingWorker), so the window never freezes.
// OOP: inheritance (extends JFrame); composition (the window is made of panels that each do
// one job); abstraction (it only knows the Tracer interface, not JdiTracer's details).
public class MainWindow extends JFrame {

    private static final String NEW_PROGRAM = """
            public class Main {
                public static void main(String[] args) {
                    
                }
            }
            """;
    private static final String HELP = """
            run and stop
              Ctrl+Enter or F5   run; while recording, stop
              Esc                pause, or stop recording

            moving through the steps
              Space              play / pause
              Right, Left        step forward, back
              Home, End          first, last step
              Alt+Down, Alt+Up   skip call, finish call
              click a line number   jump to when that line runs
              (in the editor, hold Alt with the arrow keys)

            the editor
              Ctrl+N, O, S       new, open, save
              Ctrl+Shift+S       save as
              Ctrl+Z, Ctrl+Y     undo, redo
              Ctrl+F, F3         find, find next
              Ctrl+/             comment or uncomment lines
              Tab, Shift+Tab     move lines in, out

            the view
              Ctrl + and Ctrl -  text size (or Ctrl + wheel on the code)
              Ctrl + wheel       zoom the picture (over the picture)
              Ctrl+0             normal text size and zoom
              Ctrl+D             dark or light
              Ctrl+,             settings
            """;
    private static final String[] EXAMPLES = {"BubbleSort", "ReverseList", "BSTInsert", "FibDP", "GridPaths", "BuggyList", "OffByOne", "BTreeInsert", "BankAccounts"};

    private final TraceModel model = new TraceModel();
    private final CodePanel code = new CodePanel(model);
    private final VisualPanel visual = new VisualPanel(model);
    private final ControlsBar controls = new ControlsBar(model);
    private final CallStackPanel callStack = new CallStackPanel();
    private final LocalsPanel locals = new LocalsPanel();
    private final ConsolePanel console = new ConsolePanel();
    private final JButton runButton = new JButton("run");
    private final JLabel status = new JLabel(" ");
    private final Legend legend = new Legend();
    private final JPanel statusBar = new JPanel(new BorderLayout());
    private JToolBar toolBar;
    private boolean legendWanted = true; // false while the status bar shows an error
    private final JButton themeButton = toolButton("dark", "switch between dark and light  (Ctrl+D)");
    private final JPopupMenu examplesMenu = new JPopupMenu();
    private final Tracer tracer = new JdiTracer();
    private JSplitPane top;
    private File currentFile;
    private boolean running;
    private Timer progress;
    private String lastSearch = "";
    private RunLimits limits = savedLimits();
    private String documentName = "new program"; // shown in the title

    // Autosave: every few seconds, unsaved code is copied to a draft file (see DraftStore). The
    // writing happens on a background thread, so a slow disk never pauses the window.
    private static final Logger LOG = Logger.getLogger(MainWindow.class.getName());
    private static final int AUTOSAVE_MS = 5000;
    private final DraftStore draft = openDraft();
    private final ExecutorService draftWriter = Executors.newSingleThreadExecutor(job -> {
        Thread thread = new Thread(job, "autosave");
        thread.setDaemon(true); // never keeps Raphael running
        return thread;
    });
    private final Timer autosave = new Timer(AUTOSAVE_MS, e -> autosave());
    private String lastDraft; // what the draft file holds now; null means no draft

    public MainWindow() {
        super("raphael");
        model.addListener(code);
        model.addListener(visual);
        model.addListener(controls);
        model.addListener(callStack);
        model.addListener(locals);
        model.addListener(console);

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (okToLoseCode("quit without saving your code?")) {
                    quit();
                }
            }
        });
        getContentPane().setBackground(Style.BACKGROUND);
        toolBar = buildToolBar();
        add(toolBar, BorderLayout.NORTH);

        JScrollPane picture = new JScrollPane(visual);
        picture.setBorder(Section.hairline(0, 1, 0, 0));
        picture.getViewport().setBackground(Style.BACKGROUND);
        top = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, code, picture);
        top.setResizeWeight(0.4);
        top.setBorder(BorderFactory.createEmptyBorder());
        JPanel bottom = new JPanel(new GridBagLayout());
        bottom.setBackground(Style.BACKGROUND);
        bottom.setBorder(Section.hairline(1, 0, 0, 0));
        addBottomPanel(bottom, callStack, 0.25);
        addBottomPanel(bottom, locals, 0.45); // values can be long, so variables get the most room
        addBottomPanel(bottom, console, 0.30);
        bottom.setPreferredSize(new Dimension(100, Style.BOTTOM_ROW_H));
        JSplitPane middle = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, bottom);
        middle.setResizeWeight(1); // the top gets all the spare room
        // Whenever the window changes size, including when it first opens, the bottom row keeps its height.
        middle.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                middle.setDividerLocation(middle.getHeight() - middle.getDividerSize() - Style.BOTTOM_ROW_H);
            }
        });
        middle.setBorder(Section.hairline(1, 0, 0, 0));
        add(middle, BorderLayout.CENTER);

        status.setFont(Style.UI_FONT.deriveFont(12f));
        status.setBorder(BorderFactory.createEmptyBorder(0, Style.SPACE_3, 0, Style.SPACE_3));
        statusBar.setBorder(Section.hairline(1, 0, 0, 0));
        statusBar.setPreferredSize(new Dimension(0, Style.STATUS_H));
        statusBar.add(status, BorderLayout.CENTER);
        statusBar.add(legend, BorderLayout.EAST);
        add(statusBar, BorderLayout.SOUTH);
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                showLegendIfRoom();
            }
        });

        // Scroll panes and split panes take the arrow keys, Home and End for themselves whenever
        // something inside them has focus. Without these lines our shortcuts would never hear them.
        for (JComponent claimsKeys : new JComponent[] {picture, top, middle}) {
            claimsKeys.setInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, null);
        }

        // The stdin box only appears for a program that can read input.
        code.setOnCodeChanged(() -> {
            updateTitle();
            console.showInput(code.getCode().contains("System.in"));
            if (model.getTrace() == null) { // an old compile error no longer applies
                visual.setMessage(null, null);
                console.showMessage("");
            }
        });
        code.setOnTextSizeWheel(this::changeTextSize);

        bindKeys();
        // The usable part of the screen (without the taskbar), so the window always fits.
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setSize(Math.min(1300, screen.width), Math.min(840, screen.height));
        setLocationRelativeTo(null);
        applyTheme();
        applyTextSize();
        loadExample("BSTInsert");
        tracer.setLimits(limits);
        autosave.start();
        useAppMenu();
    }

    // On a Mac, Quit (Cmd+Q) and Settings… live in the app's own menu. Route them through the
    // same unsaved-code question and the same settings dialog as everywhere else.
    private void useAppMenu() {
        if (!Desktop.isDesktopSupported()) {
            return;
        }
        Desktop desktop = Desktop.getDesktop();
        if (desktop.isSupported(Desktop.Action.APP_QUIT_HANDLER)) {
            desktop.setQuitHandler((event, response) -> {
                if (okToLoseCode("quit without saving your code?")) {
                    quit();
                } else {
                    response.cancelQuit();
                }
            });
        }
        if (desktop.isSupported(Desktop.Action.APP_PREFERENCES)) {
            desktop.setPreferencesHandler(event -> showSettings());
        }
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

    // The picture panel, for the tests.
    JComponent getPicture() {
        return visual;
    }

    // Switches between light and dark, and remembers the choice for next time.
    private void toggleTheme() {
        boolean wasError = status.getForeground().equals(Style.ERROR);
        App.setupLook(!Style.isDark());
        App.SETTINGS.putBoolean("dark", Style.isDark());
        FlatLaf.updateUI(); // tells every Swing component to pick up the new look
        SwingUtilities.updateComponentTreeUI(examplesMenu); // it isn't in the window until it's opened
        code.restoreMarks();
        applyTheme();
        status.setForeground(wasError ? Style.ERROR : Style.INK);
    }

    // The colours Swing doesn't choose by itself. Most of the window is walked in one go.
    private void applyTheme() {
        recolor(getContentPane());
        runButton.setBackground(Style.ACCENT);
        runButton.setForeground(Style.ON_ACCENT);
        String pressed = hex(Style.ACCENT_PRESSED);
        runButton.putClientProperty("FlatLaf.style",
                "toolbar.hoverBackground: " + pressed + "; toolbar.pressedBackground: " + pressed);
        themeButton.setText(Style.isDark() ? "light" : "dark");
        toolBar.setBackground(Style.BAR); // the bars frame the work area in a slightly different shade
        statusBar.setBackground(Style.BAR);
        repaint();
    }

    private static void recolor(Container container) {
        if (container instanceof JPanel || container instanceof JViewport || container instanceof JToolBar
                || container instanceof JTable || container instanceof JList || container instanceof JTextComponent) {
            container.setBackground(Style.BACKGROUND);
        }
        if (container instanceof JTable || container instanceof JList || container instanceof JTextComponent) {
            container.setForeground(Style.INK);
        }
        if (container instanceof JTextComponent) {
            JTextComponent text = (JTextComponent) container;
            text.setCaretColor(Style.INK);
            text.setSelectionColor(Style.ACCENT_TINT);
            text.setSelectedTextColor(Style.INK);
        }
        for (Component child : container.getComponents()) {
            if (child instanceof Container) {
                recolor((Container) child);
            }
        }
    }

    // Makes the code and panel text bigger or smaller, for a projector, and remembers it.
    private void changeTextSize(int change) {
        Style.setCodeSize(Style.CODE_FONT.getSize() + change);
        App.SETTINGS.putInt("textSize", Style.CODE_FONT.getSize());
        applyTextSize();
        say("text size " + Style.CODE_FONT.getSize() + Shortcuts.label(" · ctrl+0 for normal"), Style.INK_SOFT);
    }

    private void resetView() {
        changeTextSize(Style.DEFAULT_CODE_SIZE - Style.CODE_FONT.getSize());
        visual.resetZoom();
    }

    private void applyTextSize() {
        refont(getContentPane());
        code.applyFont();
        if (top.getWidth() > 0 && top.getDividerLocation() < dividerForCode()) {
            top.setDividerLocation(dividerForCode());
        }
    }

    private static void refont(Container container) {
        if (container instanceof JTextComponent || container instanceof JList || container instanceof JTable) {
            container.setFont(Style.CODE_FONT);
        }
        if (container instanceof JTable) {
            JTable table = (JTable) container;
            table.setRowHeight(table.getFontMetrics(Style.CODE_FONT).getHeight() + 4);
        }
        for (Component child : container.getComponents()) {
            if (child instanceof Container) {
                refont((Container) child);
            }
        }
    }

    private void find() {
        String text = JOptionPane.showInputDialog(this, "find:", lastSearch);
        if (text != null) {
            lastSearch = text;
            findNext();
        }
    }

    private void findNext() {
        if (!lastSearch.isEmpty() && !code.find(lastSearch)) {
            say("\"" + lastSearch + "\" isn't in the code", Style.INK_SOFT);
        }
    }

    private void showHelp() {
        JTextArea text = new JTextArea(helpText(Shortcuts.isMac()));
        text.setEditable(false);
        text.setFont(Style.UI_FONT);
        text.setBackground(Style.BACKGROUND);
        text.setForeground(Style.INK);
        JOptionPane.showMessageDialog(this, text, "raphael · keys", JOptionPane.PLAIN_MESSAGE);
    }

    // HELP with this computer's key names. On a Mac "Ctrl" becomes "Cmd" and "Alt" becomes
    // "Option", which changes their length, so the lines are laid out again: the keys, then the
    // descriptions in one column just past the longest keys.
    static String helpText(boolean mac) {
        String[] lines = HELP.split("\n");
        String[][] rows = new String[lines.length][]; // the keys and what they do, or null
        int width = 0;
        for (int i = 0; i < lines.length; i++) {
            String[] parts = lines[i].split("(?<=\\S)\\s{2,}", 2);
            if (lines[i].startsWith("  ") && parts.length == 2) {
                rows[i] = new String[] {Shortcuts.label(parts[0], mac), Shortcuts.label(parts[1], mac)};
                width = Math.max(width, rows[i][0].length() + 2);
            }
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (rows[i] != null) {
                out.append(rows[i][0]).append(" ".repeat(width - rows[i][0].length())).append(rows[i][1]);
            } else {
                out.append(Shortcuts.label(lines[i], mac));
            }
            out.append('\n');
        }
        return out.toString();
    }

    private static String hex(Color color) {
        return String.format("#%06x", color.getRGB() & 0xFFFFFF);
    }

    private JToolBar buildToolBar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.setBorder(BorderFactory.createCompoundBorder(Section.hairline(0, 0, 1, 0),
                BorderFactory.createEmptyBorder(Style.SPACE_1, Style.SPACE_3, Style.SPACE_1, Style.SPACE_3)));

        JButton newProgram = toolButton("new", "start a new program, or paste one in  (Ctrl+N)");
        newProgram.addActionListener(e -> newProgram());
        JButton open = toolButton("open", "open a .java file  (Ctrl+O)");
        open.addActionListener(e -> open());
        JButton save = toolButton("save", "save the code  (Ctrl+S)");
        save.addActionListener(e -> save());
        JButton examples = toolButton("examples", "load an example program");
        for (String name : EXAMPLES) {
            JMenuItem item = new JMenuItem(name);
            item.addActionListener(e -> loadExample(name));
            examplesMenu.add(item);
        }
        examples.addActionListener(e -> examplesMenu.show(examples, 0, examples.getHeight()));
        themeButton.addActionListener(e -> toggleTheme());
        JButton settings = toolButton("settings", "steps, timeout, memory, text size and theme  (Ctrl+,)");
        settings.addActionListener(e -> showSettings());
        JButton help = toolButton("keys", "all the keyboard shortcuts  (F1)");
        help.addActionListener(e -> showHelp());
        runButton.setToolTipText(Shortcuts.label("compile and trace the program  (Ctrl+Enter or F5)"));
        runButton.setFocusable(false);
        // The one filled control in the window. applyTheme() sets its colours: FlatLaf would turn a
        // toolbar button grey under the mouse, which white text can't be read on.
        runButton.setFont(Style.UI_FONT.deriveFont(java.awt.Font.BOLD));
        runButton.addActionListener(e -> run());

        bar.add(newProgram);
        bar.add(open);
        bar.add(save);
        bar.add(examples);
        bar.addSeparator();
        bar.add(runButton);
        bar.addSeparator();
        bar.add(controls);
        bar.addSeparator();
        bar.add(themeButton);
        bar.add(settings);
        bar.add(help);
        return bar;
    }

    private static JButton toolButton(String text, String tip) {
        JButton button = new JButton(text);
        button.setToolTipText(Shortcuts.label(tip));
        button.setFocusable(false);
        return button;
    }

    // Keyboard shortcuts that work anywhere in the window. (In the editor, the arrow keys and
    // Space still type; use the Alt versions there.)
    private void bindKeys() {
        bind("control ENTER", this::run);
        bind("F5", this::run);
        bind("control S", this::save);
        bind("shift control S", this::saveAs);
        bind("control O", this::open);
        bind("control N", this::newProgram);
        bind("control F", this::find);
        bind("F3", this::findNext);
        bind("F1", this::showHelp);
        bind("control D", this::toggleTheme);
        bind("control COMMA", this::showSettings);
        bind("ESCAPE", () -> {
            if (running) {
                tracer.stop();
            } else {
                controls.pause();
            }
        });
        for (String key : new String[] {"control EQUALS", "shift control EQUALS", "control PLUS", "control ADD"}) {
            bind(key, () -> changeTextSize(1));
        }
        for (String key : new String[] {"control MINUS", "control SUBTRACT"}) {
            bind(key, () -> changeTextSize(-1));
        }
        bind("control 0", this::resetView);
        // Only heard when the keyboard is outside the editor; inside it, these keys already work.
        bind("control V", code::pasteHere);
        bind("control A", code::selectAllHere);
        bind("RIGHT", model::next);
        bind("alt RIGHT", model::next);
        bind("LEFT", model::previous);
        bind("alt LEFT", model::previous);
        bind("alt DOWN", model::stepOver);
        bind("alt UP", model::stepOut);
        bind("HOME", model::first);
        bind("alt HOME", model::first);
        bind("END", model::last);
        bind("alt END", model::last);
        bind("SPACE", controls::togglePlay);
    }

    private void bind(String key, Runnable action) {
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(Shortcuts.key(key), key);
        getRootPane().getActionMap().put(key, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    // Compile, trace and draw on a background thread, then show the result on the Swing thread.
    private void run() {
        if (running) {
            tracer.stop(); // while recording, the run button is the stop button
            return;
        }
        String source = code.getCode();
        String input = console.getInput();
        if (top.getDividerLocation() < dividerForCode()) {
            top.setDividerLocation(dividerForCode()); // the code may have grown since it was loaded
        }
        running = true;
        runButton.setText("stop");
        visual.setRecording(true);
        progress = new Timer(200, e -> {
            if (tracer.stepsSoFar() > 0) {
                say("recording your program: " + tracer.stepsSoFar() + " steps · esc stops", Style.INK_SOFT);
            }
        });
        progress.start();
        code.setEditable(false);
        model.clear();
        visual.setMessage(null, null);
        say("compiling…", Style.INK_SOFT);

        // An anonymous subclass of SwingWorker: doInBackground() runs on a worker thread,
        // process() and done() run on the Swing thread.
        new SwingWorker<RunResult, String>() {
            @Override
            protected RunResult doInBackground() throws Exception {
                CompileResult program = new SourceCompiler().compile(source);
                if (!program.success()) {
                    return new RunResult(program, null, null);
                }
                Trace trace = tracer.trace(program, input);
                publish("drawing " + trace.size() + " steps…");
                return new RunResult(program, trace, new SceneBuilder(source).build(trace));
            }

            @Override
            protected void process(List<String> messages) {
                say(messages.get(messages.size() - 1), Style.INK_SOFT);
            }

            @Override
            protected void done() {
                running = false;
                progress.stop();
                runButton.setText("run");
                visual.setRecording(false);
                code.setEditable(true);
                try {
                    show(get());
                } catch (ExecutionException e) {
                    runFailed(e.getCause());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }.execute();
    }

    // A run that ended with an exception instead of a result. A RaphaelException is a problem
    // Raphael knows how to explain (no compiler, the program's JVM wouldn't start); anything else
    // is a bug in Raphael, which ErrorReporter logs and reports.
    private void runFailed(Throwable cause) {
        if (cause instanceof RaphaelException) {
            LOG.log(Level.WARNING, "A run couldn't start", cause);
            visual.setMessage("couldn't run:", cause.getMessage());
            say("[error] " + cause.getMessage(), Style.ERROR);
        } else {
            ErrorReporter.report(cause);
            visual.setMessage("something went wrong inside raphael:", cause.toString());
            say("[error] something went wrong inside raphael · the details are in its log", Style.ERROR);
        }
    }

    private void show(RunResult result) {
        if (result.trace() == null) {
            LOG.info("Didn't compile: " + result.program().errors().size() + " error(s)");
            List<CompileResult.CompileError> errors = result.program().errors();
            code.showErrors(errors);
            StringBuilder text = new StringBuilder();
            for (CompileResult.CompileError error : errors) {
                text.append(error.line() > 0 ? "line " + error.line() + ": " : "").append(error.message()).append("\n");
            }
            console.showMessage(text.toString());
            CompileResult.CompileError first = errors.get(0);
            String where = (first.line() > 0 ? "line " + first.line() + ": " : "") + first.message();
            String more = errors.size() > 1 ? " (+" + (errors.size() - 1) + " more in the console)" : "";
            visual.setMessage("didn't compile:", where + more);
            say("[error] " + where.lines().findFirst().orElse("") + more, Style.ERROR);
            return;
        }
        Trace trace = result.trace();
        LOG.info("Ran: " + trace.size() + " steps, " + trace.getEndReason());
        model.load(trace, result.scenes(), result.scenes().getRunSize());
        visual.requestFocusInWindow(); // so the arrow keys step the program, not the editor's cursor
        controls.play();
        String steps = "recorded " + trace.size() + " steps · space pauses";
        if (trace.getEndReason() == EndReason.FINISHED) {
            say("[ok] " + steps, Style.INK);
        } else if (trace.getEndMessage().startsWith("NoSuchElementException") && code.getCode().contains("System.in")) {
            visual.setMessage("needs input:", "this program reads input. type it into the stdin box below, then press run.");
            say("[needs input] type it into the stdin box, then run again", Style.ERROR);
        } else {
            say("[" + trace.getEndReason().name().toLowerCase(Locale.ROOT).replace('_', ' ') + "] " + trace.getEndMessage()
                    + " · " + steps, Style.ERROR);
        }
    }

    private void say(String message, Color color) {
        legendWanted = !Style.ERROR.equals(color); // during an error, the message outranks the key
        showLegendIfRoom();
        status.setText(message);
        status.setForeground(color);
    }

    // The key to the picture, unless there's an error to show or the window is too narrow for both.
    private void showLegendIfRoom() {
        legend.setVisible(legendWanted && getWidth() >= 1100);
    }

    // Where to put the divider so the longest line isn't cut off, keeping room for the picture.
    private int dividerForCode() {
        int width = top.getWidth() > 0 ? top.getWidth() : getWidth(); // not laid out yet when the window opens
        return Math.max((int) (width * 0.4), Math.min(code.widthForLongestLine(), (int) (width * 0.55)));
    }

    // Asks first if replacing the editor's text would lose changes that haven't been saved.
    private boolean okToReplaceCode() {
        return okToLoseCode("replace your unsaved code?");
    }

    private void showSettings() {
        SettingsDialog.Values chosen = SettingsDialog.ask(this,
                new SettingsDialog.Values(limits, Style.CODE_FONT.getSize(), Style.isDark()));
        if (chosen == null) {
            return;
        }
        limits = chosen.limits();
        App.SETTINGS.putInt("maxSteps", limits.maxSteps());
        App.SETTINGS.putInt("timeoutSeconds", limits.timeoutSeconds());
        App.SETTINGS.putInt("memoryMb", limits.memoryMb());
        tracer.setLimits(limits);
        if (chosen.dark() != Style.isDark()) {
            toggleTheme();
        }
        if (chosen.textSize() != Style.CODE_FONT.getSize()) {
            changeTextSize(chosen.textSize() - Style.CODE_FONT.getSize());
        }
        say("settings saved · the next run uses them", Style.INK_SOFT);
    }

    // The limits saved in settings. If they were changed outside Raphael and are out of range, the defaults.
    private static RunLimits savedLimits() {
        try {
            return new RunLimits(App.SETTINGS.getInt("maxSteps", RunLimits.DEFAULTS.maxSteps()),
                    App.SETTINGS.getInt("timeoutSeconds", RunLimits.DEFAULTS.timeoutSeconds()),
                    App.SETTINGS.getInt("memoryMb", RunLimits.DEFAULTS.memoryMb()));
        } catch (IllegalArgumentException e) {
            LOG.log(Level.WARNING, "Saved limits are out of range; using the defaults", e);
            return RunLimits.DEFAULTS;
        }
    }

    private static DraftStore openDraft() {
        try {
            return new DraftStore(AppFolders.data().resolve("draft.java"));
        } catch (IOException e) {
            LOG.log(Level.WARNING, "No folder for the autosaved draft; autosave is off", e);
            return null;
        }
    }

    // Copies unsaved code to the draft, or removes the draft once nothing is unsaved.
    private void autosave() {
        String text = code.isDirty() ? code.getCode() : null;
        if (draft == null || Objects.equals(text, lastDraft)) {
            return;
        }
        lastDraft = text;
        draftWriter.execute(() -> {
            try {
                if (text == null) {
                    draft.clear();
                } else {
                    draft.save(text);
                }
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Autosave failed", e);
            }
        });
    }

    // Called once the window is showing. A draft left from last time means Raphael didn't quit
    // normally (a normal quit removes it), so offer the code back.
    public void offerDraft() {
        if (draft == null) {
            return;
        }
        String saved;
        try {
            saved = draft.load();
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Couldn't read the autosaved draft", e);
            return;
        }
        if (saved == null || saved.isBlank()) {
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this,
                "Raphael didn't close normally last time.\nBring back the unsaved code you were working on?",
                "raphael · restore your code", JOptionPane.YES_NO_OPTION);
        if (answer == JOptionPane.YES_OPTION) {
            code.setCode(saved);
            code.markUnsaved();
            top.setDividerLocation(dividerForCode());
            currentFile = null;
            setDocument("restored code");
            say(Shortcuts.label("restored your unsaved code · ctrl+s saves it"), Style.INK_SOFT);
        } else {
            lastDraft = "";  // differs from "no draft", so the next autosave removes the file
        }
    }

    // A normal quit: no draft is left behind, so the next start doesn't offer one.
    private void quit() {
        autosave.stop();
        draftWriter.shutdown();
        try {
            draftWriter.awaitTermination(2, TimeUnit.SECONDS); // a save in progress finishes first
            if (draft != null) {
                draft.clear();
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Couldn't remove the autosaved draft", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        LOG.info("Raphael quit");
        System.exit(0);
    }

    private boolean okToLoseCode(String question) {
        return !code.isDirty() || JOptionPane.showConfirmDialog(this, question, "raphael",
                JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION;
    }

    private void newProgram() {
        if (!okToReplaceCode()) {
            return;
        }
        code.setCode(NEW_PROGRAM);
        code.startTypingAt(3, 8);
        currentFile = null;
        setDocument("new program");
        say(Shortcuts.label("type your code, or ctrl+a then ctrl+v to paste a whole program · then press run (ctrl+enter)"), Style.INK_SOFT);
    }

    private void loadExample(String name) {
        if (!okToReplaceCode()) {
            return;
        }
        try (InputStream in = getClass().getResourceAsStream("/examples/" + name + ".java")) {
            code.setCode(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            top.setDividerLocation(dividerForCode());
            currentFile = null;
            setDocument(name + ".java (example)");
            say("loaded " + name + Shortcuts.label(".java · press run (ctrl+enter)"), Style.INK_SOFT);
        } catch (IOException | NullPointerException e) { // the examples are inside Raphael, so this is a bug
            ErrorReporter.report(e);
            say("[error] could not load the example " + name, Style.ERROR);
        }
    }

    // Where open and save start: the Desktop, if there is one. Inside WSL (Linux running on
    // Windows), that's the Windows Desktop, under /mnt/c. Otherwise null, which means the usual
    // home folder.
    private static File startFolder() {
        if (System.getenv("WSL_DISTRO_NAME") != null) {
            File windowsDesktop = new File("/mnt/c/Users/" + System.getProperty("user.name") + "/Desktop");
            if (windowsDesktop.isDirectory()) {
                return windowsDesktop;
            }
        }
        File desktop = new File(System.getProperty("user.home"), "Desktop");
        return desktop.isDirectory() ? desktop : null;
    }

    private void open() {
        if (!okToReplaceCode()) { // ask before the file dialog, not after picking a file
            return;
        }
        File file = chooseFile(false);
        if (file == null) {
            return;
        }
        try {
            code.setCode(SafeFiles.readCode(file.toPath()));
            top.setDividerLocation(dividerForCode());
            currentFile = file;
            setDocument(file.getName());
            say("opened " + file.getName() + Shortcuts.label(" · press run (ctrl+enter)"), Style.INK_SOFT);
        } catch (IOException e) {
            LOG.log(Level.INFO, "Couldn't open " + file, e);
            JOptionPane.showMessageDialog(this, "Couldn't open " + file.getName() + ":\n"
                    + SafeFiles.describe(e), "Raphael", JOptionPane.WARNING_MESSAGE);
        }
    }

    // Saves to the current file, or asks where if there isn't one yet. True if it was saved.
    private boolean save() {
        return currentFile != null ? saveTo(currentFile) : saveAs();
    }

    private boolean saveAs() {
        File file = chooseFile(true);
        return file != null && saveTo(file);
    }

    private boolean saveTo(File file) {
        try {
            SafeFiles.write(file.toPath(), code.getCode());
            currentFile = file;
            code.markSaved();
            setDocument(file.getName());
            say("[saved] " + file.getName(), Style.INK_SOFT);
            return true;
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Couldn't save " + file, e);
            JOptionPane.showMessageDialog(this, "Couldn't save " + file.getName() + ":\n"
                    + SafeFiles.describe(e) + "\nYour code is still here; try saving somewhere else.",
                    "Raphael", JOptionPane.WARNING_MESSAGE);
            currentFile = null; // so the next save asks where
            return false;
        }
    }

    // The computer's own Open or Save dialog: Explorer's on Windows, Finder's on macOS, GTK's on
    // Linux (and inside WSL). Swing's JFileChooser looks and works like neither. Null if cancelled.
    private File chooseFile(boolean forSaving) {
        FileDialog dialog = new FileDialog(this, forSaving ? "Save As" : "Open", forSaving ? FileDialog.SAVE : FileDialog.LOAD);
        dialog.setDirectory(lastFolder());
        if (forSaving) {
            dialog.setFile(currentFile != null ? currentFile.getName() : suggestedName(code.getCode()));
        } else {
            dialog.setFilenameFilter((folder, name) -> name.toLowerCase(Locale.ROOT).endsWith(".java"));
            if (Shortcuts.isWindows()) {
                dialog.setFile("*.java"); // Windows ignores the filter above; this pattern filters instead
            }
        }
        dialog.setVisible(true); // waits until the user picks or cancels
        String name = dialog.getFile();
        if (name == null) {
            return null;
        }
        App.SETTINGS.put("lastFolder", dialog.getDirectory());
        if (forSaving && name.indexOf('.') < 0) {
            name += ".java";
            File file = new File(dialog.getDirectory(), name);
            // The dialog only asked about replacing the name that was typed, without ".java".
            if (file.exists() && JOptionPane.showConfirmDialog(this, file.getName() + " already exists. Replace it?",
                    "Raphael", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
                return null;
            }
            return file;
        }
        return new File(dialog.getDirectory(), name);
    }

    // Where the file dialogs open: the current file's folder, the last folder used, or the Desktop.
    private String lastFolder() {
        if (currentFile != null && currentFile.getParent() != null) {
            return currentFile.getParent();
        }
        String last = App.SETTINGS.get("lastFolder", null);
        if (last != null && new File(last).isDirectory()) {
            return last;
        }
        File start = startFolder();
        return start != null ? start.getPath() : System.getProperty("user.home");
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

    // The window's title: the document's name, with a dot while it has unsaved changes. On a
    // Mac, the title is just the name, and the dot goes in the close button, as Mac apps do it.
    private void setDocument(String name) {
        documentName = name;
        updateTitle();
    }

    private void updateTitle() {
        boolean unsaved = code.isDirty();
        if (Shortcuts.isMac()) {
            setTitle(documentName);
            getRootPane().putClientProperty("Window.documentModified", unsaved);
            getRootPane().putClientProperty("Window.documentFile", currentFile);
        } else {
            setTitle((unsaved ? "● " : "") + documentName + " — Raphael");
        }
    }

    // What one press of run produced: the compile result, and the trace and pictures if it ran.
    private record RunResult(CompileResult program, Trace trace, SceneList scenes) {
    }
}
