package raphael.ui;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.UnaryOperator;
import javax.swing.AbstractAction;
import javax.swing.JTextArea;
import javax.swing.text.BadLocationException;

// The small things a code editor does for you: Enter keeps the indentation, Tab and Shift+Tab
// move lines in and out, "}" lines up with its block, Ctrl+/ comments lines, brackets and quotes
// close themselves, Shift+Alt+Down duplicates a line, and find.
// OOP: composition (it holds a text area and adds to it, instead of being one); encapsulation
// (the line handling is private).
public class EditorKeys {

    private static final String INDENT = "    ";

    private final JTextArea editor;

    public EditorKeys(JTextArea editor) {
        this.editor = editor;
    }

    public void install() {
        editor.setFocusTraversalKeysEnabled(false); // otherwise Tab and Shift+Tab move to another component
        bind("ENTER", this::newLine);
        bind("TAB", this::indent);
        bind("shift TAB", this::unindent);
        bind("typed }", () -> typeCloser('}'));
        bind("typed )", () -> typeCloser(')'));
        bind("typed ]", () -> typeCloser(']'));
        bind("typed (", () -> typeOpener('(', ')'));
        bind("typed [", () -> typeOpener('[', ']'));
        bind("typed {", () -> typeOpener('{', '}'));
        bind("typed \"", () -> typeQuote('"'));
        bind("typed '", () -> typeQuote('\''));
        bind("control SLASH", this::toggleComment);
        bind("shift alt DOWN", this::duplicateLines);
    }

