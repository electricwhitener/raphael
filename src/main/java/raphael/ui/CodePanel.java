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
import raphael.model.ThrownException;
import raphael.render.Style;

// The code editor. While stepping, two lines are marked: the line about to run (blue, with a ▶
// in the gutter) and the line that just ran (grey) — the picture shows the state after the grey
// line. A crash makes its line red. Compile errors are marked red too; hover to see the message.
// Click a line number to jump to the next time that line runs. Editing the code clears the old
// run, because its steps no longer match the code. Ctrl+Z and Ctrl+Y undo and redo edits.
// The code is coloured as in an IDE: JavaTokens finds the pieces, ColoredView draws them.
// A program of several files has one FileText per file (its text, undo history, unsaved flag and
// error marks); the editor shows one at a time and, while stepping, follows the running line from
// file to file. A program typed in on its own is one FileText with no path.
// OOP: inheritance (extends JPanel); implements TraceListener (Observer pattern); nested classes
// for the line numbers, the line highlight and the coloured text, which overrides how Swing draws
// a line; an anonymous subclass of JTextArea.
public class CodePanel extends JPanel implements TraceListener {

    private final TraceModel model;
    private final Map<String, FileText> texts = new java.util.LinkedHashMap<>(); // path → its text, in tab order
    private FileText shown; // the file in the editor
    private java.util.function.Consumer<String> onFileShown = path -> { }; // does nothing until the program sets it
    private final List<Object> errorMarks = new ArrayList<>();
    private final List<Object> stepMarks = new ArrayList<>();
    private final LineNumbers lineNumbers;
    private final JScrollPane scroll;
    private final EditorKeys keys;
    private final FindBar findBar;
    private Runnable onCaretMoved = () -> { }; // does nothing until the program sets it
    private final List<Object> bracketMarks = new ArrayList<>();
    private Runnable onCodeChanged = () -> { }; // does nothing until the window sets it
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
            return shown.errors.get(lineAt(event.getPoint()));
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
        lineNumbers = new LineNumbers(editor, line -> model.jumpToLine(shown.path, line));
        scroll = new JScrollPane(editor);
        scroll.setRowHeaderView(lineNumbers);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(scroll);
        findBar = new FindBar(editor, this::asOneEdit);
        add(findBar, BorderLayout.NORTH);

