package raphael.ui;

import com.formdev.flatlaf.ui.FlatTextAreaUI;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;
import javax.swing.text.PlainView;
import javax.swing.text.Segment;
import javax.swing.text.Utilities;
import javax.swing.text.View;
import javax.swing.undo.UndoManager;
import raphael.compile.CompileResult;
import raphael.model.Snapshot;
import raphael.render.Style;

// The code editor. While stepping, two lines are marked: the line about to run (blue, with a ▶
// in the gutter) and the line that just ran (grey) — the picture shows the state after the grey
// line. A crash makes its line red. Compile errors are marked red too; hover to see the message.
// Click a line number to jump to the next time that line runs. Editing the code clears the old
// run, because its steps no longer match the code. Ctrl+Z and Ctrl+Y undo and redo edits.
// The code is coloured as in an IDE: JavaTokens finds the pieces, ColoredView draws them.
// OOP: inheritance (extends JPanel); implements TraceListener (Observer pattern); nested classes
// for the line numbers, the line highlight and the coloured text, which overrides how Swing draws
// a line; an anonymous subclass of JTextArea.
public class CodePanel extends JPanel implements TraceListener {

    private final TraceModel model;
    private final Map<Integer, String> errors = new HashMap<>(); // line → message
    private final List<Object> errorMarks = new ArrayList<>();
    private final List<Object> stepMarks = new ArrayList<>();
    private final LineNumbers lineNumbers;
    private final JScrollPane scroll;
    private final EditorKeys keys;
    private final UndoManager undo = new UndoManager(); // the JDK's list of edits to step back through
    private boolean dirty; // true if the text changed since it was loaded or saved
    private Runnable onCodeChanged = () -> { }; // does nothing until the window sets it
    private List<JavaTokens.Token> tokens = List.of(); // the coloured pieces of the current text
    private IntConsumer onTextSizeWheel = change -> { }; // does nothing until the window sets it

    // An anonymous subclass of JTextArea that shows the error message as a tooltip.
    private final JTextArea editor = new JTextArea() {
        // Swing calls this when the look changes, and it would put back the plain text drawing.
        @Override
        public void updateUI() {
            setUI(new ColoredTextAreaUI());
        }

        @Override
        public String getToolTipText(MouseEvent event) {
            return errors.get(lineAt(event.getPoint()));
        }
    };

