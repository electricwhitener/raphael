package raphael.ui;

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.DocumentFilter;
import javax.swing.text.Element;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import raphael.model.Trace;
import raphael.model.TypedLine;
import raphael.render.Style;
import raphael.trace.LiveInput;

// The terminal: what the program prints, and what the user types for it.
// While a program runs, what it prints shows up as it's printed. A program that reads input is
// typed to at the end of the text, as in a real terminal: Enter sends the line, Ctrl+D ends the
// input, and the recording waits while the program waits. Played back, it shows the terminal as
// it was at that step: the output so far with each typed line where it was typed (in blue), and
// what the last line printed tinted amber. It also lists compile errors (a click goes to the line).
// OOP: inheritance (extends JPanel); implements TraceListener (Observer pattern); a nested
// DocumentFilter subclass keeps the printed text from being edited (encapsulation).
public class TerminalPanel extends JPanel implements TraceListener {

    private final JTextPane screen = new JTextPane() {
        @Override
        protected void paintComponent(java.awt.Graphics g) {
            super.paintComponent(g);
            if (printed == null && last == null && getDocument().getLength() == 0) { // before the first run
                Section.paintHint(g, this, "What your program prints appears here. If it reads input, you type it here.");
            }
        }
    };
    private final StyledDocument document = screen.getStyledDocument();
    private final JLabel heading = Section.header("terminal");
    // Under the text while a program waits for input: a pale blue strip with a blue line on top,
    // so where to type is plain. Its colours are read when it paints, so they follow dark mode.
    private final JLabel hint = new JLabel() {
        @Override
        protected void paintComponent(java.awt.Graphics g) {
            g.setColor(Style.ACCENT_TINT);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(Style.ACCENT);
            g.fillRect(0, 0, getWidth(), Zoom.px(2));
            super.paintComponent(g);
        }

        @Override
        public java.awt.Color getForeground() {
            return Style.INK;
        }
    };
    private java.util.function.Consumer<String> onLineClicked = line -> { }; // until the program sets it
    private final DefaultHighlighter.DefaultHighlightPainter newOutput =
            new DefaultHighlighter.DefaultHighlightPainter(null) {
                @Override
                public java.awt.Color getColor() {
                    return Style.CHANGE_TINT; // read when it paints, so it follows dark mode
                }
            };

    // During a run.
    private LiveInput live;              // the input being typed, or null (the program reads none)
    private Supplier<String> printed;    // what the program has printed so far, or null when no run is on
    private String shown = "";           // the printed and typed text on the screen, before the user's typing
    private String output = "";          // what the program had printed at the last refresh
    private int inputStart;              // where the user's typing begins
    private boolean writing;             // true while the terminal writes to itself (the filter lets it)
    private boolean wasWaiting;
    private TraceModel last;             // the run on show, to draw again after a theme change

    // A piece of the terminal's text: printed by the program, or typed by the user.
    private record Piece(String text, boolean typed) {
    }

