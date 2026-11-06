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
import java.awt.event.KeyEvent;
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
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JViewport;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.JToolBar;
import javax.swing.SwingWorker;
import javax.swing.text.JTextComponent;
import raphael.App;
import raphael.RaphaelException;
import raphael.compile.CompileHints;
import raphael.compile.CompileResult;
import raphael.compile.SourceCompiler;
import raphael.io.AppFolders;
import raphael.io.AppLog;
import raphael.io.DraftStore;
import raphael.io.SafeFiles;
import raphael.io.UpdateCheck;
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
              (In the editor, hold Alt with the arrow keys.)

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
              F6, Shift+F6       move between toolbar, code and picture
            """;
    private static final String[] EXAMPLES = {"BubbleSort", "ReverseList", "BSTInsert", "FibDP", "GridPaths", "BuggyList", "OffByOne", "BTreeInsert", "BankAccounts", "Exceptions"};

    private final TraceModel model = new TraceModel();
    private final CodePanel code = new CodePanel(model);
    private final VisualPanel visual = new VisualPanel(model);
    private final ControlsBar controls = new ControlsBar(model);
    private final CallStackPanel callStack = new CallStackPanel();
    private final LocalsPanel locals = new LocalsPanel();
    private final ConsolePanel console = new ConsolePanel();
    private final JButton runButton = new JButton("Run"); // the stop button while recording
    private final JLabel status = new JLabel(" ");
    private final Legend legend = new Legend();
    // "Raphael 1.1.0 is available ↗": stays in the status bar, and opens the download page.
    private final JButton updateNotice = new JButton();
    private final JPanel statusBar = new JPanel(new BorderLayout());
    private JToolBar toolBar;
    private JButton examplesButton;
    // The picture area shows either the picture or, before the first run, a short guide.
    private final java.awt.CardLayout pictureCards = new java.awt.CardLayout();
    private final JPanel pictureArea = new JPanel(pictureCards);
    // A thin moving bar under the toolbar while a program is being recorded.
    private final javax.swing.JProgressBar busy = new javax.swing.JProgressBar();
    private EmptyState emptyState;
    private JSplitPane middle; // the work area above, the call stack, variables and console below
    private boolean legendWanted = true; // false while the status bar shows an error
    private final JButton themeButton = ControlsBar.button(Icons.Glyph.THEME, "Dark mode", "Dark mode  (Ctrl+D)");
    private final JPopupMenu examplesMenu = new JPopupMenu();
    private final Tracer tracer = new JdiTracer();
    private JSplitPane top;
    private File currentFile;
    private boolean running;
    private Timer progress;
    private String lastSearch = "";
    private volatile int recordedSteps; // set by the background run, read on Swing's thread
    private RunLimits limits = savedLimits();
    private String documentName = "new program"; // shown in the title

    // Autosave: every few seconds, unsaved code is copied to a draft file (see DraftStore). The
    // writing happens on a background thread, so a slow disk never pauses the window.
    private static final Logger LOG = Logger.getLogger(MainWindow.class.getName());
    private static final int AUTOSAVE_MS = 5000;
    private static final int COLLAPSED_ROW_H = 28; // the bottom panels with only their headings showing
    private final DraftStore draft = openDraft();
    private final ExecutorService draftWriter = Executors.newSingleThreadExecutor(job -> {
        Thread thread = new Thread(job, "autosave");
        thread.setDaemon(true); // never keeps Raphael running
        return thread;
    });
    private final Timer autosave = new Timer(AUTOSAVE_MS, e -> autosave());
    private String lastDraft; // what the draft file holds now; null means no draft

    public MainWindow() {
        super("Raphael");
        setIconImages(Logo.windowIcons()); // the title bar and the taskbar
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
                if (okToLoseCode()) {
                    quit();
                }
            }
        });
        getContentPane().setBackground(Style.BACKGROUND);
        setJMenuBar(buildMenuBar());
        toolBar = buildToolBar();
        busy.setIndeterminate(true);
        busy.setBorder(BorderFactory.createEmptyBorder());
        busy.setPreferredSize(new Dimension(0, 2));
        busy.setVisible(false);
        JPanel north = new JPanel(new BorderLayout());
        north.add(toolBar, BorderLayout.CENTER);
        north.add(busy, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);

        JScrollPane picture = new JScrollPane(visual);
        picture.setBorder(BorderFactory.createEmptyBorder());
        picture.getViewport().setBackground(Style.BACKGROUND);
        pictureArea.setBorder(Section.hairline(0, 1, 0, 0));
        pictureArea.add(picture, "picture");
        emptyState = new EmptyState(EXAMPLES, !WindowsFileDialog.available(), this::loadExample,
                () -> examplesMenu.show(examplesButton, 0, examplesButton.getHeight()));
        JScrollPane guide = new JScrollPane(emptyState, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        guide.setBorder(BorderFactory.createEmptyBorder());
        pictureArea.add(guide, "empty");
        model.addListener(m -> showPictureOrGuide());
        // Drop a .java file anywhere on the window to open it.
        setTransferHandler(new FileDrop(null, this::openDropped));
        visual.setTransferHandler(new FileDrop(null, this::openDropped));
        code.getEditor().setTransferHandler(new FileDrop(code.getEditor().getTransferHandler(), this::openDropped));
        top = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, code, pictureArea);
        top.setResizeWeight(0.4);
        top.setBorder(BorderFactory.createEmptyBorder());
        JPanel bottom = new JPanel(new GridBagLayout());
        bottom.setBackground(Style.BACKGROUND);
        bottom.setBorder(Section.hairline(1, 0, 0, 0));
        addBottomPanel(bottom, callStack, 0.25);
        addBottomPanel(bottom, locals, 0.45); // values can be long, so variables get the most room
        addBottomPanel(bottom, console, 0.30);
        bottom.setPreferredSize(new Dimension(100, Style.BOTTOM_ROW_H));
        bottom.setMinimumSize(new Dimension(0, COLLAPSED_ROW_H));
        middle = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, bottom);
        middle.setResizeWeight(1); // the top gets all the spare room
        // Whenever the window changes size, including when it first opens, the bottom row keeps its height.
        middle.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                fitBottomRow();
            }
        });
        middle.setBorder(Section.hairline(1, 0, 0, 0));
        add(middle, BorderLayout.CENTER);

        status.setFont(Style.UI_FONT.deriveFont(12f));
        status.setBorder(BorderFactory.createEmptyBorder(0, Style.SPACE_3, 0, Style.SPACE_3));
        statusBar.setBorder(Section.hairline(1, 0, 0, 0));
        statusBar.setPreferredSize(new Dimension(0, Style.STATUS_H));
        statusBar.add(status, BorderLayout.CENTER);
        JPanel statusRight = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, Style.SPACE_3, 0));
        statusRight.setOpaque(false);
        updateNotice.putClientProperty("JButton.buttonType", "borderless");
        updateNotice.setFont(Style.UI_FONT.deriveFont(12f));
        updateNotice.setRequestFocusEnabled(false);
        updateNotice.setVisible(false);
        updateNotice.addActionListener(e -> openReleasePage(App.SETTINGS.get("newerPage", UpdateCheck.RELEASES_PAGE)));
        statusRight.add(updateNotice);
        statusRight.add(legend);
        statusBar.add(statusRight, BorderLayout.EAST);
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
            if (model.getTrace() == null) { // an old run or compile error no longer applies
                visual.setMessage(null, null);
                console.showMessage("");
                showPictureOrGuide();
                if (!running) {
                    say(Shortcuts.label("code changed · press run (ctrl+enter)"), Style.INK_SOFT);
                }
            }
        });
        code.setOnTextSizeWheel(this::changeTextSize);

        bindKeys();
        // The usable part of the screen (without the taskbar), so the window always fits.
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setSize(Math.min(1300, screen.width), Math.min(840, screen.height));
        setMinimumSize(new Dimension(Math.min(960, screen.width), Math.min(600, screen.height)));
        setLocationRelativeTo(null);
        applyTheme();
        applyTextSize();
        loadExample("BSTInsert");
        tracer.setLimits(limits);
        autosave.start();
        useAppMenu();
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                code.getEditor().requestFocusInWindow(); // ready to type or paste, not on the first toolbar button
                checkForUpdatesQuietly();
            }
        });
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
                if (okToLoseCode()) {
                    quit();
                } else {
                    response.cancelQuit();
                }
            });
        }
        if (desktop.isSupported(Desktop.Action.APP_PREFERENCES)) {
            desktop.setPreferencesHandler(event -> showSettings());
        }
        if (desktop.isSupported(Desktop.Action.APP_ABOUT)) {
            desktop.setAboutHandler(event -> showAbout());
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
        styleRunButton();
        String mode = Style.isDark() ? "Light mode" : "Dark mode";
        themeButton.setToolTipText(Shortcuts.label(mode + "  (Ctrl+D)"));
        themeButton.getAccessibleContext().setAccessibleName(mode);
        toolBar.setBackground(Style.BAR); // the bars frame the work area in a slightly different shade
        updateNotice.setForeground(Style.ACCENT);
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
        javax.swing.JTextField field = new javax.swing.JTextField(lastSearch, 24);
        field.putClientProperty("JTextField.placeholderText", "text to find in the code");
        field.selectAll();
        String answer = Dialogs.question(this, "Find in the code").title("Find").extra(field)
                .buttons("Find", "Close").ask();
        if ("Find".equals(answer)) {
            lastSearch = field.getText();
            findNext();
        }
    }

    private void findNext() {
        if (!lastSearch.isEmpty() && !code.find(lastSearch)) {
            say("\"" + lastSearch + "\" isn't in the code", Style.INK_SOFT);
        }
    }

    // The keyboard shortcuts: a heading for each part, then each key in a key-cap chip with what it
    // does beside it. It scrolls if it's taller than 70% of the screen, so Close is always visible.
    private void showHelp() {
        JPanel list = new JPanel(new GridBagLayout());
        list.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.gridy = 0;
        for (String[] row : helpRows(Shortcuts.isMac())) {
            c.gridx = 0;
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            if (row[0].equals("section")) {
                c.gridwidth = 2;
                c.insets = new java.awt.Insets(c.gridy == 0 ? 0 : Style.SPACE_6, 0, Style.SPACE_1, 0);
                list.add(Section.header(row[1]), c);
            } else if (row[0].equals("note")) {
                c.gridwidth = 2;
                c.insets = new java.awt.Insets(Style.SPACE_1, 0, 0, 0);
                JLabel note = new JLabel(row[1]);
                note.setFont(Style.sans(false, 12f));
                note.setForeground(Style.MUTED);
                list.add(note, c);
            } else {
                c.gridwidth = 1;
                c.insets = new java.awt.Insets(Style.SPACE_1, 0, Style.SPACE_1, Style.SPACE_3);
                list.add(EmptyState.keyChip(row[1]), c);
                c.gridx = 1;
                c.weightx = 1;
                c.fill = GridBagConstraints.HORIZONTAL;
                c.insets = new java.awt.Insets(Style.SPACE_1, 0, Style.SPACE_1, 0);
                JLabel what = new JLabel(row[2]);
                what.setFont(Style.sans(false, 13f));
                what.setForeground(Style.INK);
                list.add(what, c);
            }
            c.gridy++;
        }
        JScrollPane scroll = new JScrollPane(list, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        int screen = getGraphicsConfiguration().getBounds().height;
        int tallest = screen * 7 / 10 - 160; // room for the heading and the buttons
        scroll.setPreferredSize(new Dimension(Math.max(440, list.getPreferredSize().width + 20),
                Math.min(list.getPreferredSize().height + 4, Math.max(160, tallest))));
        Dialogs.question(this, null).title("Keyboard shortcuts").extra(scroll) // the title bar says what it is
                .buttons("Close", null).ask();
    }

    // HELP as rows to show, with this computer's key names (Cmd and Option on a Mac):
    // {"section", name}, {"key", keys, what they do} or {"note", text}.
    static List<String[]> helpRows(boolean mac) {
        List<String[]> rows = new java.util.ArrayList<>();
        for (String line : HELP.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            if (!line.startsWith("  ")) {
                rows.add(new String[] {"section", line.trim()});
            } else if (line.trim().startsWith("(")) {
                String note = line.trim();
                rows.add(new String[] {"note", Shortcuts.label(note.substring(1, note.length() - 1), mac)});
            } else {
                String[] parts = line.trim().split("\\s{2,}", 2); // the keys, then what they do
                rows.add(new String[] {"key", Shortcuts.keycap(parts[0], mac), Shortcuts.label(parts[1], mac)});
            }
        }
        return rows;
    }

    private static String hex(Color color) {
        return String.format("#%06x", color.getRGB() & 0xFFFFFF);
    }

    // The toolbar, in groups split by thin dividers: the file buttons and examples; Run; the step
    // controls; then theme, settings and help. All but Run and Examples are 32 px icon buttons.
    private JToolBar buildToolBar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.setBorder(BorderFactory.createCompoundBorder(Section.hairline(0, 0, 1, 0),
                BorderFactory.createEmptyBorder(Style.SPACE_2, Style.SPACE_3, Style.SPACE_2, Style.SPACE_3)));
        // A toolbar normally moves the keyboard focus with the arrow keys; here they step the program.
        bar.setInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, null);

        JButton newProgram = ControlsBar.button(Icons.Glyph.NEW, "New program", "New program, or paste one in  (Ctrl+N)");
        newProgram.addActionListener(e -> newProgram());
        JButton open = ControlsBar.button(Icons.Glyph.OPEN, "Open", "Open a .java file  (Ctrl+O)");
        open.addActionListener(e -> open());
        JButton save = ControlsBar.button(Icons.Glyph.SAVE, "Save", "Save  (Ctrl+S)");
        save.addActionListener(e -> save());
        JButton examples = new JButton("Examples", Icons.of(Icons.Glyph.CHEVRON_DOWN, Icons.Role.TOOL));
        examplesButton = examples;
        examples.setHorizontalTextPosition(SwingConstants.LEADING); // the arrow after the word
        examples.setToolTipText("Load an example program");
        examples.setRequestFocusEnabled(false);
        examples.putClientProperty("JButton.buttonType", "toolBarButton");
        for (String name : EXAMPLES) {
            JMenuItem item = new JMenuItem(name);
            item.addActionListener(e -> loadExample(name));
            examplesMenu.add(item);
        }
        examples.addActionListener(e -> examplesMenu.show(examples, 0, examples.getHeight()));
        themeButton.addActionListener(e -> toggleTheme());
        JButton settings = ControlsBar.button(Icons.Glyph.SETTINGS, "Settings", "Settings: steps, time, memory, text size, theme  (Ctrl+,)");
        settings.addActionListener(e -> showSettings());
        JButton help = ControlsBar.button(Icons.Glyph.HELP, "Keyboard shortcuts", "Keyboard shortcuts  (F1)");
        help.addActionListener(e -> showHelp());
        runButton.setRequestFocusEnabled(false);
        runButton.setFont(Style.sans(true, Style.UI_SIZE));
        runButton.setIconTextGap(Style.SPACE_2);
        runButton.addActionListener(e -> run());

        bar.add(newProgram);
        bar.add(open);
        bar.add(save);
        bar.add(examples);
        bar.add(divider());
        bar.add(runButton);
        bar.add(divider());
        bar.add(controls);
        bar.add(divider());
        bar.add(themeButton);
        bar.add(settings);
        bar.add(help);
        for (java.awt.Component part : bar.getComponents()) {
            if (part instanceof javax.swing.AbstractButton) {
                // A toolbar makes its buttons unreachable with Tab; let Tab reach them (a click
                // still doesn't take the focus, so Space and the arrows keep stepping the program).
                part.setFocusable(true);
            }
        }
        for (JButton tall : new JButton[] {examples, runButton}) {
            tall.putClientProperty("JComponent.minimumHeight", 32); // as tall as the icon buttons; width as the text needs
        }
        return bar;
    }

    // A thin upright line between two groups of buttons, with a gap on each side.
    private static JComponent divider() {
        JComponent line = new JComponent() {
            @Override
            protected void paintComponent(java.awt.Graphics g) {
                g.setColor(Style.HAIRLINE);
                g.fillRect(getWidth() / 2, (getHeight() - 24) / 2, 1, 24);
            }
        };
        Dimension size = new Dimension(2 * Style.SPACE_2 + 1, 32);
        line.setPreferredSize(size);
        line.setMaximumSize(size);
        return line;
    }

    // Run is the one filled button: blue with "Run", or, while recording, dark with "Stop". Its
    // colours are set here because FlatLaf would turn a toolbar button grey under the mouse.
    private void styleRunButton() {
        java.awt.Color fill = running ? Style.INK : Style.ACCENT;
        java.awt.Color text = running ? Style.BACKGROUND : Style.ON_ACCENT;
        runButton.setText(running ? "Stop" : "Run");
        runButton.setIcon(Icons.of(running ? Icons.Glyph.STOP : Icons.Glyph.PLAY,
                running ? Icons.Role.ON_INK : Icons.Role.ON_ACCENT));
        runButton.setToolTipText(Shortcuts.label(running ? "Stop recording; keep the steps so far  (Esc)"
                : "Compile and record the program  (Ctrl+Enter or F5)"));
        runButton.setBackground(fill);
        runButton.setForeground(text);
        String pressed = hex(running ? Style.INK_SOFT : Style.ACCENT_PRESSED);
        runButton.putClientProperty("FlatLaf.style", "arc: " + Style.ARC + "; margin: 5,10,5,14; borderWidth: 0; focusWidth: "
                + Style.FOCUS_WIDTH + "; hoverBackground: " + pressed + "; pressedBackground: " + pressed);
    }

    // Keyboard shortcuts that work anywhere in the window. (In the editor, the arrow keys and
    // Space still type; use the Alt versions there.)
    private void bindKeys() {
        // The keys with a menu item (Ctrl+S, Ctrl+Enter, F1…) are that item's shortcut, set in
        // buildMenuBar(). These are the others: plain keys, and second keys for the same command.
        bind("F5", this::run);
        bind("control COMMA", this::showSettings);
        bind("control EQUALS", () -> changeTextSize(1));
        bind("control MINUS", () -> changeTextSize(-1));
        bind("F6", () -> moveFocus(1));
        bind("shift F6", () -> moveFocus(-1));
        bind("ESCAPE", () -> {
            if (running) {
                tracer.stop();
            } else {
                controls.pause();
            }
        });
        for (String key : new String[] {"shift control EQUALS", "control PLUS", "control ADD"}) {
            bind(key, () -> changeTextSize(1));
        }
        bind("control SUBTRACT", () -> changeTextSize(-1));
        // Only heard when the keyboard is outside the editor; inside it, these keys already work.
        bind("control V", code::pasteHere);
        bind("control A", code::selectAllHere);
        bind("RIGHT", model::next);
        bind("alt RIGHT", model::next);
        bind("LEFT", model::previous);
        bind("alt LEFT", model::previous);
        bind("HOME", model::first);
        bind("alt HOME", model::first);
        bind("END", model::last);
        bind("alt END", model::last);
        bind("SPACE", controls::togglePlay);
    }

    // The menu bar: every command in one place, with its shortcut beside it. On a Mac it's the
    // menu bar at the top of the screen, and Settings and Quit are in the app's own menu. On
    // Windows and Linux it's in the window, with underlined letters (Alt+F opens File).
    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();
        JMenu file = menu("File", KeyEvent.VK_F);
        file.add(item("New", "control N", this::newProgram));
        file.add(item("Open…", "control O", this::open));
        JMenu examples = menu("Open example", KeyEvent.VK_E);
        for (String name : EXAMPLES) {
            examples.add(item(name, null, () -> loadExample(name)));
        }
        file.add(examples);
        file.addSeparator();
        file.add(item("Save", "control S", this::save));
        file.add(item("Save As…", "shift control S", this::saveAs));
        if (!Shortcuts.isMac()) {
            file.addSeparator();
            file.add(item(Shortcuts.label("Settings…  (Ctrl+,)"), null, this::showSettings));
            file.addSeparator();
            file.add(item("Quit", "control Q", () -> {
                if (okToLoseCode()) {
                    quit();
                }
            }));
        }
        JMenu edit = menu("Edit", KeyEvent.VK_E);
        edit.add(item("Undo", "control Z", code::undo));
        edit.add(item("Redo", "control Y", code::redo));
        edit.addSeparator();
        edit.add(item("Find…", "control F", this::find));
        edit.add(item("Find next", "F3", this::findNext));
        JMenu run = menu("Run", KeyEvent.VK_R);
        run.add(item("Run or stop", "control ENTER", this::run));
        run.add(item("Play or pause  (Space)", null, controls::togglePlay));
        run.addSeparator();
        run.add(item("Step forward  (→)", null, model::next));
        run.add(item("Step back  (←)", null, model::previous));
        run.add(item("Skip call", "alt DOWN", model::stepOver));
        run.add(item("Finish call", "alt UP", model::stepOut));
        run.add(item("First step  (Home)", null, model::first));
        run.add(item("Last step  (End)", null, model::last));
        JMenu view = menu("View", KeyEvent.VK_V);
        // Swing would show these keys as "Ctrl+Equals" and "Ctrl+Minus", so they're in the text instead.
        view.add(item(Shortcuts.label("Larger text  (Ctrl +)"), null, () -> changeTextSize(1)));
        view.add(item(Shortcuts.label("Smaller text  (Ctrl −)"), null, () -> changeTextSize(-1)));
        view.add(item("Normal size", "control 0", this::resetView));
        view.addSeparator();
        view.add(item("Switch light or dark", "control D", this::toggleTheme));
        JMenu help = menu("Help", KeyEvent.VK_H);
        help.add(item("Keyboard shortcuts", "F1", this::showHelp));
        help.add(item("Check for updates…", null, this::checkForUpdatesNow));
        help.addSeparator();
        if (AppLog.folder() != null) {
            help.add(item("Open log folder", null, this::openLogFolder));
        }
        if (!Shortcuts.isMac()) { // on a Mac, About is in the app's own menu (useAppMenu)
            help.add(item("About Raphael", null, this::showAbout));
        }
        for (JMenu menu : new JMenu[] {file, edit, run, view, help}) {
            bar.add(menu);
        }
        return bar;
    }

    private static JMenu menu(String name, int letter) {
        JMenu menu = new JMenu(name);
        if (!Shortcuts.isMac()) {
            menu.setMnemonic(letter);
        }
        return menu;
    }

    // A menu item with its shortcut (written the Windows way, as in bind(); null for none).
    private static JMenuItem item(String name, String key, Runnable action) {
        JMenuItem item = new JMenuItem(name);
        if (key != null) {
            item.setAccelerator(Shortcuts.key(key));
        }
        item.addActionListener(e -> action.run());
        return item;
    }

    // At most once a day, if allowed in settings: ask GitHub in the background whether a newer
    // Raphael exists, and if so, say it in the status bar. Silent when there's nothing to say.
    private void checkForUpdatesQuietly() {
        String current = UpdateCheck.currentVersion();
        showUpdateNotice(current); // a newer version found earlier stays announced until it's installed
        long last = App.SETTINGS.getLong("lastUpdateCheck", 0);
        if (current == null || !App.SETTINGS.getBoolean("checkUpdates", true)
                || System.currentTimeMillis() - last < java.util.concurrent.TimeUnit.DAYS.toMillis(1)) {
            return;
        }
        App.SETTINGS.putLong("lastUpdateCheck", System.currentTimeMillis());
        Thread asking = new Thread(() -> {
            try {
                UpdateCheck.Release release = UpdateCheck.newer(current);
                rememberUpdate(release);
                SwingUtilities.invokeLater(() -> showUpdateNotice(current));
            } catch (IOException e) {
                LOG.log(Level.FINE, "Couldn't check for updates", e); // offline: nothing to report
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "update check");
        asking.setDaemon(true);
        asking.start();
    }

    // Help → Check for updates…: ask now, and say what was found.
    private void checkForUpdatesNow() {
        String current = UpdateCheck.currentVersion();
        if (current == null) {
            Dialogs.tell(this, "This Raphael was built from the source code",
                    "It has no version number to compare. The newest release is on " + UpdateCheck.RELEASES_PAGE);
            return;
        }
        new SwingWorker<UpdateCheck.Release, Void>() {
            @Override
            protected UpdateCheck.Release doInBackground() throws Exception {
                return UpdateCheck.newer(current);
            }

            @Override
            protected void done() {
                try {
                    UpdateCheck.Release release = get();
                    rememberUpdate(release);
                    showUpdateNotice(current);
                    if (release == null) {
                        Dialogs.tell(MainWindow.this, "Raphael is up to date", "You have the newest version, " + current + ".");
                    } else if ("Download".equals(Dialogs.question(MainWindow.this, "Raphael " + release.version() + " is available")
                            .body("You have " + current + ". The new version's installers are on its release page.")
                            .buttons("Download", "Later").ask())) {
                        openReleasePage(release.page());
                    }
                } catch (ExecutionException e) {
                    LOG.log(Level.INFO, "Couldn't check for updates", e.getCause());
                    Dialogs.tell(MainWindow.this, "Couldn't reach GitHub", "Check your internet connection, or look at "
                            + UpdateCheck.RELEASES_PAGE);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }.execute();
    }

    // The newest release found (or none), remembered so the notice survives a restart.
    private static void rememberUpdate(UpdateCheck.Release release) {
        if (release == null) {
            App.SETTINGS.remove("newerVersion");
            App.SETTINGS.remove("newerPage");
        } else {
            App.SETTINGS.put("newerVersion", release.version());
            App.SETTINGS.put("newerPage", release.page());
        }
    }

    // Shows the notice if a newer version than this one is known; hides it once it's installed.
    private void showUpdateNotice(String current) {
        String newer = App.SETTINGS.get("newerVersion", null);
        boolean show = current != null && newer != null && UpdateCheck.isNewer(newer, current);
        if (show) {
            updateNotice.setText("Raphael " + newer + " is available ↗");
            updateNotice.setToolTipText("Open the download page");
            updateNotice.getAccessibleContext().setAccessibleName("Raphael " + newer + " is available. Opens the download page.");
        }
        updateNotice.setForeground(Style.ACCENT);
        updateNotice.setVisible(show);
    }

    private void openReleasePage(String page) {
        try {
            Desktop.getDesktop().browse(java.net.URI.create(page));
        } catch (IOException | UnsupportedOperationException | IllegalArgumentException e) {
            Dialogs.tell(this, "The page couldn't be opened", "It's at " + page);
        }
    }

    // About Raphael: the version, what it is, the licence, and the details a bug report needs.
    private void showAbout() {
        String version = UpdateCheck.currentVersion();
        String details = "Raphael " + versionText() + ", Java " + System.getProperty("java.version") + ", "
                + System.getProperty("os.name") + " " + System.getProperty("os.version") + " " + System.getProperty("os.arch");
        Dialogs.question(this, version != null ? "Raphael " + version : "Raphael").title("About Raphael")
                .body("See your Java program's data structures change while it runs."
                        + (version == null ? "\nBuilt from the source code." : "")
                        + "\n\nFree and open source, under the MIT License.")
                .helper("Copy details", button -> {
                    java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                            .setContents(new java.awt.datatransfer.StringSelection(details), null);
                    button.setText("Copied");
                    Timer back = new Timer(1500, tick -> button.setText("Copy details"));
                    back.setRepeats(false);
                    back.start();
                })
                .helper("Website", button -> openReleasePage("https://github.com/electricwhitener/raphael"))
                .buttons("OK", null)
                .ask();
    }

    private static String versionText() {
        String version = UpdateCheck.currentVersion();
        return version != null ? version : "(built from the source)";
    }

    private void openLogFolder() {
        try {
            Desktop.getDesktop().open(AppLog.folder().toFile());
        } catch (IOException | UnsupportedOperationException | IllegalArgumentException e) {
            Dialogs.tell(this, "The log folder couldn't be opened", "It's here: " + AppLog.folder());
        }
    }

    // F6 moves the keyboard to the next part of the window (toolbar, code, picture), and
    // Shift+F6 back. In the code, Tab indents, so this is the way out of it.
    private void moveFocus(int direction) {
        JComponent[] regions = {(JComponent) toolBar.getComponentAtIndex(0), code.getEditor(), visual};
        java.awt.Component owner = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        int now = -1;
        for (int i = 0; i < regions.length; i++) {
            if (owner != null && (owner == regions[i] || SwingUtilities.isDescendingFrom(owner, regions[i]))) {
                now = i;
            }
        }
        int next = Math.floorMod(now + direction, regions.length);
        regions[next].requestFocusInWindow();
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
        styleRunButton();
        visual.setRecording(true);
        busy.setVisible(true);
        showPictureOrGuide();
        progress = new Timer(200, e -> {
            if (tracer.stepsSoFar() > 0) { // 0 once recording has finished and drawing has begun
                visual.setRecordedSteps(tracer.stepsSoFar());
            }
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
                recordedSteps = trace.size();
                publish("drawing " + trace.size() + " steps…");
                return new RunResult(program, trace, new SceneBuilder(source).build(trace));
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
                busy.setVisible(false);
                styleRunButton();
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
            }
        }.execute();
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
    private void fitBottomRow() {
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
                String hint = CompileHints.hint(error.message());
                if (hint != null) {
                    text.append("  hint: ").append(hint).append("\n");
                }
            }
            console.showMessage(text.toString());
            CompileResult.CompileError first = errors.get(0);
            String where = (first.line() > 0 ? "line " + first.line() + ": " : "") + first.message();
            String more = errors.size() > 1 ? " (+" + (errors.size() - 1) + " more in the console)" : "";
            String hint = CompileHints.hint(first.message());
            visual.setMessage("didn't compile:", where + more + (hint == null ? "" : "\n\n" + hint));
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
        return okToLoseCode();
    }

    private void showSettings() {
        String saved = App.SETTINGS.get("dark", null); // no choice saved: follow the computer
        SettingsDialog.Theme theme = saved == null ? SettingsDialog.Theme.SYSTEM
                : Boolean.parseBoolean(saved) ? SettingsDialog.Theme.DARK : SettingsDialog.Theme.LIGHT;
        SettingsDialog.Values chosen = SettingsDialog.ask(this,
                new SettingsDialog.Values(limits, Style.CODE_FONT.getSize(), theme, App.SETTINGS.getBoolean("checkUpdates", true)));
        if (chosen == null) {
            return;
        }
        limits = chosen.limits();
        App.SETTINGS.putInt("maxSteps", limits.maxSteps());
        App.SETTINGS.putInt("timeoutSeconds", limits.timeoutSeconds());
        App.SETTINGS.putInt("memoryMb", limits.memoryMb());
        tracer.setLimits(limits);
        App.SETTINGS.putBoolean("checkUpdates", chosen.checkUpdates());
        boolean wantDark = chosen.theme() == SettingsDialog.Theme.SYSTEM ? SystemTheme.isDark()
                : chosen.theme() == SettingsDialog.Theme.DARK;
        if (wantDark != Style.isDark()) {
            toggleTheme(); // also saves the choice
        }
        if (chosen.theme() == SettingsDialog.Theme.SYSTEM) {
            App.SETTINGS.remove("dark"); // from now on, follow the computer
        } else {
            App.SETTINGS.putBoolean("dark", wantDark);
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
        String when;
        try {
            when = " from " + whenText(draft.savedAt(), java.time.LocalDateTime.now());
        } catch (IOException e) {
            when = "";
        }
        long lines = saved.lines().count();
        String answer = Dialogs.question(this, "Restore your unsaved code?")
                .body("Raphael didn't close normally last time. It kept a copy of your code" + when
                        + " (" + lines + (lines == 1 ? " line" : " lines") + ").")
                .buttons("Restore", null, "Discard it")
                .ask();
        if ("Restore".equals(answer)) {
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

    // Before the code in the editor is replaced or Raphael quits: if there are unsaved changes,
    // offer to save them. True if it's fine to go on.
    private boolean okToLoseCode() {
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
        if (file != null) {
            openFile(file);
        }
    }

    // A file dropped onto the window.
    private void openDropped(File file) {
        if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".java")) {
            Dialogs.tell(this, "Raphael opens .java files", file.getName() + " isn't one. Drop a file whose name ends in .java.");
            return;
        }
        if (okToReplaceCode()) {
            openFile(file);
        }
    }

    private void openFile(File file) {
        try {
            code.setCode(SafeFiles.readCode(file.toPath()));
            top.setDividerLocation(dividerForCode());
            currentFile = file;
            setDocument(file.getName());
            say("opened " + file.getName() + Shortcuts.label(" · press run (ctrl+enter)"), Style.INK_SOFT);
        } catch (IOException e) {
            LOG.log(Level.INFO, "Couldn't open " + file, e);
            Dialogs.tell(this, "Couldn't open " + file.getName(), "Because " + SafeFiles.describe(e) + ".");
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
            Dialogs.tell(this, "Couldn't save " + file.getName(), "Because " + SafeFiles.describe(e)
                    + ". Your code is still here; try saving it somewhere else.");
            currentFile = null; // so the next save asks where
            return false;
        }
    }

    // The computer's own Open or Save dialog: Explorer's on Windows, Finder's on macOS, GTK's on
    // Linux, and Windows' own inside WSL (see WindowsFileDialog). Swing's JFileChooser looks and
    // works like none of them. Null if cancelled.
    private File chooseFile(boolean forSaving) {
        if (WindowsFileDialog.available()) { // inside WSL: Windows' own dialog, as a browser would show
            try {
                File chosen = WindowsFileDialog.choose(this, forSaving, new File(lastFolder()),
                        currentFile != null ? currentFile.getName() : suggestedName(code.getCode()));
                if (chosen != null && chosen.getParent() != null) {
                    App.SETTINGS.put("lastFolder", chosen.getParent());
                }
                return chosen;
            } catch (IOException e) {
                LOG.log(Level.INFO, "Windows' file dialog didn't open; using Java's instead", e);
            }
        }
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
            if (file.exists() && !"Replace".equals(Dialogs.question(this, "Replace " + file.getName() + "?")
                    .body("A file with that name is already in this folder. Replacing it overwrites what's in it.")
                    .buttons("Cancel", null, "Replace").ask())) {
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

    // "today at 14:32", "yesterday at 09:05" or "3 Nov at 18:40", for the restore question.
    static String whenText(java.time.Instant saved, java.time.LocalDateTime now) {
        java.time.LocalDateTime then = java.time.LocalDateTime.ofInstant(saved, java.time.ZoneId.systemDefault());
        String time = then.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        if (then.toLocalDate().equals(now.toLocalDate())) {
            return "today at " + time;
        }
        if (then.toLocalDate().equals(now.toLocalDate().minusDays(1))) {
            return "yesterday at " + time;
        }
        return then.format(java.time.format.DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)) + " at " + time;
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