    private void bind(String key, Runnable action) {
        editor.getInputMap().put(Shortcuts.key(key), key);
        editor.getActionMap().put(key, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (editor.isEditable()) {
                    action.run();
                }
            }
        });
    }

    // Starts a new line at the same indentation, one level deeper after a "{". Between "{" and
    // "}" it opens the block: the cursor on its own line inside, the "}" below.
    void newLine() {
        String before = textBeforeCaretOnLine();
        String indent = before.substring(0, before.length() - before.stripLeading().length());
        boolean opens = before.stripTrailing().endsWith("{");
        if (opens && editor.getSelectedText() == null && charAfterCaret() == '}') {
            int at = editor.getSelectionStart();
            editor.replaceSelection("\n" + indent + INDENT + "\n" + indent);
            editor.setCaretPosition(at + 1 + indent.length() + INDENT.length());
            return;
        }
        editor.replaceSelection("\n" + indent + (opens ? INDENT : ""));
    }

    // An opening bracket brings its closer, when the next character is a space, the end of the
    // line or another closer (so typing before a word doesn't add one). The cursor goes between.
    void typeOpener(char open, char close) {
        char next = charAfterCaret();
        if (editor.getSelectedText() == null && (next == 0 || next == '\n' || next == ' ' || ")]};,".indexOf(next) >= 0)) {
            int at = editor.getSelectionStart();
            editor.replaceSelection(String.valueOf(open) + close);
            editor.setCaretPosition(at + 1); // between the two
        } else {
            editor.replaceSelection(String.valueOf(open));
        }
    }

    // A closer typed just before the same closer steps over it instead of adding a second one.
    // A "}" at the start of a line moves back one level, to line up with its block.
    void typeCloser(char close) {
        if (editor.getSelectedText() == null && charAfterCaret() == close) {
            editor.setCaretPosition(editor.getCaretPosition() + 1);
            return;
        }
        if (close == '}') {
            closeBrace();
        } else {
            editor.replaceSelection(String.valueOf(close));
        }
    }

    // A quote brings its partner, unless it's closing one (then it steps over it) or comes right
    // after a letter or digit (as in don't).
    void typeQuote(char quote) {
        if (editor.getSelectedText() == null && charAfterCaret() == quote) {
            editor.setCaretPosition(editor.getCaretPosition() + 1);
            return;
        }
        String before = textBeforeCaretOnLine();
        char previous = before.isEmpty() ? ' ' : before.charAt(before.length() - 1);
        char next = charAfterCaret();
        boolean pairs = editor.getSelectedText() == null && !Character.isLetterOrDigit(previous) && previous != '\\'
                && (next == 0 || next == '\n' || next == ' ' || ")]};,".indexOf(next) >= 0);
        int at = editor.getSelectionStart();
        editor.replaceSelection(pairs ? "" + quote + quote : String.valueOf(quote));
        editor.setCaretPosition(at + 1); // after the quote: between the pair, if there is one
    }

    // The selected lines (or the cursor's line) again, just below; the cursor moves with the copy.
    void duplicateLines() {
        try {
            int[] range = selectedRange();
            String lines = editor.getText(range[0], range[1] - range[0]);
            int caretFromStart = editor.getCaretPosition() - range[0];
            editor.insert("\n" + lines, range[1]);
            editor.setCaretPosition(range[1] + 1 + Math.min(caretFromStart, lines.length()));
        } catch (BadLocationException e) {
            // the text changed underneath us; nothing to do
        }
    }

    // The character just after the cursor, or 0 at the end of the text.
    private char charAfterCaret() {
        int caret = editor.getSelectionEnd();
        try {
            return caret < editor.getDocument().getLength() ? editor.getText(caret, 1).charAt(0) : 0;
        } catch (BadLocationException e) {
            return 0;
        }
    }

    // A "}" typed at the start of a line moves back one level, to line up with its block.
    void closeBrace() {
        String before = textBeforeCaretOnLine();
        int caret = editor.getCaretPosition();
        if (editor.getSelectedText() == null && before.endsWith(INDENT) && before.isBlank()) {
            editor.replaceRange("}", caret - INDENT.length(), caret);
        } else {
            editor.replaceSelection("}");
        }
    }

    // With several lines selected, moves them all in. Otherwise types an indent.
    void indent() {
        String selected = editor.getSelectedText();
        if (selected != null && selected.contains("\n")) {
            changeLines(line -> line.isBlank() ? line : INDENT + line);
        } else {
            editor.replaceSelection(INDENT);
        }
    }

    void unindent() {
        changeLines(line -> {
            int spaces = 0;
            while (spaces < INDENT.length() && spaces < line.length() && line.charAt(spaces) == ' ') {
                spaces++;
            }
            return line.substring(spaces);
        });
    }

    // Comments out the selected lines, or un-comments them if they're all comments already.
    void toggleComment() {
        boolean allComments = true;
        for (String line : selectedLines()) {
            allComments &= line.isBlank() || line.stripLeading().startsWith("//");
        }
        boolean remove = allComments;
        changeLines(line -> {
            if (line.isBlank()) {
                return line;
            }
            int start = line.length() - line.stripLeading().length();
            if (!remove) {
                return line.substring(0, start) + "// " + line.substring(start);
            }
            String rest = line.substring(start + 2);
            return line.substring(0, start) + (rest.startsWith(" ") ? rest.substring(1) : rest);
        });
    }

    // Selects the next match after the cursor, wrapping round to the top, ignoring upper and lower
    // case. Returns false if the text isn't there.
    public boolean find(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        String all = editor.getText().toLowerCase(Locale.ROOT);
        String wanted = text.toLowerCase(Locale.ROOT);
        int found = all.indexOf(wanted, editor.getSelectionEnd());
        if (found < 0) {
            found = all.indexOf(wanted);
        }
        if (found < 0) {
            return false;
        }
        editor.requestFocusInWindow();
        editor.select(found, found + wanted.length());
        return true;
    }

    private String textBeforeCaretOnLine() {
        try {
            int caret = editor.getSelectionStart();
            int start = editor.getLineStartOffset(editor.getLineOfOffset(caret));
            return editor.getText(start, caret - start);
        } catch (BadLocationException e) {
            return "";
        }
    }

    private int[] selectedRange() throws BadLocationException {
        int end = editor.getSelectionEnd();
        if (end > editor.getSelectionStart() && editor.getText(end - 1, 1).equals("\n")) {
            end--; // a selection ending at the very start of a line doesn't include that line
        }
        int first = editor.getLineStartOffset(editor.getLineOfOffset(editor.getSelectionStart()));
        int lastLine = editor.getLineOfOffset(end);
        int last = editor.getLineEndOffset(lastLine);
        if (lastLine < editor.getLineCount() - 1) {
            last--; // leave the line's own newline out
        }
        return new int[] {first, last};
    }

    private List<String> selectedLines() {
        try {
            int[] range = selectedRange();
            return List.of(editor.getText(range[0], range[1] - range[0]).split("\n", -1));
        } catch (BadLocationException e) {
            return List.of();
        }
    }

    // Applies a change to each selected line in one edit, so one undo reverses all of it.
    private void changeLines(UnaryOperator<String> change) {
        try {
            int[] range = selectedRange();
            boolean hadSelection = editor.getSelectedText() != null;
            List<String> changed = new ArrayList<>();
            for (String line : editor.getText(range[0], range[1] - range[0]).split("\n", -1)) {
                changed.add(change.apply(line));
            }
            String text = String.join("\n", changed);
            editor.replaceRange(text, range[0], range[1]);
            if (hadSelection) {
                editor.select(range[0], range[0] + text.length());
            }
        } catch (BadLocationException e) {
            // the text changed underneath us; nothing to do
        }
    }
}