    public TerminalPanel() {
        super(new BorderLayout());
        screen.setEditable(false);
        screen.setFont(Style.CODE_FONT);
        screen.setMargin(new java.awt.Insets(Style.SPACE_1, Style.SPACE_3, Style.SPACE_1, Style.SPACE_3));
        screen.getAccessibleContext().setAccessibleName("Terminal");
        ((javax.swing.text.AbstractDocument) document).setDocumentFilter(new Typing());
        // A click on a compile error ("Circle.java, line 12: …" or "line 12: …") goes to that line.
        screen.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (printed != null) {
                    return; // a run: the text is the program's
                }
                int at = screen.viewToModel2D(e.getPoint());
                Element line = document.getDefaultRootElement().getElement(document.getDefaultRootElement().getElementIndex(at));
                try {
                    onLineClicked.accept(document.getText(line.getStartOffset(), line.getEndOffset() - line.getStartOffset()));
                } catch (BadLocationException ignored) {
                    // clicked below the text
                }
            }
        });
        // Ctrl+D ends the input, only while there is input to end; otherwise the key goes on to the
        // window (where it switches the theme).
        screen.getInputMap().put(KeyStroke.getKeyStroke("control D"), "end input");
        screen.getActionMap().put("end input", new AbstractAction() {
            @Override
            public boolean isEnabled() {
                return accepting();
            }

            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                endInput();
            }
        });

        JScrollPane scroll = new JScrollPane(screen);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        hint.setVisible(false);
        Zoom.font(hint, Style.sans(false, Style.UI_SMALL));
        hint.setBorder(Zoom.gap(Style.SPACE_1 + 2, Style.SPACE_3, Style.SPACE_1, Style.SPACE_3));
        JPanel body = new JPanel(new BorderLayout());
        body.add(scroll, BorderLayout.CENTER);
        body.add(hint, BorderLayout.SOUTH);
        add(Section.wrap(heading, body));
    }

    // Inside the bottom row's tabs, the tab says what this is, so the heading goes.
    public void hideHeading() {
        heading.setVisible(false);
    }

    // Told the text of a line that was clicked (a compile error).
    public void setOnLineClicked(java.util.function.Consumer<String> onLineClicked) {
        this.onLineClicked = onLineClicked;
    }

    // Compile errors, or nothing: plain text, the first line in sight.
    public void showMessage(String message) {
        printed = null;
        live = null;
        last = null;
        setHint(null);
        write(List.of(new Piece(message, false)), null);
        screen.setCaretPosition(0);
    }

    // ---- a run ----

    // A run begins: printed says what the program has printed so far; live takes what the user
    // types, or is null for a program that reads no input.
    public void startRun(LiveInput live, Supplier<String> printed) {
        this.live = live;
        this.printed = printed;
        this.last = null;
        wasWaiting = false;
        shown = "";
        output = "";
        write(List.of(), null);
        inputStart = 0;
        screen.setEditable(live != null);
        screen.getCaret().setVisible(live != null && screen.hasFocus());
        setHint(null);
    }

    // While the run records (the window asks every fraction of a second): what's been printed and
    // typed since. True when the program has just started waiting for input.
    public boolean refresh() {
        if (printed == null) {
            return false;
        }
        String got = printed.get();
        if (got.length() >= output.length()) {
            output = got; // (once the recording ends, the tracer has nothing to say: keep the last)
        }
        List<Piece> pieces = pieces(output, live == null ? List.of() : live.typed(), Integer.MAX_VALUE);
        StringBuilder all = new StringBuilder();
        pieces.forEach(p -> all.append(p.text()));
        String now = all.toString();
        boolean atEnd = screen.getCaretPosition() >= document.getLength();
        writing = true;
        try {
            if (now.startsWith(shown)) { // the usual case: more at the end, before what the user is typing
                int skip = shown.length();
                int at = inputStart;
                for (Piece piece : pieces) {
                    String text = piece.text();
                    if (skip >= text.length()) {
                        skip -= text.length();
                        continue;
                    }
                    String rest = text.substring(skip);
                    skip = 0;
                    document.insertString(at, rest, style(piece));
                    at += rest.length();
                }
                inputStart = at;
            } else { // printed text went (it can't, but then all of it again)
                document.remove(0, inputStart);
                int at = 0;
                for (Piece piece : pieces) {
                    document.insertString(at, piece.text(), style(piece));
                    at += piece.text().length();
                }
                inputStart = at;
            }
        } catch (BadLocationException e) {
            throw new IllegalStateException(e); // offsets inside the document, worked out above
        } finally {
            writing = false;
        }
        shown = now;
        if (atEnd || accepting()) {
            screen.setCaretPosition(document.getLength());
        }
        boolean waiting = live != null && live.isWaiting();
        boolean started = waiting && !wasWaiting;
        wasWaiting = waiting;
        setHint(waiting ? "Your turn: type a line and press Enter.   Ctrl+D: no more input."
                : live != null && live.isEnded() ? "No more input (Ctrl+D). The program can still print." : null);
        return started;
    }

    // The run's recording has ended; the playback takes over the text.
    public void endRun() {
        refresh();
        printed = null;
        live = null;
        screen.setEditable(false);
        setHint(null);
    }

    // The keyboard goes to the end of the text, to type.
    public void focusInput() {
        screen.requestFocusInWindow();
        screen.setCaretPosition(document.getLength());
        screen.getCaret().setVisible(true);
    }

    // True while the user can type for the program.
    public boolean accepting() {
        return live != null && !live.isEnded() && printed != null;
    }

    private void endInput() {
        if (accepting()) {
            String typing = typing();
            if (!typing.isEmpty()) {
                live.type(typing, output.length()); // what's typed before Ctrl+D is sent first, as a terminal does
                clearTyping();
            }
            live.end();
            screen.setEditable(false);
            refresh();
        }
    }

    // What the user has typed but not sent yet.
    private String typing() {
        try {
            return document.getText(inputStart, document.getLength() - inputStart);
        } catch (BadLocationException e) {
            return "";
        }
    }

    private void clearTyping() {
        writing = true;
        try {
            document.remove(inputStart, document.getLength() - inputStart);
        } catch (BadLocationException ignored) {
            // nothing typed
        } finally {
            writing = false;
        }
    }

    // After the user's typing changed: each whole line (Enter, or pasted lines) goes to the program.
    private void sendLines() {
        if (!accepting()) {
            return;
        }
        String typing = typing();
        int end = typing.lastIndexOf('\n');
        if (end < 0) {
            return;
        }
        String keep = typing.substring(end + 1);
        clearTyping();
        for (String line : typing.substring(0, end).split("\n", -1)) {
            live.type(line, output.length());
        }
        refresh(); // the lines show where they were typed
        writing = true;
        try {
            document.insertString(document.getLength(), keep, typedStyle());
        } catch (BadLocationException ignored) {
            // the end is always there
        } finally {
            writing = false;
        }
        screen.setCaretPosition(document.getLength());
    }

    private void setHint(String text) {
        hint.setText(text == null ? "" : text);
        hint.setVisible(text != null);
    }

    // The text before inputStart is the program's: the user can only type after it.
    private final class Typing extends DocumentFilter {
        @Override
        public void insertString(FilterBypass fb, int offset, String text, AttributeSet attributes) throws BadLocationException {
            if (writing) {
                super.insertString(fb, offset, text, attributes);
            } else if (accepting() && offset >= inputStart) {
                super.insertString(fb, offset, text, typedStyle());
                if (text.indexOf('\n') >= 0) {
                    SwingUtilities.invokeLater(TerminalPanel.this::sendLines);
                }
            }
        }

        @Override
        public void remove(FilterBypass fb, int offset, int length) throws BadLocationException {
            if (writing || (accepting() && offset >= inputStart)) {
                super.remove(fb, offset, length);
            }
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attributes) throws BadLocationException {
            if (writing) {
                super.replace(fb, offset, length, text, attributes);
            } else if (accepting() && offset >= inputStart) {
                super.replace(fb, offset, length, text, typedStyle());
                if (text != null && text.indexOf('\n') >= 0) {
                    SwingUtilities.invokeLater(TerminalPanel.this::sendLines);
                }
            }
        }
    }

    // ---- playing back ----

    @Override
    public void stepChanged(TraceModel model) {
        if (printed != null) {
            return; // recording: the live text stays
        }
        Trace trace = model.getTrace();
        last = trace == null ? null : model;
        if (trace == null) {
            write(List.of(), null);
            return;
        }
        if (!model.hasSteps()) { // the program ended before any of its lines ran
            List<Piece> pieces = new ArrayList<>(pieces(trace.getOutput(), trace.getTyped(), Integer.MAX_VALUE));
            pieces.add(new Piece(endLine(trace.getOutput(), trace), false));
            write(pieces, null);
            return;
        }
        int step = model.getStep();
        String output = withoutJavaLines(trace.getOutputAt(step));
        String before = step > 0 ? withoutJavaLines(trace.getOutputAt(step - 1)) : "";
        List<Piece> pieces = new ArrayList<>(pieces(output, trace.getTyped(), step));
        if (model.isLastStep()) {
            pieces.add(new Piece(endLine(output, trace), false));
        }
        boolean grew = output.length() > before.length() && output.startsWith(before);
        write(pieces, grew ? new int[] {before.length(), output.length()} : null);
    }

    private static String endLine(String output, Trace trace) {
        return (output.isEmpty() || output.endsWith("\n") ? "" : "\n") + "— " + trace.getEndMessage();
    }

    // A theme change: the typed lines' blue is drawn again in the new theme's.
    @Override
    public void updateUI() {
        super.updateUI();
        if (last != null) {
            stepChanged(last);
        }
    }

    // The printed text with each typed line (up to this step) put where it was typed.
    private static List<Piece> pieces(String output, List<TypedLine> typed, int step) {
        List<Piece> pieces = new ArrayList<>();
        int from = 0;
        for (TypedLine line : typed) {
            if (line.step() > step) {
                break; // typed in order, so the rest came later too
            }
            int at = Math.min(Math.max(line.at(), from), output.length());
            if (at > from) {
                pieces.add(new Piece(output.substring(from, at), false));
            }
            pieces.add(new Piece(line.text(), true));
            from = at;
        }
        if (from < output.length()) {
            pieces.add(new Piece(output.substring(from), false));
        }
        return pieces;
    }

    // The whole text again; printedRange (offsets in the printed text) is tinted amber.
    private void write(List<Piece> pieces, int[] printedRange) {
        writing = true;
        screen.getHighlighter().removeAllHighlights();
        try {
            document.remove(0, document.getLength());
            int at = 0;
            int printedSoFar = 0;
            List<int[]> tints = new ArrayList<>();
            for (Piece piece : pieces) {
                document.insertString(at, piece.text(), style(piece));
                if (printedRange != null && !piece.typed()) {
                    int from = Math.max(printedRange[0], printedSoFar);
                    int to = Math.min(printedRange[1], printedSoFar + piece.text().length());
                    if (from < to) {
                        tints.add(new int[] {at + from - printedSoFar, at + to - printedSoFar});
                    }
                    printedSoFar += piece.text().length();
                }
                at += piece.text().length();
            }
            // After all the text is in: a tint added earlier would grow over text inserted at its end.
            for (int[] tint : tints) {
                screen.getHighlighter().addHighlight(tint[0], tint[1], newOutput);
            }
        } catch (BadLocationException e) {
            throw new IllegalStateException(e); // offsets inside the document, worked out above
        } finally {
            writing = false;
        }
    }

    private static AttributeSet style(Piece piece) {
        SimpleAttributeSet style = new SimpleAttributeSet();
        if (piece.typed()) {
            StyleConstants.setForeground(style, Style.ACCENT);
        }
        return style;
    }

    // What the user types: blue as soon as it's typed, as it will be once sent (the theme's blue now).
    private static AttributeSet typedStyle() {
        return style(new Piece("", true));
    }

    // A crash's stack trace, without the lines from inside Java itself, which only hide the student's.
    private static String withoutJavaLines(String output) {
        return output.replaceAll("(?m)^\\s+at (java|jdk|sun)\\S*.*\\R", "");
    }

    // ---- for tests ----

    String text() {
        try {
            return document.getText(0, document.getLength());
        } catch (BadLocationException e) {
            return "";
        }
    }

    void typeForTest(String text) {
        try {
            document.insertString(document.getLength(), text, null);
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
    }

    boolean hintShown() {
        return hint.isVisible();
    }
}