    public CodePanel(TraceModel model) {
        super(new BorderLayout());
        this.model = model;
        editor.setFont(Style.CODE_FONT);
        editor.setTabSize(4);
        editor.setMargin(new Insets(6, 8, 6, 8));
        editor.setBackground(Style.BACKGROUND);
        editor.setForeground(Style.INK);
        editor.setCaretColor(Style.INK);
        editor.setSelectionColor(Style.ACCENT_TINT);
        editor.setSelectedTextColor(Style.INK);
        ToolTipManager.sharedInstance().registerComponent(editor);
        lineNumbers = new LineNumbers(editor, model::jumpToLine);
        scroll = new JScrollPane(editor);
        scroll.setRowHeaderView(lineNumbers);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(scroll);

        editor.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                codeChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                codeChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                // style changes only; the text is the same
            }
        });
        editor.getDocument().addUndoableEditListener(undo);
        bindEditKey("control Z", this::undo);
        bindEditKey("control Y", this::redo);
        keys = new EditorKeys(editor);
        keys.install();
        editor.addMouseWheelListener(e -> {
            if (e.isControlDown()) {
                onTextSizeWheel.accept(e.getWheelRotation() < 0 ? 1 : -1);
            } else { // let the scroll pane scroll as usual
                scroll.dispatchEvent(SwingUtilities.convertMouseEvent(editor, e, scroll));
            }
        });
    }

    public String getCode() {
        return editor.getText();
    }

    public void setCode(String code) {
        editor.setText(code.replace("\r\n", "\n")); // a file saved on Windows ends its lines with \r\n
        editor.setCaretPosition(0);
        undo.discardAllEdits(); // undo shouldn't bring back the code that was here before
        dirty = false;
    }

    public void setOnCodeChanged(Runnable onCodeChanged) {
        this.onCodeChanged = onCodeChanged;
    }

    // Puts the cursor at the start of a line (lines count from 1), ready to type.
    public void startTypingAt(int line, int column) {
        try {
            editor.setCaretPosition(editor.getLineStartOffset(line - 1) + column);
        } catch (BadLocationException e) {
            // no such line
        }
        editor.requestFocusInWindow();
    }

    // Pastes into the editor even if the keyboard is on another part of the window.
    public void pasteHere() {
        editor.requestFocusInWindow();
        editor.paste();
    }

    public void selectAllHere() {
        editor.requestFocusInWindow();
        editor.selectAll();
    }

    public boolean find(String text) {
        return keys.find(text);
    }

    public void setOnTextSizeWheel(IntConsumer onTextSizeWheel) {
        this.onTextSizeWheel = onTextSizeWheel;
    }

    // Changing the look gives the text area a new highlighter, so the line marks are put back.
    public void restoreMarks() {
        errorMarks.clear();
        for (int line : errors.keySet()) {
            errorMarks.add(mark(line, () -> Style.ERROR_LINE));
        }
        stepChanged(model);
    }

    // Uses the current code font, after the text size changed.
    public void applyFont() {
        editor.setFont(Style.CODE_FONT);
        lineNumbers.setFont(Style.CODE_FONT);
        lineNumbers.revalidate();
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markSaved() {
        dirty = false;
    }

    // How wide this panel must be to show the longest line without cutting it off.
    public int widthForLongestLine() {
        int longest = 0;
        for (String line : editor.getText().split("\n", -1)) {
            longest = Math.max(longest, line.replace("\t", "    ").length());
        }
        Insets margin = editor.getMargin();
        return longest * editor.getFontMetrics(editor.getFont()).charWidth('0') + margin.left + margin.right
                + lineNumbers.getPreferredSize().width + scroll.getVerticalScrollBar().getPreferredSize().width;
    }

    void undo() {
        if (editor.isEditable() && undo.canUndo()) {
            undo.undo();
        }
    }

    void redo() {
        if (editor.isEditable() && undo.canRedo()) {
            undo.redo();
        }
    }

    // The text box itself, for the tests.
    JTextArea getEditor() {
        return editor;
    }

    public void setEditable(boolean editable) {
        editor.setEditable(editable);
    }

    // Marks each compile error's line in red; the message shows when you hover over it.
    public void showErrors(List<CompileResult.CompileError> compileErrors) {
        clearErrors();
        for (CompileResult.CompileError error : compileErrors) {
            int line = (int) error.line();
            if (line > 0) {
                errors.merge(line, error.message(), (a, b) -> a + "\n" + b);
                errorMarks.add(mark(line, () -> Style.ERROR_LINE));
            }
        }
        if (!compileErrors.isEmpty() && compileErrors.get(0).line() > 0) {
            scrollToLine((int) compileErrors.get(0).line());
        }
    }

    @Override
    public void stepChanged(TraceModel model) {
        for (Object mark : stepMarks) {
            editor.getHighlighter().removeHighlight(mark);
        }
        stepMarks.clear();
        Snapshot now = model.getSnapshot();
        Snapshot before = model.getPreviousSnapshot();
        int current = now == null ? -1 : now.getLine();
        int previous = before == null ? -1 : before.getLine();
        boolean crashed = now != null && now.getException() != null;
        if (previous > 0 && previous != current) {
            stepMarks.add(mark(previous, () -> Style.PREVIOUS_LINE));
        }
        if (current > 0) {
            stepMarks.add(mark(current, crashed ? () -> Style.ERROR_LINE : () -> Style.CURRENT_LINE));
            scrollToLine(current);
        }
        lineNumbers.show(current, previous, crashed, model.hasSteps());
    }

    private void bindEditKey(String key, Runnable action) {
        editor.getInputMap().put(KeyStroke.getKeyStroke(key), key);
        editor.getActionMap().put(key, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    private void codeChanged() {
        dirty = true;
        tokens = JavaTokens.of(editor.getText());
        editor.repaint(); // a /* can change the colour of every line after it
        clearErrors();
        model.clear();
        onCodeChanged.run();
    }

    private void clearErrors() {
        for (Object mark : errorMarks) {
            editor.getHighlighter().removeHighlight(mark);
        }
        errorMarks.clear();
        errors.clear();
    }

    // Highlights a whole line (lines count from 1). Returns a tag for removing it later.
    private Object mark(int line, Supplier<Color> color) {
        try {
            int start = editor.getLineStartOffset(line - 1);
            int end = editor.getLineEndOffset(line - 1);
            return editor.getHighlighter().addHighlight(start, end, new LinePainter(color));
        } catch (BadLocationException e) {
            return null; // the line doesn't exist (the code was changed)
        }
    }

    private void scrollToLine(int line) {
        try {
            Rectangle2D where = editor.modelToView2D(editor.getLineStartOffset(line - 1));
            if (where != null) {
                Rectangle area = where.getBounds();
                area.grow(0, area.height * 3); // keep a few lines visible around it
                editor.scrollRectToVisible(area);
            }
        } catch (BadLocationException e) {
            // the line doesn't exist (the code was changed)
        }
    }

    private int lineAt(Point point) {
        try {
            return editor.getLineOfOffset(editor.viewToModel2D(point)) + 1;
        } catch (BadLocationException e) {
            return -1;
        }
    }

    private static Color colorOf(JavaTokens.Kind kind) {
        return switch (kind) {
            case KEYWORD -> Style.CODE_KEYWORD;
            case TYPE -> Style.CODE_TYPE;
            case METHOD -> Style.CODE_METHOD;
            case STRING -> Style.CODE_STRING;
            case NUMBER -> Style.CODE_NUMBER;
            case COMMENT -> Style.CODE_COMMENT;
        };
    }

    // FlatLaf's look for a text area, but with our own way of drawing each line.
    private final class ColoredTextAreaUI extends FlatTextAreaUI {
        @Override
        public View create(Element element) {
            return new ColoredView(element);
        }
    }

    // Swing draws a line of text with one call to drawUnselectedText. This overrides it to draw
    // each piece of the line in its own colour instead.
    private final class ColoredView extends PlainView {

        ColoredView(Element element) {
            super(element);
        }

        @Override
        protected float drawUnselectedText(Graphics2D g, float x, float y, int start, int end) throws BadLocationException {
            int position = start;
            for (JavaTokens.Token token : tokens) {
                if (token.end() <= position) {
                    continue;
                }
                if (token.start() >= end) {
                    break;
                }
                if (token.start() > position) {
                    x = drawPiece(g, x, y, position, token.start(), Style.INK);
                    position = token.start();
                }
                int stop = Math.min(token.end(), end);
                x = drawPiece(g, x, y, position, stop, colorOf(token.kind()));
                position = stop;
            }
            return position < end ? drawPiece(g, x, y, position, end, Style.INK) : x;
        }

        private float drawPiece(Graphics2D g, float x, float y, int start, int end, Color color) throws BadLocationException {
            Segment text = getLineBuffer();
            getDocument().getText(start, end - start, text);
            g.setColor(color);
            return Utilities.drawTabbedText(text, x, y, g, this, start);
        }
    }

    // Paints a coloured band across the whole width of one line, behind the text.
    private static class LinePainter implements Highlighter.HighlightPainter {

        private final Supplier<Color> color; // asked each time it paints, so it follows dark mode

        LinePainter(Supplier<Color> color) {
            this.color = color;
        }

        @Override
        public void paint(Graphics g, int start, int end, Shape bounds, JTextComponent text) {
            try {
                Rectangle2D line = text.modelToView2D(start);
                g.setColor(color.get());
                g.fillRect(0, (int) line.getY(), text.getWidth(), (int) line.getHeight());
            } catch (BadLocationException e) {
                // nothing to paint
            }
        }
    }

    // The line numbers down the left side, with a ▶ at the line about to run and a smaller grey
    // one at the line that just ran. Clicking a number jumps to the next time that line runs.
    private static class LineNumbers extends JComponent {

        private final JTextArea editor;
        private int current = -1;
        private int previous = -1;
        private boolean crashed;
        private boolean clickable;

        LineNumbers(JTextArea editor, IntConsumer onClick) {
            this.editor = editor;
            setFont(editor.getFont());
            setToolTipText("click a line number to jump to the next time it runs");
            editor.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    revalidate();
                    repaint();
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    revalidate();
                    repaint();
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                }
            });
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (clickable) {
                        try {
                            onClick.accept(editor.getLineOfOffset(editor.viewToModel2D(new Point(0, e.getY()))) + 1);
                        } catch (BadLocationException ignored) {
                            // clicked below the last line
                        }
                    }
                }
            });
        }

        void show(int current, int previous, boolean crashed, boolean clickable) {
            this.current = current;
            this.previous = previous;
            this.crashed = crashed;
            this.clickable = clickable;
            setCursor(Cursor.getPredefinedCursor(clickable ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            int digits = Math.max(2, String.valueOf(editor.getLineCount()).length());
            return new Dimension(getFontMetrics(getFont()).charWidth('0') * digits + 30, editor.getHeight());
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Rectangle clip = g.getClipBounds();
            g2.setColor(Style.BACKGROUND);
            g2.fillRect(clip.x, clip.y, clip.width, clip.height);
            g2.setColor(Style.HAIRLINE);
            g2.drawLine(getWidth() - 1, clip.y, getWidth() - 1, clip.y + clip.height);
            FontMetrics metrics = g2.getFontMetrics(getFont());
            try {
                int first = editor.getLineOfOffset(editor.viewToModel2D(new Point(0, clip.y)));
                int last = editor.getLineOfOffset(editor.viewToModel2D(new Point(0, clip.y + clip.height)));
                for (int line = first; line <= last; line++) {
                    Rectangle2D where = editor.modelToView2D(editor.getLineStartOffset(line));
                    int number = line + 1;
                    int baseline = (int) where.getY() + metrics.getAscent();
                    double middle = where.getY() + where.getHeight() / 2;
                    Color color = Style.MUTED;
                    Font font = getFont();
                    if (number == current) {
                        color = crashed ? Style.ERROR : Style.ACCENT;
                        font = getFont().deriveFont(Font.BOLD);
                        arrow(g2, 6, middle, 5, color);
                    } else if (number == previous) {
                        color = Style.INK_SOFT;
                        arrow(g2, 7, middle, 3.5, Style.MUTED);
                    }
                    String text = String.valueOf(number);
                    g2.setFont(font);
                    g2.setColor(color);
                    g2.drawString(text, getWidth() - 8 - g2.getFontMetrics().stringWidth(text), baseline);
                }
            } catch (BadLocationException e) {
                // the text changed while painting; the next repaint fixes it
            }
        }

        private static void arrow(Graphics2D g, double x, double middle, double size, Color color) {
            Path2D triangle = new Path2D.Double();
            triangle.moveTo(x, middle - size);
            triangle.lineTo(x + size * 1.3, middle);
            triangle.lineTo(x, middle + size);
            triangle.closePath();
            g.setColor(color);
            g.fill(triangle);
        }
    }
}
