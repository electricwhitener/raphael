package raphael.ui;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import javax.swing.AbstractAction;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.text.BadLocationException;

// The small things a code editor does for you: Enter keeps the indentation, Tab and Shift+Tab
// move lines in and out, "}" lines up with its block, Ctrl+/ comments lines, and find.
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
        bind("typed }", this::closeBrace);
        bind("control SLASH", this::toggleComment);
    }

    private void bind(String key, Runnable action) {
        editor.getInputMap().put(KeyStroke.getKeyStroke(key), key);
        editor.getActionMap().put(key, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (editor.isEditable()) {
                    action.run();
                }
            }
        });
    }

    // Starts a new line at the same indentation, one level deeper after a "{".
    void newLine() {
        String before = textBeforeCaretOnLine();
        String indent = before.substring(0, before.length() - before.stripLeading().length());
        editor.replaceSelection("\n" + indent + (before.stripTrailing().endsWith("{") ? INDENT : ""));
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
        String all = editor.getText().toLowerCase();
        String wanted = text.toLowerCase();
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
