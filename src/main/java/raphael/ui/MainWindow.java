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
import javax.swing.JTable;
import javax.swing.JViewport;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.JToolBar;
import javax.swing.SwingWorker;
import javax.swing.text.JTextComponent;
import raphael.App;
import raphael.io.AppFolders;
import raphael.io.AppLog;
import raphael.io.DraftStore;
import raphael.io.ExampleCatalog;
import raphael.io.RunFile;
import raphael.io.RunFileException;
import raphael.io.SafeFiles;
import raphael.io.UpdateCheck;
import raphael.render.Style;
import raphael.trace.RunLimits;

// Raphael's main window: the menu bar, the toolbar, the status bar, and the welcome page or the
// program that's open (a Workbench, which holds the code, its run and its panels). Every command
// goes to the program in front, through active(); the window itself never holds a program's state.
// OOP: inheritance (extends JFrame); composition (the window is made of parts that each do one
// job); an inner class implements Workbench.Host, the interface a Workbench talks back through.
public class MainWindow extends JFrame implements MomentMode.Watcher {

    private static final String HELP = """
            run and stop
              Ctrl+Enter or F5   run; while recording, stop
              Esc                pause, or stop recording

            moving through the steps
              Space              play / pause
              Right, Left        step forward, back
              Home, End          first, last step
              Alt+Down, Alt+Up   skip call, finish call
              Alt+PgDn, Alt+PgUp  next, previous key moment
              click a line number   jump to when that line runs
              (In the editor, hold Alt with the arrow keys.)

            the editor
              Ctrl+N, O, S       new (in the last language), open, save
              Ctrl+Shift+S       save as
              Ctrl+W             close the tab
              Ctrl+Shift+T       reopen a closed tab
              Ctrl+Tab           next tab (Shift: the one before)
              Ctrl+Z, Ctrl+Y     undo, redo
              Ctrl+F, Ctrl+H     find, replace
              F3, Shift+F3       next match, previous match
              Ctrl+G             go to line
              Ctrl+P             quick open: files and examples
              Ctrl+Shift+P       command palette: every command
              Ctrl+B             show or hide the examples
              Shift+Alt+Down     duplicate line
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
    // One tab per open program, plus the Welcome tab. Each program is a Workbench.
    private final javax.swing.JTabbedPane tabs = new javax.swing.JTabbedPane();
    // The program in the tab in front, or null while the Welcome tab is in front.
    private Workbench current;
    // Closed tabs, newest first, for Ctrl+Shift+T.
    private final java.util.Deque<Closed> closedTabs = new java.util.ArrayDeque<>();
    private int untitled; // how many "Untitled-N" programs this window has made
    private String welcomeSaid = "start a new program, open a file, or pick an example"; // the status on the Welcome tab
    private final Workbench.Host host = new Host();
    // The step controls in the toolbar belong to the open program; this slot holds them, and an
    // idle set (switched off) on the welcome page.
    private final JPanel controlsSlot = new JPanel(new BorderLayout());
    private final ControlsBar idleControls = new ControlsBar(new TraceModel());
    private final JButton runButton = new JButton("Run"); // the stop button while recording
    private final JLabel status = new JLabel(" ");
    private final JButton position = new JButton(); // "Ln 12, Col 5"; a click opens Go to Line
    private final JButton languageChip = new JButton(); // "Java": the program's language; a click changes it
    private final JPanel positionDivider = new JPanel(); // a thin line between the legend and Ln, Col
    private final Legend legend = new Legend();
    // "Raphael 1.1.0 is available ↗": stays in the status bar, and opens the download page.
    private final JButton updateNotice = new JButton();
    private final JPanel statusBar = new JPanel(new BorderLayout());
    private JToolBar toolBar;
    private javax.swing.JToggleButton examplesButton; // pressed while the side bar is wanted
    private JMenuItem examplesItem;                     // View → Examples
    private final java.util.Map<MomentMode, javax.swing.JRadioButtonMenuItem> momentChoices = new java.util.EnumMap<>(MomentMode.class);

    javax.swing.JRadioButtonMenuItem momentChoice(MomentMode mode) { // for tests
        return momentChoices.get(mode);
    }

    // The key-moment choice changed (here or in any tab): the Run menu shows it, and the status says it.
    @Override
    public void momentModeChanged(MomentMode mode) {
        momentChoices.get(mode).setSelected(true);
        if (isActive()) {
            say("key moments: " + mode.description(), Style.INK_SOFT);
        }
    }
    // A thin moving bar under the toolbar while a program is being recorded.
    private final javax.swing.JProgressBar busy = new javax.swing.JProgressBar();
    private final JButton themeButton = ControlsBar.button(Icons.Glyph.THEME, "Dark mode", "Dark mode  (Ctrl+D)");
    private final ExamplesPanel examplesPanel = new ExamplesPanel(new ExamplesPanel.Actions() {
        @Override
        public void preview(ExampleCatalog.Example example) {
            previewExample(example);
        }

        @Override
        public void open(ExampleCatalog.Example example) {
            keepExample(example);
        }
    });
    private javax.swing.JSplitPane sideSplit; // the examples side bar | the tabs
    private final JMenu recentMenu = new JMenu("Open recent");
    private JMenuItem saveRecordingItem;
    private JButton saveButton;
    // The commands that need a program open; they're off on the welcome page, keys included.
    private final List<JMenuItem> needsProgram = new java.util.ArrayList<>();
    // "saved recording · 163 steps" in the status bar, while a recording read from a file is on show.
    private final JLabel recordingChip = EmptyState.keyChip("");
    private JMenuItem exportStepsItem;
    private WelcomePage welcome;

    // Autosave: every few seconds, unsaved code is copied to a draft file (see DraftStore). The
    // writing happens on a background thread, so a slow disk never pauses the window.
    private static final Logger LOG = Logger.getLogger(MainWindow.class.getName());
    private static final int AUTOSAVE_MS = 5000;
    // One draft file per program with unsaved changes, in a drafts folder.
    private final java.nio.file.Path draftFolder = draftFolder();
    private final java.util.Map<Workbench, DraftStore> drafts = new java.util.HashMap<>();
    private final java.util.Map<Workbench, String> drafted = new java.util.HashMap<>(); // what each draft holds now
    private static final java.util.concurrent.atomic.AtomicInteger DRAFTS = new java.util.concurrent.atomic.AtomicInteger(); // numbers drafts across windows
    private final ExecutorService draftWriter = Executors.newSingleThreadExecutor(job -> {
        Thread thread = new Thread(job, "autosave");
        thread.setDaemon(true); // never keeps Raphael running
        return thread;
    });
    private final Timer autosave = new Timer(AUTOSAVE_MS, e -> autosave());

    public MainWindow() {
        super("Raphael");
        setIconImages(Logo.windowIcons()); // the title bar and the taskbar

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (okToLoseAll()) {
                    Windows.closed(MainWindow.this); // the last window closed ends Raphael
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

        // Drop a code file anywhere on the window to open it.
        setTransferHandler(new FileDrop(null, this::openDropped));
        welcome = new WelcomePage(new WelcomePage.Actions(this::newProgram, this::open, this::pasteProgram,
                this::loadExample, this::openFile, language -> {
                    App.SETTINGS.put("lastNewLanguage", language.id());
                    examplesPanel.showLanguage(language);
                    if (!App.SETTINGS.getBoolean("examplesOpenedOnce", false)) {
                        App.SETTINGS.putBoolean("examplesOpenedOnce", true); // the first answer opens the side bar (with the first program), once
                        showExamplesPanel(true);
                    }
                    say("New programs and the examples are in " + language.name() + " now", Style.INK_SOFT);
                }));
        welcome.setTransferHandler(new FileDrop(null, this::openDropped));
        welcome.showRecent(recentFiles());
        buildTabs();
        sideSplit = new javax.swing.JSplitPane(javax.swing.JSplitPane.HORIZONTAL_SPLIT, examplesPanel, tabs);
        sideSplit.setBorder(BorderFactory.createEmptyBorder());
        sideSplit.setDividerSize(5);
        sideSplit.setContinuousLayout(true);
        sideSplit.setDividerLocation(App.SETTINGS.getInt("examplesWidth", 270));
        sideSplit.addPropertyChangeListener(javax.swing.JSplitPane.DIVIDER_LOCATION_PROPERTY, e -> {
            if (examplesPanel.isVisible() && sideSplit.getDividerLocation() > 120) {
                App.SETTINGS.putInt("examplesWidth", sideSplit.getDividerLocation());
            }
        });
        // Hidden until a program is in front (placeSideBar): the window opens on the Welcome tab.
        examplesPanel.setVisible(false);
        examplesPanel.setMinimumSize(new Dimension(180, 0));
        sideSplit.setInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, null); // the arrow keys step the program
        add(sideSplit, BorderLayout.CENTER);
        placeSideBar();

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
        recordingChip.setToolTipText("These steps were recorded earlier. Press Run to record this code again.");
        recordingChip.setVisible(false);
        position.putClientProperty("JButton.buttonType", "borderless");
        position.setFont(Style.CODE_FONT.deriveFont(12f)); // monospace, so the numbers don't jitter as the cursor moves
        position.setRequestFocusEnabled(false);
        position.setToolTipText(Shortcuts.label("Go to line  (Ctrl+G)"));
        position.addActionListener(e -> goToLine());
        position.getAccessibleContext().setAccessibleName("Line and column. Go to line.");
        positionDivider.setPreferredSize(new Dimension(1, 16));
        statusRight.add(recordingChip);
        statusRight.add(updateNotice);
        statusRight.add(legend);
        languageChip.putClientProperty("JButton.buttonType", "borderless");
        languageChip.setFont(Style.UI_FONT.deriveFont(12f));
        languageChip.setRequestFocusEnabled(false);
        languageChip.setToolTipText("The program's language: click to change it");
        languageChip.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        languageChip.setMargin(new java.awt.Insets(Style.SPACE_1 / 2, Style.SPACE_2, Style.SPACE_1 / 2, Style.SPACE_2));
        languageChip.addActionListener(e -> chooseLanguage(languageChip, true));
        statusRight.add(positionDivider);
        statusRight.add(languageChip);
        statusRight.add(position); // last, always in the same place
        statusBar.add(statusRight, BorderLayout.EAST);
        add(statusBar, BorderLayout.SOUTH);
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                showLegendIfRoom();
            }
        });

        bindKeys();
        switchTabsWithCtrlTab();
        // The usable part of the screen (without the taskbar), so the window always fits.
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setSize(Math.min(1300, screen.width), Math.min(840, screen.height));
        setMinimumSize(new Dimension(Math.min(960, screen.width), Math.min(600, screen.height)));
        setLocationRelativeTo(null);
        applyTheme();
        applyTextSize();
        showWelcome();
        autosave.start();
        addWindowFocusListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowGainedFocus(WindowEvent e) {
                Windows.focused(MainWindow.this);
            }
        });
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                if (current == null) {
                    welcome.focusExamples();
                } else {
                    current.focusEditor(); // ready to type or paste, not on the first toolbar button
                }
                checkForUpdatesQuietly();
            }
        });
    }

    // The open program's picture panel (null on the welcome page), for the tests.
    JComponent getPicture() {
        return current == null ? null : current.getVisual();
    }

    // The program in front, or null on the welcome page.
    Workbench active() {
        return current;
    }

    // The tabs' titles, in order, for the tests.
    List<String> tabTitles() {
        List<String> titles = new java.util.ArrayList<>();
        for (int i = 0; i < tabs.getTabCount(); i++) {
            titles.add(tabs.getTitleAt(i));
        }
        return titles;
    }

    // Runs an action on the open program's recorded run; on the welcome page, does nothing.
    private void onModel(java.util.function.Consumer<TraceModel> action) {
        if (current != null) {
            action.accept(current.getModel());
        }
    }

    private void onCode(java.util.function.Consumer<CodePanel> action) {
        if (current != null) {
            action.accept(current.getCode());
        }
    }

    // Switches between light and dark, and remembers the choice for next time. Every window follows.
    private void toggleTheme() {
        App.setupLook(!Style.isDark());
        App.SETTINGS.putBoolean("dark", Style.isDark());
        FlatLaf.updateUI(); // tells every Swing component, in every window, to pick up the new look
        Windows.forEach(MainWindow::themeChanged);
        if (!Windows.all().contains(this)) {
            themeChanged(); // a window the registry doesn't know (the tests make one directly)
        }
    }

    // The theme changed: the colours this window sets itself, and its programs' marks.
    private void themeChanged() {
        boolean wasError = status.getForeground().equals(Style.ERROR);
        SwingUtilities.updateComponentTreeUI(idleControls); // out of the window while a program is open
        for (Workbench program : programs()) {
            program.themeChanged();
        }
        applyTheme();
        status.setForeground(wasError ? Style.ERROR : Style.INK);
        if (current != null) {
            host.stateChanged(current);
        }
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
        positionDivider.setBackground(Style.HAIRLINE);
        position.setForeground(Style.INK_SOFT);
        languageChip.setForeground(Style.INK_SOFT);
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
        Windows.forEach(MainWindow::applyTextSize); // every window, this one included
        if (!Windows.all().contains(this)) {
            applyTextSize();
        }
        say("text size " + Style.CODE_FONT.getSize() + Shortcuts.label(" · ctrl+0 for normal"), Style.INK_SOFT);
    }

    private void resetView() {
        changeTextSize(Style.DEFAULT_CODE_SIZE - Style.CODE_FONT.getSize());
        if (current != null) {
            current.getVisual().resetZoom();
        }
    }

    private void applyTextSize() {
        refont(getContentPane());
        for (Workbench program : programs()) {
            program.textSizeChanged();
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

    // Edit → Go to Line… (Ctrl+G, or a click on Ln, Col): asks for a line number and goes there.
    private void goToLine() {
        if (current == null) {
            return;
        }
        CodePanel code = current.getCode();
        javax.swing.JTextField field = new javax.swing.JTextField(String.valueOf(code.caretLine()), 8);
        field.selectAll();
        String answer = Dialogs.question(this, "Go to line (1 to " + code.lineCount() + ")").title("Go to Line").extra(field)
                .buttons("Go", "Cancel").ask();
        if ("Go".equals(answer)) {
            try {
                code.goToLine(Integer.parseInt(field.getText().trim()));
            } catch (NumberFormatException e) {
                say("\"" + field.getText().trim() + "\" isn't a line number", Style.INK_SOFT);
            }
        }
    }

    // Quick Open (Ctrl+P): the open folder's files, the recent files and every example, found by
    // typing part of a name.
    private void quickOpen() {
        List<CommandPalette.Entry> entries = new java.util.ArrayList<>();
        if (current != null && current.getFolder() != null) {
            for (String path : current.getCode().getPaths()) {
                Workbench program = current;
                entries.add(new CommandPalette.Entry(path.substring(path.lastIndexOf('/') + 1), path,
                        () -> program.getCode().showFile(path)));
            }
        }
        for (File file : recentFiles()) {
            entries.add(new CommandPalette.Entry(file.getName(), "recent · " + file.getParent(), () -> openFile(file)));
        }
        for (ExampleCatalog.Group group : ExampleCatalog.groups()) {
            for (ExampleCatalog.Example example : group.examples()) {
                entries.add(new CommandPalette.Entry(example.title(), "example · " + group.name(), () -> loadExample(example)));
            }
        }
        CommandPalette.showEntries(this, "Type part of a file or example name", entries);
        focusWorkArea();
    }

    // After a palette or dialog closes, the keyboard goes back to the program (not to the first
    // button in the window, which is where Swing would put it).
    private void focusWorkArea() {
        java.awt.Component owner = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (current != null && (owner == null || !SwingUtilities.isDescendingFrom(owner, current))) {
            current.focusEditor();
        } else if (current == null) {
            welcome.focusExamples();
        }
    }

    // Ln, Col on the right of the status bar, for the program in front.
    private void showPosition() {
        position.setVisible(current != null);
        languageChip.setVisible(current != null);
        if (current != null) {
            languageChip.setText(current.getLanguage().name());
        }
        if (current != null) {
            position.setText("Ln " + current.getCode().caretLine() + ", Col " + current.getCode().caretColumn());
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
        controlsSlot.setOpaque(false);
        idleControls.setSpeedEnabled(false);
        bar.setBorder(BorderFactory.createCompoundBorder(Section.hairline(0, 0, 1, 0),
                BorderFactory.createEmptyBorder(Style.SPACE_2, Style.SPACE_3, Style.SPACE_2, Style.SPACE_3)));
        // A toolbar normally moves the keyboard focus with the arrow keys; here they step the program.
        bar.setInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT, null);

        JButton newProgram = ControlsBar.button(Icons.Glyph.NEW, "New program", "New program, or paste one in  (Ctrl+N)");
        newProgram.addActionListener(e -> chooseLanguage(newProgram, false)); // a menu of the languages
        JButton open = ControlsBar.button(Icons.Glyph.OPEN, "Open", "Open a program or a recording  (Ctrl+O)");
        open.addActionListener(e -> open());
        JButton save = ControlsBar.button(Icons.Glyph.SAVE, "Save", "Save  (Ctrl+S)");
        saveButton = save;
        save.addActionListener(e -> save());
        javax.swing.JToggleButton examples = new javax.swing.JToggleButton("Examples");
        examples.setSelected(examplesPanel.isVisible());
        examplesButton = examples;
        examples.setToolTipText(Shortcuts.label("Show or hide the examples  (Ctrl+B)"));
        examples.setRequestFocusEnabled(false);
        examples.putClientProperty("JButton.buttonType", "toolBarButton");
        examples.addActionListener(e -> toggleExamples());
        themeButton.addActionListener(e -> toggleTheme());
        JButton settings = ControlsBar.button(Icons.Glyph.SETTINGS, "Settings", "Settings: steps, time, memory, text size, theme  (Ctrl+,)");
        settings.addActionListener(e -> showSettings());
        JButton help = ControlsBar.button(Icons.Glyph.HELP, "Keyboard shortcuts", "Keyboard shortcuts  (F1)");
        help.addActionListener(e -> showHelp());
        runButton.setRequestFocusEnabled(false);
        runButton.setFont(Style.sans(true, Style.UI_SIZE));
        runButton.setIconTextGap(Style.SPACE_2);
        runButton.addActionListener(e -> run());

        bar.add(homeButton());
        bar.add(divider());
        bar.add(newProgram);
        bar.add(open);
        bar.add(save);
        bar.add(examples);
        bar.add(divider());
        bar.add(runButton);
        bar.add(divider());
        bar.add(controlsSlot);
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
        for (javax.swing.AbstractButton tall : new javax.swing.AbstractButton[] {examples, runButton}) {
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
        boolean running = current != null && current.isRunning();
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
            if (current != null) {
                current.stopOrPause();
            }
        });
        for (String key : new String[] {"shift control EQUALS", "control PLUS", "control ADD"}) {
            bind(key, () -> changeTextSize(1));
        }
        bind("control SUBTRACT", () -> changeTextSize(-1));
        // Only heard when the keyboard is outside the editor; inside it, these keys already work.
        bind("control V", () -> {
            if (current == null) {
                pasteProgram();
            } else {
                current.getCode().pasteHere();
            }
        });
        bind("control A", () -> onCode(CodePanel::selectAllHere));
        bind("RIGHT", () -> onModel(TraceModel::next));
        bind("alt RIGHT", () -> onModel(TraceModel::next));
        bind("LEFT", () -> onModel(TraceModel::previous));
        bind("alt LEFT", () -> onModel(TraceModel::previous));
        bind("HOME", () -> onModel(TraceModel::first));
        bind("alt HOME", () -> onModel(TraceModel::first));
        bind("END", () -> onModel(TraceModel::last));
        bind("alt END", () -> onModel(TraceModel::last));
        bind("SPACE", this::togglePlay);
        bind("control PAGE_DOWN", () -> nextTab(1));
        bind("control PAGE_UP", () -> nextTab(-1));
    }

    // The menu bar: every command in one place, with its shortcut beside it. On a Mac it's the
    // menu bar at the top of the screen, and Settings and Quit are in the app's own menu. On
    // Windows and Linux it's in the window, with underlined letters (Alt+F opens File).
    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();
        JMenu file = menu("File", KeyEvent.VK_F);
        file.add(item("New", "control N", this::newProgram)); // in the last language chosen
        JMenu newIn = new JMenu("New Program In");
        for (raphael.lang.Language language : raphael.lang.Languages.all()) {
            newIn.add(item(language.name(), null, () -> newProgram(language)));
        }
        file.add(newIn);
        file.add(item("New Window", "shift control N", Windows::openNew));
        file.add(item("Open…", "control O", this::open));
        file.add(item("Open Folder…", "shift control O", this::openFolder));
        JMenu examples = menu("Open example", KeyEvent.VK_E);
        addExamples(examples);
        file.add(examples);
        file.add(recentMenu);
        file.add(item("Close tab", "control W", this::closeProgram));
        file.add(item("Reopen closed tab", "shift control T", this::reopenClosedTab));
        file.addSeparator();
        file.add(item("Save", "control S", this::save));
        file.add(item("Save As…", "shift control S", this::saveAs));
        file.addSeparator();
        saveRecordingItem = item("Save recording…", null, () -> {
            if (current != null) {
                current.saveRecording();
            }
        });
        saveRecordingItem.setToolTipText("Save the program and every recorded step in a .raphael file, to play again without running");
        file.add(saveRecordingItem);
        exportStepsItem = item("Export steps as CSV…", null, () -> {
            if (current != null) {
                current.exportSteps();
            }
        });
        exportStepsItem.setToolTipText("Each step as a row of a table: its line, its variables, what it printed");
        file.add(exportStepsItem);
        file.addMenuListener(new javax.swing.event.MenuListener() {
            @Override
            public void menuSelected(javax.swing.event.MenuEvent e) {
                saveRecordingItem.setEnabled(current != null && current.hasRecording());
                exportStepsItem.setEnabled(current != null && current.getModel().getTrace() != null);
                fillRecent();
            }

            @Override
            public void menuDeselected(javax.swing.event.MenuEvent e) {
            }

            @Override
            public void menuCanceled(javax.swing.event.MenuEvent e) {
            }
        });
        if (!Shortcuts.isMac()) {
            file.addSeparator();
            file.add(item(Shortcuts.label("Settings…  (Ctrl+,)"), null, this::showSettings));
            file.addSeparator();
            file.add(item("Quit", "control Q", Windows::quitAll));
        }
        JMenu edit = menu("Edit", KeyEvent.VK_E);
        edit.add(item("Undo", "control Z", () -> onCode(CodePanel::undo)));
        edit.add(item("Redo", "control Y", () -> onCode(CodePanel::redo)));
        edit.addSeparator();
        edit.add(item("Cut", "control X", () -> onCode(CodePanel::cut)));
        edit.add(item("Copy", "control C", () -> onCode(CodePanel::copy)));
        edit.add(item("Paste", "control V", () -> onCode(CodePanel::pasteHere)));
        edit.add(item("Select All", "control A", () -> onCode(CodePanel::selectAllHere)));
        edit.add(item("Duplicate Line", "shift alt DOWN", () -> onCode(CodePanel::duplicateLine)));
        edit.addSeparator();
        edit.add(item("Find…", "control F", () -> onCode(code -> code.openFind(false))));
        edit.add(item("Replace…", "control H", () -> onCode(code -> code.openFind(true))));
        edit.add(item("Find Next", "F3", () -> onCode(code -> code.findNext(false))));
        edit.add(item("Find Previous", "shift F3", () -> onCode(code -> code.findNext(true))));
        edit.addSeparator();
        edit.add(item("Go to Line…", "control G", this::goToLine));
        JMenu run = menu("Run", KeyEvent.VK_R);
        run.add(item("Run or stop", "control ENTER", this::run));
        run.add(item("Play or pause  (Space)", null, this::togglePlay));
        run.addSeparator();
        run.add(item("Step forward  (→)", null, () -> onModel(TraceModel::next)));
        run.add(item("Step back  (←)", null, () -> onModel(TraceModel::previous)));
        run.add(item("Skip call", "alt DOWN", () -> onModel(TraceModel::stepOver)));
        run.add(item("Finish call", "alt UP", () -> onModel(TraceModel::stepOut)));
        run.add(item("First step  (Home)", null, () -> onModel(TraceModel::first)));
        run.add(item("Last step  (End)", null, () -> onModel(TraceModel::last)));
        run.addSeparator();
        run.add(item("Next key moment", "alt PAGE_DOWN", () -> onModel(model -> model.nextMoment(1))));
        run.add(item("Previous key moment", "alt PAGE_UP", () -> onModel(model -> model.nextMoment(-1))));
        run.addSeparator(); // key moments: off, shown, or paused at (the same choice as the playback bar's)
        javax.swing.ButtonGroup modes = new javax.swing.ButtonGroup();
        for (MomentMode mode : MomentMode.values()) {
            javax.swing.JRadioButtonMenuItem choice = new javax.swing.JRadioButtonMenuItem(mode.label(), mode == MomentMode.current());
            choice.setToolTipText(mode.description());
            choice.addActionListener(e -> MomentMode.set(mode));
            modes.add(choice);
            momentChoices.put(mode, choice);
            run.add(choice);
        }
        MomentMode.watch(this);
        JMenu view = menu("View", KeyEvent.VK_V);
        view.add(item("Command Palette…", "shift control P", () -> {
            CommandPalette.showCommands(this, getJMenuBar());
            focusWorkArea();
        }));
        view.add(item("Quick Open…", "control P", this::quickOpen));
        examplesItem = item("Examples", "control B", this::toggleExamples);
        view.add(examplesItem);
        view.addSeparator();
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
        if (!Shortcuts.isMac()) { // on a Mac, About is in the app's own menu (Windows.installAppMenu)
            help.add(item("About Raphael", null, this::showAbout));
        }
        for (JMenu menu : new JMenu[] {file, edit, run, view, help}) {
            bar.add(menu);
        }
        for (int i = 0; i < file.getItemCount(); i++) {
            JMenuItem item = file.getItem(i);
            if (item != null && (item.getText().equals("Save") || item.getText().equals("Save As…"))) {
                needsProgram.add(item);
            }
        }
        for (JMenu menu : new JMenu[] {edit, run}) {
            for (int i = 0; i < menu.getItemCount(); i++) {
                if (menu.getItem(i) != null) { // null: a separator
                    needsProgram.add(menu.getItem(i));
                }
            }
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
    void showAbout() {
        String version = UpdateCheck.currentVersion();
        String details = "Raphael " + versionText() + ", Java " + System.getProperty("java.version") + ", "
                + System.getProperty("os.name") + " " + System.getProperty("os.version") + " " + System.getProperty("os.arch");
        Dialogs.question(this, version != null ? "Raphael " + version : "Raphael").title("About Raphael")
                .body("See your program's data structures change while it runs."
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
        JComponent first = (JComponent) toolBar.getComponentAtIndex(0);
        JComponent[] regions = current == null ? new JComponent[] {first, welcome}
                : new JComponent[] {first, current.getCode().getEditor(), current.getVisual()};
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

    // Run, or stop a recording: the open program does it.
    private void run() {
        if (current != null) {
            current.run();
        }
    }

    private void togglePlay() {
        if (current != null) {
            current.getControls().togglePlay();
        }
    }

    // A message in the status bar. While a program is open it's that program's message, so it
    // comes back when the program is in front again.
    private void say(String message, Color color) {
        if (current != null) {
            current.say(message, color);
        } else {
            welcomeSaid = message;
            showStatus(message, color);
        }
    }

    private void showStatus(String message, Color color) {
        status.setText(message);
        status.setForeground(color);
        status.getAccessibleContext().setAccessibleDescription(message);
    }

    // The key to the picture, unless there's an error to show or the window is too narrow for both.
    private void showLegendIfRoom() {
        boolean room = getWidth() >= 1100;
        legend.setVisible(current != null && current.wantsLegend() && room);
        positionDivider.setVisible(legend.isVisible() && current != null);
        String chip = current == null ? null : current.recordingChip();
        if (chip != null) {
            recordingChip.setText(chip);
        }
        recordingChip.setVisible(chip != null && room);
    }

    void showSettings() {
        String saved = App.SETTINGS.get("dark", null); // no choice saved: follow the computer
        SettingsDialog.Theme theme = saved == null ? SettingsDialog.Theme.SYSTEM
                : Boolean.parseBoolean(saved) ? SettingsDialog.Theme.DARK : SettingsDialog.Theme.LIGHT;
        SettingsDialog.Values chosen = SettingsDialog.ask(this,
                new SettingsDialog.Values(savedLimits(), Style.CODE_FONT.getSize(), theme, App.SETTINGS.getBoolean("checkUpdates", true)));
        if (chosen == null) {
            return;
        }
        RunLimits limits = chosen.limits();
        App.SETTINGS.putInt("maxSteps", limits.maxSteps());
        App.SETTINGS.putInt("timeoutSeconds", limits.timeoutSeconds());
        App.SETTINGS.putInt("memoryMb", limits.memoryMb());
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

    // The folder for drafts, or null if there's nowhere to write (then autosave is off).
    private static java.nio.file.Path draftFolder() {
        try {
            java.nio.file.Path folder = AppFolders.data().resolve("drafts");
            java.nio.file.Files.createDirectories(folder);
            return folder;
        } catch (IOException e) {
            LOG.log(Level.WARNING, "No folder for autosaved drafts; autosave is off", e);
            return null;
        }
    }

    // Copies each program's unsaved code to its own draft, and removes a draft once its program
    // has nothing unsaved. The writing happens on the autosave thread.
    private void autosave() {
        if (draftFolder == null) {
            return;
        }
        for (Workbench program : programs()) {
            String text = program.isDirty() ? program.getCode().getCode() : null;
            if (Objects.equals(text, drafted.get(program))) {
                continue;
            }
            drafted.put(program, text);
            DraftStore draft = drafts.computeIfAbsent(program, p -> new DraftStore(draftFolder.resolve(
                    "draft-" + ProcessHandle.current().pid() + "-" + DRAFTS.incrementAndGet() + program.getLanguage().endings().get(0))));
            writeDraft(draft, text);
        }
    }

    private void writeDraft(DraftStore draft, String text) {
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

    // A program's tab closed: its draft goes too.
    private void dropDraft(Workbench program) {
        DraftStore draft = drafts.remove(program);
        drafted.remove(program);
        if (draft != null) {
            writeDraft(draft, null);
        }
    }

    // Called once the window is showing. Drafts left from last time mean Raphael didn't quit
    // normally (a normal quit removes them), so offer the code back, each program in its own tab.
    // The single draft.java of Raphael 1.0 is offered too.
    public void offerDraft() {
        List<DraftStore> found = new java.util.ArrayList<>();
        List<String> codes = new java.util.ArrayList<>();
        List<raphael.lang.Language> languages = new java.util.ArrayList<>(); // each draft's, from its ending
        java.time.Instant newest = null;
        List<java.nio.file.Path> files = new java.util.ArrayList<>();
        try {
            files.add(AppFolders.data().resolve("draft.java"));
            if (draftFolder != null) {
                try (java.util.stream.Stream<java.nio.file.Path> list = java.nio.file.Files.list(draftFolder)) {
                    list.filter(f -> raphael.lang.Languages.forFile(f.getFileName().toString()) != null).sorted().forEach(files::add);
                }
            }
            for (java.nio.file.Path file : files) {
                DraftStore draft = new DraftStore(file);
                String code = draft.load();
                if (code == null || code.isBlank()) {
                    continue;
                }
                found.add(draft);
                codes.add(code);
                languages.add(raphael.lang.Languages.forFile(file.getFileName().toString()));
                java.time.Instant at = draft.savedAt();
                newest = newest == null || at.isAfter(newest) ? at : newest;
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Couldn't read the autosaved drafts", e);
        }
        if (found.isEmpty()) {
            return;
        }
        String when = newest == null ? "" : " from " + whenText(newest, java.time.LocalDateTime.now());
        String what;
        if (codes.size() == 1) {
            long lines = codes.get(0).lines().count();
            what = "a copy of your code" + when + " (" + lines + (lines == 1 ? " line" : " lines") + ")";
        } else {
            what = "copies of " + codes.size() + " programs" + when + ", each in its own tab";
        }
        String answer = Dialogs.question(this, "Restore your unsaved code?")
                .body("Raphael didn't close normally last time. It kept " + what + ".")
                .buttons("Restore", null, "Discard it")
                .ask();
        if ("Restore".equals(answer)) {
            for (int i = 0; i < codes.size(); i++) {
                String code = codes.get(i);
                Workbench program = newTab();
                program.setLanguage(languages.get(i));
                program.showCode(code, null, "restored code");
                program.getCode().markUnsaved();
                host.stateChanged(program);
            }
            say(Shortcuts.label("restored your unsaved code · ctrl+s saves it"), Style.INK_SOFT);
        }
        for (DraftStore draft : found) { // restored programs are drafted again as they are now
            writeDraft(draft, null);
        }
    }

    // The window is closing normally: its drafts go, so the next start doesn't offer them.
    void forgetDrafts() {
        autosave.stop();
        for (Workbench program : programs()) {
            dropDraft(program);
        }
        draftWriter.shutdown();
        try {
            draftWriter.awaitTermination(2, TimeUnit.SECONDS); // a save in progress finishes first
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // Before the window closes: every program with unsaved changes is brought to the front and
    // asked about in turn. True if it's fine to go on.
    boolean okToLoseAll() {
        for (Workbench program : programs()) {
            if (program.isDirty()) {
                tabs.setSelectedComponent(program);
                if (!program.okToLoseCode()) {
                    return false;
                }
            }
        }
        return true;
    }

    // The open programs, in tab order.
    private List<Workbench> programs() {
        List<Workbench> programs = new java.util.ArrayList<>();
        for (int i = 0; i < tabs.getTabCount(); i++) {
            if (tabs.getComponentAt(i) instanceof Workbench) {
                programs.add((Workbench) tabs.getComponentAt(i));
            }
        }
        return programs;
    }

    void newProgram() {
        newProgram(lastNewLanguage());
    }

    // A new program in this language, from its template, with the cursor where the code goes.
    void newProgram(raphael.lang.Language language) {
        App.SETTINGS.put("lastNewLanguage", language.id());
        Workbench program = newTab();
        program.setLanguage(language);
        String template = language.template();
        program.showCode(template, null, "Untitled-" + (++untitled));
        int[] at = typingPlace(template);
        program.getCode().startTypingAt(at[0], at[1]);
        say(Shortcuts.label("type your code, or ctrl+a then ctrl+v to paste a whole program · then press run (ctrl+enter)"), Style.INK_SOFT);
    }

    // A menu of the four languages under a button: for New (a new program in that language, the
    // last one chosen first, with Ctrl+N beside it), or for the status bar's chip (the program in
    // front becomes that language; its code stays as it is, coloured and run as the new language).
    private void chooseLanguage(JComponent under, boolean change) {
        JPopupMenu menu = new JPopupMenu();
        List<raphael.lang.Language> order = new java.util.ArrayList<>(raphael.lang.Languages.all());
        raphael.lang.Language first = change && current != null ? current.getLanguage() : lastNewLanguage();
        order.remove(first);
        order.add(0, first);
        for (raphael.lang.Language language : order) {
            String keys = !change && language == first ? Shortcuts.label("   Ctrl+N") : "";
            JMenuItem item = new JMenuItem((change && language == first ? "✓ " : "") + language.name() + keys);
            item.addActionListener(e -> {
                if (!change) {
                    newProgram(language);
                } else if (current != null) {
                    changeLanguage(current, language);
                }
            });
            menu.add(item);
        }
        if (change) {
            JMenuItem note = new JMenuItem("Changing it doesn't edit your code");
            note.setEnabled(false);
            menu.insert(note, 0);
            menu.insert(new JPopupMenu.Separator(), 1);
        }
        menu.show(under, 0, change ? -menu.getPreferredSize().height : under.getHeight());
    }

    // The program in front becomes another language. A template nobody has typed into is swapped
    // for the new language's own.
    private void changeLanguage(Workbench program, raphael.lang.Language language) {
        String code = program.getCode().getCode();
        boolean untouchedTemplate = code.equals(program.getLanguage().template());
        program.setLanguage(language);
        if (untouchedTemplate) {
            program.showCode(language.template(), null, "Untitled-" + untitled);
            int[] at = typingPlace(language.template());
            program.getCode().startTypingAt(at[0], at[1]);
        }
        App.SETTINGS.put("lastNewLanguage", language.id());
        showPosition();
        say("this program is " + language.name() + " now"
                + (language.cantRun() != null ? " · " + language.cantRun() : Shortcuts.label(" · press run (ctrl+enter)")), Style.INK_SOFT);
    }

    // ---- the examples side bar ----

    // Shows the side bar if it's hidden, else hides it (Ctrl+B, the toolbar's Examples, View → Examples).
    // Not on the Welcome tab, which has its own example browser.
    void toggleExamples() {
        if (current != null) {
            showExamplesPanel(!examplesPanel.isVisible());
        }
    }

    boolean examplesShowing() {
        return examplesPanel.isVisible();
    }

    // Whether the user wants the side bar (remembered); it shows while a program is in front.
    void showExamplesPanel(boolean show) {
        App.SETTINGS.putBoolean("examplesShown", show);
        placeSideBar();
        if (show && current != null) {
            examplesPanel.focusSearch();
        } else if (current != null) {
            current.focusEditor();
        }
    }

    // The side bar is open when it's wanted and a program is in front: the Welcome tab has the
    // full example browser already. Leaving the Welcome tab brings it back as it was.
    private void placeSideBar() {
        if (sideSplit == null) {
            return; // the tabs are being built; it's placed once the split exists
        }
        boolean wanted = App.SETTINGS.getBoolean("examplesShown", true) && raphael.lang.Languages.chosen();
        boolean show = wanted && current != null;
        if (examplesPanel.isVisible() != show) {
            examplesPanel.setVisible(show);
            if (show) {
                sideSplit.setDividerLocation(App.SETTINGS.getInt("examplesWidth", 270));
            }
            sideSplit.revalidate();
        }
        if (examplesButton != null) {
            examplesButton.setSelected(show); // not pressed (and off) on the Welcome tab
            examplesButton.setEnabled(current != null);
        }
        if (examplesItem != null) {
            examplesItem.setEnabled(current != null);
        }
    }

    // One click on an example: it opens in the preview tab (italic title), which the next click
    // reuses. A tab with the user's own code is never replaced.
    void previewExample(ExampleCatalog.Example example) {
        Workbench preview = null;
        for (Workbench program : programs()) {
            if (program.isPreview() && !program.isDirty()) {
                preview = program;
            }
        }
        if (preview != null && example.equals(preview.getExample())) {
            tabs.setSelectedComponent(preview);
            return;
        }
        if (preview != null) {
            tabs.setSelectedComponent(preview);
        }
        Workbench program = showExample(example, preview != null);
        program.setPreview(true);
    }

    // A double click (or Enter): the example opens to keep, in the preview tab if it's there.
    void keepExample(ExampleCatalog.Example example) {
        if (current != null && current.isPreview() && example.equals(current.getExample())) {
            current.setPreview(false);
            return;
        }
        showExample(example, false).setPreview(false);
    }

    // The language New uses without asking (Ctrl+N): the last one chosen, else the one being learned.
    static raphael.lang.Language lastNewLanguage() {
        String id = App.SETTINGS.get("lastNewLanguage", null);
        return id == null ? raphael.lang.Languages.preferred() : raphael.lang.Languages.byId(id);
    }

    // Where typing starts in a template: the first line that's only indentation (line from 1).
    static int[] typingPlace(String template) {
        String[] lines = template.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (!lines[i].isEmpty() && lines[i].isBlank()) {
                return new int[] {i + 1, lines[i].length()};
            }
        }
        return new int[] {1, 0};
    }

    void loadExample(String name) {
        loadExample(ExampleCatalog.find(name));
    }

    private void loadExample(ExampleCatalog.Example example) {
        showExample(example, false);
    }

    // Puts an example in a tab: the one in front if it's untouched (or the preview being reused), else a new one.
    private Workbench showExample(ExampleCatalog.Example example, boolean reuseFront) {
        Workbench program = reuseFront ? current : newTab(true);
        String source = ExampleCatalog.source(example);
        program.setLanguage(example.language());
        program.showCode(source, null, example.name() + example.language().endings().get(0) + " (example)");
        program.setExample(example);
        program.setInput(ExampleCatalog.input(source)); // an example that reads input comes with some
        program.setTabTitle(example.title()); // "Bubble sort"; the tooltip says it's an example
        host.stateChanged(program);
        examplesPanel.select(example);
        say(example.title() + ": " + example.blurb().replaceAll("\\.$", "")
                + Shortcuts.label(" · press run (ctrl+enter)"), Style.INK_SOFT);
        return program;
    }

    // The examples, grouped: a heading for each section (Java, Algorithms…), then a submenu for
    // each group. Hovering an example says what it shows.
    private void addExamples(JComponent menu) {
        String section = null;
        for (ExampleCatalog.Group group : ExampleCatalog.groups()) {
            if (!group.section().equals(section)) {
                if (section != null) {
                    menu.add(new JPopupMenu.Separator());
                }
                section = group.section();
                JLabel heading = new JLabel(section.toUpperCase(Locale.ROOT)) {
                    @Override
                    public Color getForeground() {
                        return Style.MUTED;
                    }
                };
                heading.setFont(Style.sans(true, 10.5f));
                heading.setBorder(BorderFactory.createEmptyBorder(Style.SPACE_1, Style.SPACE_3, Style.SPACE_1, Style.SPACE_3));
                menu.add(heading);
            }
            JMenu submenu = new JMenu(group.name());
            for (ExampleCatalog.Example example : group.examples()) {
                JMenuItem item = item(example.title(), null, () -> loadExample(example));
                item.setToolTipText(example.blurb());
                submenu.add(item);
            }
            menu.add(submenu);
        }
    }

    // A program pasted from the clipboard, in place of the welcome page.
    private void pasteProgram() {
        String text;
        try {
            text = (String) java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().getData(java.awt.datatransfer.DataFlavor.stringFlavor);
        } catch (java.awt.datatransfer.UnsupportedFlavorException | IOException | IllegalStateException e) {
            text = null; // nothing usable on the clipboard (an image, say, or it's busy)
        }
        if (text == null || text.isBlank()) {
            say(Shortcuts.label("the clipboard has no text · copy a program first, then press ctrl+v"), Style.INK_SOFT);
            return;
        }
        Workbench program = newTab();
        raphael.lang.Language language = raphael.lang.Languages.recognise(text); // what the pasted code looks like
        program.setLanguage(language != null ? language : lastNewLanguage());
        program.showCode(text, null, "pasted program");
        program.getCode().markUnsaved();
        host.stateChanged(program);
        say(Shortcuts.label("pasted your " + program.getLanguage().name() + " program"
                + (language == null ? " (the language is in the status bar: click it to change)" : "")
                + " · press run (ctrl+enter)"), Style.INK_SOFT);
    }

    // The Welcome tab, in front: opened again if it was closed.
    void showWelcome() {
        if (tabs.indexOfComponent(welcome) < 0) {
            tabs.insertTab("Welcome", null, welcome, "Start a program, open a file, or pick an example", 0);
        }
        tabs.setSelectedComponent(welcome);
        tabChanged(); // also when it was already in front
        welcome.focusExamples();
    }

    // A new tab for the program about to be opened, in front.
    private Workbench newTab() {
        return newTab(false);
    }

    // replaceUntouched: an untouched program in front (a new program nobody typed in, an example
    // nobody ran) is replaced instead, so trying examples one after another doesn't pile up tabs.
    private Workbench newTab(boolean replaceUntouched) {
        Workbench program = new Workbench(host);
        recolor(program);
        refont(program);
        program.textSizeChanged();
        int at = tabs.getSelectedIndex() + 1;
        // (an example the user chose to keep isn't replaced, even if it hasn't been run yet)
        if (replaceUntouched && current != null && current.isUntouched() && (current.getExample() == null || current.isPreview())) {
            at = tabs.indexOfComponent(current);
            removeTab(current);
        }
        tabs.insertTab("", null, program, null, Math.min(at, tabs.getTabCount()));
        tabs.setSelectedComponent(program);
        tabChanged();
        getContentPane().validate(); // laid out now, so the code's divider can be placed
        return program;
    }

    // The tab in front changed: the toolbar, menus, title and status bar follow its program.
    private void tabChanged() {
        Component selected = tabs.getSelectedComponent();
        Workbench now = selected instanceof Workbench ? (Workbench) selected : null;
        if (current != null && current != now) {
            current.getControls().pause(); // a program behind another doesn't keep playing
        }
        current = now;
        useControls(current == null ? idleControls : current.getControls());
        runButton.setEnabled(current != null);
        showWelcomeControls(current == null);
        if (current == null) {
            styleRunButton();
            busy.setVisible(false);
            updateTitle();
            showPosition();
            showStatus(welcomeSaid, Style.INK_SOFT);
        } else {
            host.stateChanged(current);
            examplesPanel.select(current.getExample()); // the side bar shows which example this is
            java.awt.Component owner = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            if (owner == null || !SwingUtilities.isDescendingFrom(owner, current)) {
                // The keyboard goes to the code, not to the first button (the Home logo). Asked now,
                // not later, so a request that comes after (the find bar's) still wins.
                current.focusEditor();
            }
        }
    }

    // Puts a program's step controls (or the idle ones) into the toolbar.
    private void useControls(ControlsBar bar) {
        if (controlsSlot.getComponentCount() == 1 && controlsSlot.getComponent(0) == bar) {
            return;
        }
        controlsSlot.removeAll();
        controlsSlot.add(bar, BorderLayout.CENTER);
        controlsSlot.revalidate();
        controlsSlot.repaint();
    }

    // On the Welcome tab, everything that needs a program is off, and so is the picture's key.
    private void showWelcomeControls(boolean welcome) {
        for (JMenuItem item : needsProgram) {
            item.setEnabled(!welcome);
        }
        saveButton.setEnabled(!welcome);
        placeSideBar();
        showLegendIfRoom();
    }

    // File → Close tab (Ctrl+W): closes the tab in front, after asking about unsaved code.
    void closeProgram() {
        closeTab(tabs.getSelectedIndex());
    }

    // Closes one tab. A program with unsaved changes is brought to the front and asked about first;
    // one still recording stops. The last tab closed brings the Welcome tab back. True if it closed.
    private boolean closeTab(int index) {
        if (index < 0 || index >= tabs.getTabCount()) {
            return false;
        }
        Component tab = tabs.getComponentAt(index);
        if (tab instanceof Workbench) {
            Workbench program = (Workbench) tab;
            if (program.isDirty()) {
                tabs.setSelectedIndex(index);
                if (!program.okToLoseCode()) {
                    return false;
                }
            }
            program.stop();
            closedTabs.push(new Closed(program.getCurrentFile(), program.getCode().getCode(), program.getDocumentName(),
                    program.getTabTitle(), program.isDirty()));
            while (closedTabs.size() > 20) {
                closedTabs.removeLast();
            }
            removeTab(program);
        } else {
            tabs.removeTabAt(index);
        }
        if (tabs.getTabCount() == 0) {
            showWelcome();
        }
        tabChanged();
        return true;
    }

    // Takes a program's tab away, with its draft.
    private void removeTab(Workbench program) {
        program.stop();
        tabs.remove(program);
        dropDraft(program);
        if (program == current) {
            current = null;
        }
    }

    // Closes every tab but this one; stops at the first the user keeps (Cancel).
    private void closeOthers(Component keep) {
        for (int i = tabs.getTabCount() - 1; i >= 0; i--) {
            if (tabs.getComponentAt(i) != keep && !closeTab(i)) {
                return;
            }
        }
    }

    // Ctrl+Shift+T: the last tab closed comes back, from its file if it has one.
    void reopenClosedTab() {
        Closed closed = closedTabs.poll();
        if (closed == null) {
            say("no closed tab to reopen", Style.INK_SOFT);
            return;
        }
        if (closed.file() != null && closed.file().isFile()) {
            openFile(closed.file());
            return;
        }
        Workbench program = newTab();
        program.showCode(closed.code(), null, closed.name());
        if (!closed.title().equals(closed.name())) {
            program.setTabTitle(closed.title()); // an example comes back with its title
        }
        if (closed.unsaved()) {
            program.getCode().markUnsaved();
            host.stateChanged(program);
        }
        say("reopened " + closed.name(), Style.INK_SOFT);
    }

    // Ctrl+Tab, Ctrl+PgDn: the next tab; with Shift, or Ctrl+PgUp, the one before. They go round.
    private void nextTab(int direction) {
        int count = tabs.getTabCount();
        if (count > 1) {
            tabs.setSelectedIndex(Math.floorMod(tabs.getSelectedIndex() + direction, count));
            if (current != null) {
                current.getVisual().requestFocusInWindow();
            }
        }
    }

    // Ctrl+Tab normally moves the keyboard focus out of the editor before any shortcut hears it, so
    // it's caught first, while this window is the one in front.
    private void switchTabsWithCtrlTab() {
        java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(event -> {
            if (event.getID() != KeyEvent.KEY_PRESSED || event.getKeyCode() != KeyEvent.VK_TAB
                    || !event.isControlDown() || !isActive()) {
                return false;
            }
            nextTab(event.isShiftDown() ? -1 : 1);
            return true; // used up: the focus stays where it was
        });
    }

    // The tab strip: a close button on each tab, a menu on right-click, and middle-click closes.
    private void buildTabs() {
        tabs.setTabLayoutPolicy(javax.swing.JTabbedPane.SCROLL_TAB_LAYOUT);
        tabs.putClientProperty("JTabbedPane.tabClosable", true);
        tabs.putClientProperty("JTabbedPane.tabCloseToolTipText", Shortcuts.label("Close  (Ctrl+W)"));
        tabs.putClientProperty("JTabbedPane.tabCloseCallback",
                (java.util.function.IntConsumer) index -> closeTab(index));
        tabs.putClientProperty("JTabbedPane.showTabSeparators", true);
        tabs.putClientProperty("JTabbedPane.tabHeight", Style.TAB_H);
        tabs.putClientProperty("JTabbedPane.maximumTabWidth", 240);
        tabs.putClientProperty("JTabbedPane.tabsPopupPolicy", "asNeeded");
        tabs.putClientProperty("JTabbedPane.scrollButtonsPolicy", "asNeededSingle");
        tabs.setFocusable(false); // the keyboard stays with the code or the picture
        tabs.addChangeListener(e -> tabChanged());
        tabs.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                int index = tabs.indexAtLocation(e.getX(), e.getY());
                if (index < 0) {
                    return;
                }
                if (SwingUtilities.isMiddleMouseButton(e)) {
                    closeTab(index);
                } else if (e.isPopupTrigger()) {
                    tabMenu(index).show(tabs, e.getX(), e.getY());
                }
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                int index = tabs.indexAtLocation(e.getX(), e.getY());
                if (index >= 0 && e.isPopupTrigger()) { // Windows opens the menu on release
                    tabMenu(index).show(tabs, e.getX(), e.getY());
                }
            }
        });
        tabs.insertTab("Welcome", null, welcome, "Start a program, open a file, or pick an example", 0);
    }

    // Right-click on a tab: close it, the others or all, and, for a saved file, copy its path or show its folder.
    private JPopupMenu tabMenu(int index) {
        Component tab = tabs.getComponentAt(index);
        File file = tab instanceof Workbench ? ((Workbench) tab).getCurrentFile() : null;
        JPopupMenu menu = new JPopupMenu();
        menu.add(item("Close", null, () -> closeTab(tabs.indexOfComponent(tab))));
        menu.add(item("Close Others", null, () -> closeOthers(tab)));
        menu.add(item("Close All", null, () -> closeOthers(null)));
        if (file != null) {
            menu.addSeparator();
            menu.add(item("Copy Path", null, () -> java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new java.awt.datatransfer.StringSelection(file.getAbsolutePath()), null)));
            menu.add(item(Shortcuts.isMac() ? "Reveal in Finder" : "Show in Folder", null, () -> showInFolder(file)));
        }
        return menu;
    }

    // Opens the folder that holds a file, with the file selected where the computer can do that.
    private void showInFolder(File file) {
        try {
            Desktop desktop = Desktop.getDesktop();
            if (desktop.isSupported(Desktop.Action.BROWSE_FILE_DIR)) {
                desktop.browseFileDirectory(file);
            } else {
                desktop.open(file.getAbsoluteFile().getParentFile());
            }
        } catch (IOException | UnsupportedOperationException | IllegalArgumentException e) {
            Dialogs.tell(this, "The folder couldn't be opened", "It's here: " + file.getAbsoluteFile().getParent());
        }
    }

    // The logo at the start of the toolbar: it's also the way home, to the Welcome tab.
    private JButton homeButton() {
        java.awt.Image icon = new java.awt.image.BaseMultiResolutionImage(Logo.draw(22), Logo.draw(44), Logo.draw(66));
        JButton home = new JButton(new javax.swing.ImageIcon(icon));
        home.putClientProperty("JButton.buttonType", "toolBarButton");
        home.setToolTipText("Home: the Welcome tab");
        home.getAccessibleContext().setAccessibleName("Home");
        home.setRequestFocusEnabled(false);
        home.addActionListener(e -> showWelcome());
        Dimension size = new Dimension(32, 32);
        home.setPreferredSize(size);
        home.setMaximumSize(size);
        return home;
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
        File file = chooseFile(false, FileKind.CODE_OR_RUN, null);
        if (file != null) {
            openFile(file);
        }
    }

    // A file the system asked Raphael to open: double-clicked, or named on the command line.
    public void openFromSystem(File file) {
        if (!file.isFile()) {
            Dialogs.tell(this, "Couldn't open " + file.getName(), "There's no file at " + file.getPath() + ".");
            return;
        }
        openDropped(file);
    }

    // A file dropped onto the window.
    private void openDropped(File file) {
        if (file.isDirectory()) {
            openFolder(file);
            return;
        }
        if (!FileKind.CODE_OR_RUN.accepts(file.getName())) {
            Dialogs.tell(this, "Raphael opens code and .raphael files", file.getName()
                    + " isn't one. Drop a program (.java, .c, .cpp or .py) or a saved recording (.raphael).");
            return;
        }
        openFile(file);
    }

    private void openFile(File file) {
        if (file.isDirectory()) {
            openFolder(file);
            return;
        }
        for (int i = 0; i < tabs.getTabCount(); i++) { // already open: its tab comes to the front
            Component tab = tabs.getComponentAt(i);
            if (tab instanceof Workbench && file.getAbsoluteFile().equals(((Workbench) tab).getCurrentFile() == null ? null
                    : ((Workbench) tab).getCurrentFile().getAbsoluteFile())) {
                tabs.setSelectedIndex(i);
                return;
            }
        }
        if (FileKind.RUN.accepts(file.getName())) {
            openRecording(file);
            return;
        }
        try {
            String text = SafeFiles.readCode(file.toPath());
            Workbench program = newTab(true);
            raphael.lang.Language language = raphael.lang.Languages.forFile(file.getName());
            program.setLanguage(language != null ? language : raphael.lang.Languages.JAVA);
            program.showCode(text, file, file.getName());
            remember(file);
            say("opened " + file.getName() + Shortcuts.label(" · press run (ctrl+enter)"), Style.INK_SOFT);
        } catch (IOException e) {
            LOG.log(Level.INFO, "Couldn't open " + file, e);
            Dialogs.tell(this, "Couldn't open " + file.getName(), "Because " + SafeFiles.describe(e) + ".");
        }
    }

    // A recording saved earlier: its code goes in the editor and its steps play, with nothing run.
    private void openRecording(File file) {
        RunFile.Saved saved;
        try {
            saved = RunFile.read(file.toPath());
        } catch (RunFileException e) {
            LOG.log(Level.INFO, "Couldn't open the recording " + file, e);
            Dialogs.tell(this, "Couldn't open " + file.getName(), e.getMessage());
            return;
        } catch (IOException e) {
            LOG.log(Level.INFO, "Couldn't open the recording " + file, e);
            Dialogs.tell(this, "Couldn't open " + file.getName(), "Because " + SafeFiles.describe(e) + ".");
            return;
        }
        newTab(true).showRecording(file, saved);
        remember(file);
    }

    // File → Open Folder: a folder of code files as one program, its files in tabs. The program's
    // language is the one most of its files are in.
    private void openFolder() {
        File folder = chooseFolder();
        if (folder != null) {
            openFolder(folder);
        }
    }

    private static final int FOLDER_MAX_FILES = 200;
    private static final java.util.Set<String> SKIPPED_FOLDERS = java.util.Set.of("target", "out", "bin", "build", "node_modules");

    private void openFolder(File folder) {
        for (Workbench program : programs()) { // already open: its tab comes to the front
            if (folder.getAbsoluteFile().equals(program.getFolder() == null ? null : program.getFolder().getAbsoluteFile())) {
                tabs.setSelectedComponent(program);
                return;
            }
        }
        List<raphael.model.SourceFile> files = new java.util.ArrayList<>();
        String first = null;
        java.nio.file.Path root = folder.toPath();
        raphael.lang.Language language;
        try (java.util.stream.Stream<java.nio.file.Path> all = java.nio.file.Files.walk(root, 8)) {
            List<java.nio.file.Path> code = all
                    .filter(path -> raphael.lang.Languages.forFile(path.getFileName().toString()) != null
                            && java.nio.file.Files.isRegularFile(path))
                    .filter(path -> inOwnFolders(root.relativize(path)))
                    .sorted().limit(FOLDER_MAX_FILES).toList();
            java.util.Map<raphael.lang.Language, Long> counts = new java.util.HashMap<>();
            for (java.nio.file.Path path : code) {
                counts.merge(raphael.lang.Languages.forFile(path.getFileName().toString()), 1L, Long::sum);
            }
            language = counts.entrySet().stream().max(java.util.Map.Entry.comparingByValue()).map(java.util.Map.Entry::getKey)
                    .orElse(raphael.lang.Languages.JAVA);
            raphael.lang.Language chosen = language;
            List<java.nio.file.Path> sources = code.stream().filter(path -> chosen.owns(path.getFileName().toString())).toList();
            for (java.nio.file.Path path : sources) {
                String text = SafeFiles.readCode(path);
                String relative = root.relativize(path).toString().replace(File.separatorChar, '/');
                files.add(new raphael.model.SourceFile(relative, text));
                boolean hasMain = text.contains("static void main") || text.matches("(?s).*\\bint\\s+main\\s*\\(.*")
                        || text.contains("__main__");
                if (hasMain && (first == null || relative.endsWith("Main.java"))) {
                    first = relative;
                }
            }
        } catch (IOException | java.io.UncheckedIOException e) {
            LOG.log(Level.INFO, "Couldn't open the folder " + folder, e);
            Dialogs.tell(this, "Couldn't open " + folder.getName(), "Because " + SafeFiles.describe(
                    e instanceof java.io.UncheckedIOException ? ((java.io.UncheckedIOException) e).getCause() : (IOException) e) + ".");
            return;
        }
        if (files.isEmpty()) {
            Dialogs.tell(this, "No code files in " + folder.getName(), "Raphael opens a folder of .java, .c, .cpp or .py files as one program. "
                    + "This folder has none (folders named target, out, bin and build are skipped).");
            return;
        }
        Workbench program = newTab(true);
        program.setLanguage(language);
        program.showFiles(files, first, folder, folder.getName());
        remember(folder);
        say("opened " + folder.getName() + ": " + files.size() + (files.size() == 1 ? " file" : " files")
                + Shortcuts.label(" · press run (ctrl+enter)"), Style.INK_SOFT);
    }

    // Whether a file is in the program's own folders, not a hidden one (.git) or a build's output.
    private static boolean inOwnFolders(java.nio.file.Path relative) {
        for (int i = 0; i < relative.getNameCount() - 1; i++) {
            String name = relative.getName(i).toString();
            if (name.startsWith(".") || SKIPPED_FOLDERS.contains(name)) {
                return false;
            }
        }
        return true;
    }

    // The computer's dialog for choosing a folder: Windows' own inside WSL, Finder's on a Mac
    // (FileDialog in its folder mode), and Java's chooser elsewhere, since GTK's and Windows' FileDialog
    // can't choose folders. Null if cancelled.
    private File chooseFolder() {
        File start = new File(lastFolder());
        File chosen = null;
        if (WindowsFileDialog.available()) {
            try {
                chosen = WindowsFileDialog.chooseFolder(this, start);
                return rememberFolder(chosen);
            } catch (IOException e) {
                LOG.log(Level.INFO, "Windows' folder dialog didn't open; using Java's instead", e);
            }
        }
        if (Shortcuts.isMac()) {
            System.setProperty("apple.awt.fileDialogForDirectories", "true");
            try {
                FileDialog dialog = new FileDialog(this, "Open Folder", FileDialog.LOAD);
                dialog.setDirectory(start.getPath());
                dialog.setVisible(true);
                chosen = dialog.getFile() == null ? null : new File(dialog.getDirectory(), dialog.getFile());
            } finally {
                System.setProperty("apple.awt.fileDialogForDirectories", "false");
            }
            return rememberFolder(chosen);
        }
        javax.swing.JFileChooser chooser = new javax.swing.JFileChooser(start);
        chooser.setDialogTitle("Open Folder");
        chooser.setFileSelectionMode(javax.swing.JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        if (chooser.showDialog(this, "Open") == javax.swing.JFileChooser.APPROVE_OPTION) {
            chosen = chooser.getSelectedFile();
        }
        return rememberFolder(chosen);
    }

    // The next dialog starts next to the folder chosen.
    private static File rememberFolder(File chosen) {
        if (chosen != null && chosen.getParent() != null) {
            App.SETTINGS.put("lastFolder", chosen.getParent());
        }
        return chosen;
    }

    // Recent files: the last few opened or saved, newest first, kept in Preferences.
    private static final int RECENT_MAX = 8;

    private List<File> recentFiles() {
        List<File> recent = new java.util.ArrayList<>();
        for (String path : App.SETTINGS.get("recentFiles", "").split("\n")) {
            if (!path.isBlank() && new File(path).exists()) { // a file moved or deleted since drops out
                recent.add(new File(path));
            }
        }
        return recent;
    }

    private void remember(File file) {
        List<File> recent = recentFiles();
        recent.removeIf(f -> f.getAbsolutePath().equals(file.getAbsolutePath()));
        recent.add(0, file.getAbsoluteFile());
        if (recent.size() > RECENT_MAX) {
            recent = recent.subList(0, RECENT_MAX);
        }
        App.SETTINGS.put("recentFiles", String.join("\n", recent.stream().map(File::getPath).toList()));
        List<File> shown = recent;
        welcome.showRecent(shown);
        Windows.forEach(window -> window.welcome.showRecent(shown));
    }

    // File → Open recent, filled each time the File menu opens.
    private void fillRecent() {
        recentMenu.removeAll();
        List<File> recent = recentFiles();
        for (File file : recent) {
            JMenuItem item = item(file.getName(), null, () -> openFile(file));
            item.setToolTipText(file.getPath());
            recentMenu.add(item);
        }
        recentMenu.setEnabled(!recent.isEmpty());
    }

    // Saves the open program to its file, or asks where if it has none yet.
    private void save() {
        if (current != null) {
            current.save();
        }
    }

    private void saveAs() {
        if (current != null) {
            current.saveAs();
        }
    }

    // The computer's own Open or Save dialog: Explorer's on Windows, Finder's on macOS, GTK's on
    // Linux, and Windows' own inside WSL (see WindowsFileDialog). Swing's JFileChooser looks and
    // works like none of them. Null if cancelled.
    // kind: the files to show; suggested: the name a save starts with.
    private File chooseFile(boolean forSaving, FileKind kind, String suggested) {
        if (WindowsFileDialog.available()) { // inside WSL: Windows' own dialog, as a browser would show
            try {
                File chosen = WindowsFileDialog.choose(this, forSaving, kind, new File(lastFolder()), suggested);
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
            dialog.setFile(suggested);
        } else {
            dialog.setFilenameFilter((folder, name) -> kind.accepts(name));
            if (Shortcuts.isWindows()) {
                dialog.setFile(kind.patterns()); // Windows ignores the filter above; this pattern filters instead
            }
        }
        dialog.setVisible(true); // waits until the user picks or cancels
        String name = dialog.getFile();
        if (name == null) {
            return null;
        }
        App.SETTINGS.put("lastFolder", dialog.getDirectory());
        if (forSaving && kind.mainEnding() != null && !kind.accepts(name)) {
            name += "." + kind.mainEnding();
            File file = new File(dialog.getDirectory(), name);
            // The dialog only asked about replacing the name that was typed, without the ending.
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
        File currentFile = current == null ? null : current.getCurrentFile();
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
        return Workbench.suggestedName(code);
    }

    // Whether code works with files (see Workbench.usesFiles).
    static boolean usesFiles(String code) {
        return Workbench.usesFiles(code);
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

    // The window's title: the open program's name, with a dot while it has unsaved changes. On a
    // Mac, the title is just the name, and the dot goes in the close button, as Mac apps do it.
    private void updateTitle() {
        String name = current == null ? "Welcome" : current.getDocumentName();
        boolean unsaved = current != null && current.isDirty();
        if (Shortcuts.isMac()) {
            setTitle(name);
            getRootPane().putClientProperty("Window.documentModified", unsaved);
            getRootPane().putClientProperty("Window.documentFile", current == null ? null : current.getCurrentFile());
        } else {
            setTitle((unsaved ? "● " : "") + name + " — Raphael");
        }
    }

    // A tab that was closed, enough to open it again.
    private record Closed(File file, String code, String name, String title, boolean unsaved) {
    }

    // How the open program talks back to the window.
    // OOP: an inner class implementing an interface; it can use the window's private methods.
    private class Host implements Workbench.Host {
        @Override
        public void stateChanged(Workbench workbench) {
            int index = tabs.indexOfComponent(workbench);
            if (index >= 0) {
                String title = (workbench.isDirty() ? "● " : "") + workbench.getTabTitle();
                // a preview tab's title is in italics, as in VS Code: the next example clicked replaces it
                tabs.setTitleAt(index, workbench.isPreview() ? "<html><i>" + title.replace("&", "&amp;").replace("<", "&lt;")
                        + "</i></html>" : title);
                tabs.setForegroundAt(index, workbench.isPreview() ? Style.MUTED : null); // lighter too, not italics alone
                File file = workbench.getCurrentFile();
                String tip = file != null ? file.getAbsolutePath()
                        : workbench.getFolder() != null ? workbench.getFolder().getAbsolutePath()
                        : workbench.getDocumentName().endsWith("(example)")
                        ? "Example: " + workbench.getDocumentName().replace(" (example)", "") + ". Save As… keeps a copy."
                        : "not saved yet";
                tabs.setToolTipTextAt(index, workbench.isRunning() ? "recording…"
                        : workbench.isPreview() ? "Preview: double-click the example, edit it or run it to keep this tab" : tip);
            }
            if (workbench != current) {
                return; // a program that isn't in front: its state shows when it is
            }
            updateTitle();
            styleRunButton();
            busy.setVisible(workbench.isRunning());
            showStatus(workbench.statusText(), workbench.statusColor());
            showLegendIfRoom();
            showPosition();
        }

        @Override
        public void caretMoved(Workbench workbench) {
            if (workbench == current) {
                showPosition();
            }
        }

        @Override
        public File chooseFile(boolean forSaving, FileKind kind, String suggested) {
            return MainWindow.this.chooseFile(forSaving, kind, suggested);
        }

        @Override
        public File chooseFolder() {
            return MainWindow.this.chooseFolder();
        }

        @Override
        public void remember(File file) {
            MainWindow.this.remember(file);
        }

        @Override
        public void openDropped(File file) {
            MainWindow.this.openDropped(file);
        }

        @Override
        public void loadExample(String name) {
            MainWindow.this.loadExample(name);
        }

        @Override
        public void showExamples() {
            showExamplesPanel(true);
        }

        @Override
        public void changeTextSize(int change) {
            MainWindow.this.changeTextSize(change);
        }

        @Override
        public RunLimits limits() {
            return savedLimits(); // from the settings, which another window may have changed
        }
    }
}