        show(newText(null, ""));
        bindEditKey("control Z", this::undo);
        bindEditKey("control Y", this::redo);
        bindEditKey("shift control Z", this::redo); // the usual redo on a Mac, and in many editors
        bindEditKey("control F", () -> findBar.open(false));
        bindEditKey("control H", () -> findBar.open(true)); // in a text box, Ctrl+H would otherwise delete a letter
        bindEditKey("F3", () -> findBar.next(false));
        bindEditKey("shift F3", () -> findBar.next(true));
        editor.addCaretListener(e -> {
            markBrackets();
            onCaretMoved.run();
        });
        keys = new EditorKeys(editor);
        keys.install();
        editor.addMouseWheelListener(e -> {
            if (Shortcuts.isDown(e)) {
                onTextSizeWheel.accept(e.getWheelRotation() < 0 ? 1 : -1);
            } else { // let the scroll pane scroll as usual
                scroll.dispatchEvent(SwingUtilities.convertMouseEvent(editor, e, scroll));
            }
        });
    }

    // The text of the file in the editor.
    public String getCode() {
        return editor.getText();
    }

    // A program of one file, typed in or opened on its own.
    public void setCode(String code) {
        texts.clear();
        show(newText(null, code));
        loaded();
    }

    // New code is in: old errors and the old run no longer apply, and the window hears of it.
    private void loaded() {
        clearErrors();
        model.clear();
        onCodeChanged.run();
    }

    // A program of several files; the one named by first (or the first file) is shown.
    public void setFiles(List<raphael.model.SourceFile> files, String first) {
        texts.clear();
        FileText front = null;
        for (raphael.model.SourceFile file : files) {
            FileText text = newText(file.path(), file.text());
            if (front == null || file.path().equals(first)) {
                front = text;
            }
        }
        show(front != null ? front : newText(null, ""));
        loaded();
    }

    // Every file of the program, with its text as it is now, in tab order.
    public List<raphael.model.SourceFile> getFiles() {
        List<raphael.model.SourceFile> files = new ArrayList<>();
        for (FileText text : texts.values()) {
            files.add(new raphael.model.SourceFile(text.path, editorText(text)));
        }
        return files;
    }

    // The paths of the program's files, in tab order (one null for a program of one file).
    public List<String> getPaths() {
        return new ArrayList<>(texts.keySet());
    }

    // The path of the file in the editor (null for a program of one file).
    public String getShownPath() {
        return shown.path;
    }

    // Puts this file in the editor, if the program has it.
    public void showFile(String path) {
        FileText text = texts.get(path);
        if (text != null && text != shown) {
            show(text);
        }
    }

    // Told the path of the file the editor shows, whenever it changes.
    public void setOnFileShown(java.util.function.Consumer<String> onFileShown) {
        this.onFileShown = onFileShown;
    }

    public boolean isDirty(String path) {
        FileText text = texts.get(path);
        return text != null && text.dirty;
    }

    public void markSaved(String path) {
        FileText text = texts.get(path);
        if (text != null) {
            text.dirty = false;
        }
    }

    // A new file's text, kept in this program's list of files. Each has its own document, so its
    // own undo history; the listeners are attached once, here.
    private FileText newText(String path, String code) {
        FileText text = new FileText(path);
        try {
            text.document.insertString(0, code.replace("\r\n", "\n"), null); // a file saved on Windows ends its lines with \r\n
        } catch (BadLocationException e) {
            throw new IllegalStateException(e); // inserting at 0 always works
        }
        text.tokens = JavaTokens.of(code.replace("\r\n", "\n"));
        text.document.addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                codeChanged(text);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                codeChanged(text);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                // style changes only; the text is the same
            }
        });
        text.document.addUndoableEditListener(text.undo); // added after the first text: undo can't remove it
        texts.put(path, text);
        return text;
    }

    private static String editorText(FileText text) {
        try {
            return text.document.getText(0, text.document.getLength());
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
    }

    // Puts a file's text in the editor, with its own error marks; the step marks follow.
    private void show(FileText text) {
        if (shown != null) {
            shown.caret = editor.getCaretPosition();
        }
        for (Object mark : errorMarks) {
            editor.getHighlighter().removeHighlight(mark);
        }
        errorMarks.clear();
        for (Object mark : stepMarks) {
            editor.getHighlighter().removeHighlight(mark);
        }
        stepMarks.clear();
        shown = text;
        editor.setDocument(text.document);
        editor.setTabSize(4); // kept on the document, so set for each one
        editor.setCaretPosition(Math.min(text.caret, text.document.getLength()));
        for (int line : text.errors.keySet()) {
            errorMarks.add(mark(line, () -> Style.ERROR_LINE));
        }
        lineNumbers.revalidate();
        lineNumbers.repaint();
        stepChanged(model);
        findBar.refresh();
        onFileShown.accept(text.path);
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

    // The find bar: opened (with the replace row, or not), or the next or previous match.
    public void openFind(boolean withReplace) {
        findBar.open(withReplace);
    }

    public void findNext(boolean backwards) {
        findBar.next(backwards);
    }

    // Esc: closes the find bar if it's open. False if it wasn't.
    public boolean closeFind() {
        if (!findBar.isVisible()) {
            return false;
        }
        findBar.close();
        return true;
    }

    // The cursor's line and column, counting from 1.
    public int caretLine() {
        try {
            return editor.getLineOfOffset(editor.getCaretPosition()) + 1;
        } catch (BadLocationException e) {
            return 1;
        }
    }

    public int caretColumn() {
        try {
            return editor.getCaretPosition() - editor.getLineStartOffset(caretLine() - 1) + 1;
        } catch (BadLocationException e) {
            return 1;
        }
    }

    public int lineCount() {
        return editor.getLineCount();
    }

    public void setOnCaretMoved(Runnable onCaretMoved) {
        this.onCaretMoved = onCaretMoved;
    }

    // Go to line: the cursor at its start, in view.
    public void goToLine(int line) {
        int target = Math.max(1, Math.min(line, editor.getLineCount()));
        try {
            editor.setCaretPosition(editor.getLineStartOffset(target - 1));
        } catch (BadLocationException e) {
            return;
        }
        scrollToLine(target);
        editor.requestFocusInWindow();
    }

    // Edit menu: cut, copy and paste in the editor, wherever the keyboard is.
    public void cut() {
        editor.requestFocusInWindow();
        editor.cut();
    }

    public void copy() {
        editor.requestFocusInWindow();
        editor.copy();
    }

    // Shift+Alt+Down: the line the cursor is on (or the selected lines) again, below.
    public void duplicateLine() {
        if (editor.isEditable()) {
            keys.duplicateLines();
        }
    }

    // Runs a change made of many edits (Replace All) as one, so one undo takes it all back.
    private void asOneEdit(Runnable change) {
        javax.swing.undo.CompoundEdit all = new javax.swing.undo.CompoundEdit();
        javax.swing.event.UndoableEditListener collect = e -> all.addEdit(e.getEdit());
        shown.document.removeUndoableEditListener(shown.undo);
        shown.document.addUndoableEditListener(collect);
        try {
            change.run();
        } finally {
            shown.document.removeUndoableEditListener(collect);
            shown.document.addUndoableEditListener(shown.undo);
            all.end();
            shown.undo.addEdit(all);
        }
    }

    // A bracket next to the cursor and the one it pairs with are outlined, as in an IDE.
    private void markBrackets() {
        for (Object mark : bracketMarks) {
            editor.getHighlighter().removeHighlight(mark);
        }
        bracketMarks.clear();
        String text = editor.getText();
        int caret = editor.getCaretPosition();
        int at = -1;
        if (caret > 0 && "()[]{}".indexOf(text.charAt(caret - 1)) >= 0) {
            at = caret - 1;
        } else if (caret < text.length() && "()[]{}".indexOf(text.charAt(caret)) >= 0) {
            at = caret;
        }
        if (at < 0 || inStringOrComment(at)) {
            return;
        }
        int match = matchingBracket(text, at);
        if (match < 0) {
            return;
        }
        for (int position : new int[] {at, match}) {
            try {
                bracketMarks.add(editor.getHighlighter().addHighlight(position, position + 1, BRACKET_PAINTER));
            } catch (BadLocationException e) {
                // the text changed meanwhile
            }
        }
    }

    // Where the bracket at this position is closed (or opened), skipping strings and comments; -1 if nowhere.
    private int matchingBracket(String text, int at) {
        char c = text.charAt(at);
        int pair = "()[]{}".indexOf(c);
        boolean opens = pair % 2 == 0;
        char other = "()[]{}".charAt(opens ? pair + 1 : pair - 1);
        int depth = 0;
        for (int i = at; i >= 0 && i < text.length(); i += opens ? 1 : -1) {
            char here = text.charAt(i);
            if ((here == c || here == other) && !inStringOrComment(i)) {
                depth += here == c ? 1 : -1;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private boolean inStringOrComment(int position) {
        for (JavaTokens.Token token : shown.tokens) {
            if (token.start() > position) {
                return false;
            }
            if (position < token.end()) {
                return token.kind() == JavaTokens.Kind.STRING || token.kind() == JavaTokens.Kind.COMMENT;
            }
        }
        return false;
    }

    // An outline round a bracket and its partner.
    private static final Highlighter.HighlightPainter BRACKET_PAINTER = (g, start, end, bounds, text) -> {
        try {
            Rectangle2D box = text.modelToView2D(start).createUnion(text.modelToView2D(end));
            g.setColor(Style.MUTED);
            g.drawRect((int) box.getX(), (int) box.getY(), Math.max(1, (int) box.getWidth() - 1), (int) box.getHeight() - 1);
        } catch (BadLocationException e) {
            // nothing to draw
        }
    };

    public void setOnTextSizeWheel(IntConsumer onTextSizeWheel) {
        this.onTextSizeWheel = onTextSizeWheel;
    }

    // Changing the look gives the text area a new highlighter, so the line marks are put back.
    public void restoreMarks() {
        errorMarks.clear();
        for (int line : shown.errors.keySet()) {
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

    // Whether any of the program's files has changes that aren't saved.
    public boolean isDirty() {
        for (FileText text : texts.values()) {
            if (text.dirty) {
                return true;
            }
        }
        return false;
    }

    public void markSaved() {
        for (FileText text : texts.values()) {
            text.dirty = false;
        }
    }

    // For code that isn't in any file yet, such as a restored draft.
    public void markUnsaved() {
        shown.dirty = true;
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
        if (editor.isEditable() && shown.undo.canUndo()) {
            shown.undo.undo();
        }
    }

    void redo() {
        if (editor.isEditable() && shown.undo.canRedo()) {
            shown.undo.redo();
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
    // An error with a file goes in that file; one without goes in the file on show. The first
    // error's file comes into the editor.
    public void showErrors(List<CompileResult.CompileError> compileErrors) {
        clearErrors();
        for (CompileResult.CompileError error : compileErrors) {
            int line = (int) error.line();
            FileText text = error.file() != null && texts.containsKey(error.file()) ? texts.get(error.file()) : shown;
            if (line > 0) {
                text.errors.merge(line, error.message(), (a, b) -> a + "\n" + b);
            }
        }
        if (!compileErrors.isEmpty()) {
            CompileResult.CompileError first = compileErrors.get(0);
            FileText firstFile = first.file() != null && texts.containsKey(first.file()) ? texts.get(first.file()) : shown;
            show(firstFile); // marks its errors
            if (first.line() > 0) {
                scrollToLine((int) first.line());
            }
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
        String runningIn = fileOf(now);
        if (now != null && runningIn != null && texts.containsKey(runningIn) && texts.get(runningIn) != shown) {
            show(texts.get(runningIn)); // follow the program into another file; show() marks it
            return;
        }
        int current = now == null || !java.util.Objects.equals(runningIn, shown.path) ? -1 : now.getLine();
        int previous = before == null || !java.util.Objects.equals(fileOf(before), shown.path) ? -1 : before.getLine();
        boolean crashed = now != null && now.getException() != null;
        if (previous > 0 && previous != current) {
            stepMarks.add(mark(previous, () -> Style.PREVIOUS_LINE));
        }
        ThrownException thrown = now == null ? null : now.getThrown();
        if (current > 0) {
            stepMarks.add(mark(current, crashed ? () -> Style.ERROR_LINE : () -> Style.CURRENT_LINE));
            if (thrown != null && thrown.throwLine() > 0) {
                scrollToLine(thrown.throwLine()); // then the catch: both stay in view if they fit
            }
            scrollToLine(current);
        }
        lineNumbers.show(current, previous, crashed, model.hasSteps());
        boolean bracketHere = thrown != null && java.util.Objects.equals(thrown.throwFile(), shown.path)
                && java.util.Objects.equals(thrown.catchFile(), shown.path);
        lineNumbers.showThrow(bracketHere ? thrown.throwLine() : -1, bracketHere ? thrown.catchLine() : -1);
    }

    // The file the program is running in at this step (null for a program of one file).
    private static String fileOf(Snapshot snapshot) {
        return snapshot == null || snapshot.getFrames().isEmpty() ? null : snapshot.getFrames().get(0).getFile();
    }

    private void bindEditKey(String key, Runnable action) {
        editor.getInputMap().put(Shortcuts.key(key), key);
        editor.getActionMap().put(key, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    private void codeChanged(FileText text) {
        text.dirty = true;
        text.tokens = JavaTokens.of(editorText(text));
        lineNumbers.revalidate();
        lineNumbers.repaint();
        if (text != shown) {
            return;
        }
        javax.swing.SwingUtilities.invokeLater(findBar::refresh); // the matches move with the text
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
        for (FileText text : texts.values()) {
            text.errors.clear();
        }
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
                snapToWholeLines(area.height / 7);
            }
        } catch (BadLocationException e) {
            // the line doesn't exist (the code was changed)
        }
    }

    // Moves the view so its top line is whole, not cut in half by the edge.
    private void snapToWholeLines(int lineHeight) {
        if (lineHeight <= 0 || !(editor.getParent() instanceof javax.swing.JViewport)) {
            return;
        }
        javax.swing.JViewport view = (javax.swing.JViewport) editor.getParent();
        Point at = view.getViewPosition();
        int top = editor.getInsets().top;
        int y = top + Math.round((at.y - top) / (float) lineHeight) * lineHeight;
        int lowest = Math.max(0, editor.getHeight() - view.getHeight());
        view.setViewPosition(new Point(at.x, Math.max(0, Math.min(y, lowest))));
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
            for (JavaTokens.Token token : shown.tokens) {
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

        private static final int BRACKET = 12; // room at the left for a caught exception's jump

        private final JTextArea editor;
        private int current = -1;
        private int previous = -1;
        private boolean crashed;
        private boolean clickable;
        private int throwLine = -1; // a caught exception's jump, from the line that threw it
        private int catchLine = -1; // to the line of its catch, or -1 for none

        LineNumbers(JTextArea editor, IntConsumer onClick) {
            this.editor = editor;
            setFont(editor.getFont());
            setToolTipText("click a line number to jump to the next time it runs"); // CodePanel repaints it as the text changes
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

        void showThrow(int from, int to) {
            throwLine = from;
            catchLine = to;
            repaint();
        }

        // The bracket's edge triangle stays at the edge while the code scrolls, so a scroll repaints
        // the whole gutter, not just the part that came into view.
        @Override
        public void addNotify() {
            super.addNotify();
            if (getParent() instanceof javax.swing.JViewport) {
                ((javax.swing.JViewport) getParent()).addChangeListener(e -> {
                    if (throwLine > 0) {
                        repaint();
                    }
                });
            }
        }

        @Override
        public Dimension getPreferredSize() {
            int digits = Math.max(2, String.valueOf(editor.getLineCount()).length());
            return new Dimension(getFontMetrics(getFont()).charWidth('0') * digits + 30 + BRACKET, editor.getHeight());
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
                        arrow(g2, BRACKET + 6, middle, 5, color);
                    } else if (number == previous) {
                        color = Style.INK_SOFT;
                        arrow(g2, BRACKET + 7, middle, 3.5, Style.MUTED);
                    }
                    String text = String.valueOf(number);
                    g2.setFont(font);
                    g2.setColor(color);
                    g2.drawString(text, getWidth() - 8 - g2.getFontMetrics().stringWidth(text), baseline);
                }
                if (throwLine > 0 && catchLine > 0 && throwLine != catchLine) {
                    throwBracket(g2);
                }
            } catch (BadLocationException e) {
                // the text changed while painting; the next repaint fixes it
            }
        }

        // A caught exception's jump, in the gutter's left edge: a dot at the line that threw it,
        // a red stem along the edge, and an arrowhead at the line of its catch. When the throw
        // line is scrolled out of view, the stem stops at the edge, with a small triangle pointing
        // towards it and its line number.
        private void throwBracket(Graphics2D g) throws BadLocationException {
            Rectangle visible = getVisibleRect();
            double from = middleOf(throwLine);
            double to = middleOf(catchLine);
            double top = visible.y + 6;
            double bottom = visible.y + visible.height - 6;
            boolean above = from < top;
            boolean below = from > bottom;
            double start = above ? top + 12 : below ? bottom - 12 : from;
            g.setColor(Style.ERROR);
            g.setStroke(new java.awt.BasicStroke(2f, java.awt.BasicStroke.CAP_BUTT, java.awt.BasicStroke.JOIN_MITER));
            Path2D line = new Path2D.Double();
            if (above || below) {
                line.moveTo(4, start);
            } else {
                line.moveTo(8, start);
                line.lineTo(4, start);
            }
            line.lineTo(4, to);
            line.lineTo(8, to);
            g.draw(line);
            if (above || below) {
                // A triangle at the edge, pointing to the throw line, and its number.
                double tip = above ? top : bottom;
                double base = above ? top + 6 : bottom - 6;
                Path2D pointer = new Path2D.Double();
                pointer.moveTo(4, tip);
                pointer.lineTo(0, base);
                pointer.lineTo(8, base);
                pointer.closePath();
                g.fill(pointer);
                g.setFont(Style.CAPTION_FONT);
                g.drawString(String.valueOf(throwLine), 10, (float) (above ? top + 9 : bottom - 1));
            } else {
                g.fill(new java.awt.geom.Ellipse2D.Double(6, from - 2.5, 5, 5));
            }
            Path2D head = new Path2D.Double();
            head.moveTo(BRACKET + 2, to);
            head.lineTo(8, to - 4);
            head.lineTo(8, to + 4);
            head.closePath();
            g.fill(head);
        }

        private double middleOf(int line) throws BadLocationException {
            Rectangle2D where = editor.modelToView2D(editor.getLineStartOffset(Math.min(line, editor.getLineCount()) - 1));
            return where.getY() + where.getHeight() / 2;
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

    // One file of the program: its text, undo history, unsaved flag, compile errors, colours and
    // where the cursor was. The editor shows one FileText at a time.
    private static final class FileText {
        final String path; // null for a program of one file
        final javax.swing.text.PlainDocument document = new javax.swing.text.PlainDocument();
        final UndoManager undo = new UndoManager(); // the JDK's list of edits to step back through
        final Map<Integer, String> errors = new HashMap<>(); // line → message
        List<JavaTokens.Token> tokens = List.of(); // the coloured pieces of the text
        boolean dirty; // changed since it was loaded or saved
        int caret;

        FileText(String path) {
            this.path = path;
        }
    }
}
