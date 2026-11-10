package raphael.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import raphael.render.Style;

// The find (and replace) bar at the top of the editor, as in an IDE: Ctrl+F opens it, Ctrl+H
// opens it with the replace row. Every match is highlighted, the bar says "3 of 7", Enter and F3
// go to the next match, Shift+Enter and Shift+F3 to the one before, and Esc closes it.
// Match case, whole word and regular expressions can be switched on.
// OOP: inheritance (extends JPanel); composition (it works on the editor it's given); the
// matching is one private method that the counting, the moving and the replacing all use.
public class FindBar extends JPanel {

    // How a whole replace-all is made one edit, so one undo takes it all back.
    public interface Edits {
        void asOne(Runnable change);
    }

    private final JTextArea editor;
    private final Edits edits;
    private final JTextField find = new JTextField(18);
    private final JTextField replace = new JTextField(18);
    private final JLabel count = new JLabel();
    private final JToggleButton matchCase = toggle("Aa", "Match case");
    private final JToggleButton wholeWord = toggle("W", "Whole word");
    private final JToggleButton regex = toggle(".*", "Regular expression");
    private final JPanel replaceRow = new JPanel(new FlowLayout(FlowLayout.LEFT, Style.SPACE_1, 0));
    private final List<Object> marks = new ArrayList<>();
    private final DefaultHighlighter.DefaultHighlightPainter painter =
            new DefaultHighlighter.DefaultHighlightPainter(null) {
                @Override
                public Color getColor() {
                    return Style.PRESSED; // grey, so the selected match (in the selection's blue) stands out; asked each time for dark mode
                }
            };

    public FindBar(JTextArea editor, Edits edits) {
        super(new BorderLayout());
        this.editor = editor;
        this.edits = edits;
        setBorder(BorderFactory.createCompoundBorder(Section.hairline(0, 0, 1, 0),
                BorderFactory.createEmptyBorder(Style.SPACE_1, Style.SPACE_2, Style.SPACE_1, Style.SPACE_2)));
        JPanel findRow = new JPanel(new FlowLayout(FlowLayout.LEFT, Style.SPACE_1, 0));
        findRow.setOpaque(false);
        replaceRow.setOpaque(false);
        find.putClientProperty("JTextField.placeholderText", "Find");
        replace.putClientProperty("JTextField.placeholderText", "Replace");
        count.setFont(Style.sans(false, 12f));
        count.setForeground(Style.MUTED);
        count.setPreferredSize(new Dimension(84, count.getFontMetrics(count.getFont()).getHeight())); // as tall when empty
        JButton previous = small("↑", "Previous match  (Shift+Enter)", () -> next(true));
        JButton following = small("↓", "Next match  (Enter)", () -> next(false));
        JButton close = small("×", "Close  (Esc)", this::close);
        findRow.add(find);
        findRow.add(matchCase);
        findRow.add(wholeWord);
        findRow.add(regex);
        findRow.add(count);
        findRow.add(previous);
        findRow.add(following);
        replaceRow.add(replace);
        replaceRow.add(small("Replace", "Replace this match", this::replaceOne));
        replaceRow.add(small("All", "Replace every match", this::replaceAll));
        JPanel rows = new JPanel(new BorderLayout(0, Style.SPACE_1));
        rows.setOpaque(false);
        rows.add(findRow, BorderLayout.NORTH);
        rows.add(replaceRow, BorderLayout.SOUTH);
        add(rows, BorderLayout.CENTER);
        add(close, BorderLayout.EAST);
        for (JToggleButton option : new JToggleButton[] {matchCase, wholeWord, regex}) {
            option.addActionListener(e -> refresh());
        }
        find.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                typed();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                typed();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
            }
        });
        key(find, "ENTER", () -> next(false));
        key(find, "shift ENTER", () -> next(true));
        key(replace, "ENTER", this::replaceOne);
        for (JComponent field : new JComponent[] {find, replace}) {
            key(field, "ESCAPE", this::close);
        }
        setVisible(false);
    }

    // Opens the bar, with the replace row if asked, starting from the selected text if there is one.
    public void open(boolean withReplace) {
        String selected = editor.getSelectedText();
        if (selected != null && !selected.contains("\n")) {
            find.setText(selected);
        }
        replaceRow.setVisible(withReplace);
        setVisible(true);
        revalidate();
        find.requestFocusInWindow();
        find.selectAll();
        refresh();
    }

    public void close() {
        clearMarks();
        setVisible(false);
        editor.requestFocusInWindow();
    }

    // F3 and Shift+F3: the next or previous match; with the bar closed, it opens first.
    public void next(boolean backwards) {
        if (!isVisible() || find.getText().isEmpty()) {
            open(replaceRow.isVisible() && isVisible());
            if (find.getText().isEmpty()) {
                return;
            }
        }
        List<int[]> all = matches();
        if (all.isEmpty()) {
            refresh();
            return;
        }
        int from = backwards ? editor.getSelectionStart() : editor.getSelectionEnd();
        int[] chosen = backwards ? all.get(all.size() - 1) : all.get(0); // wrap round
        for (int i = 0; i < all.size(); i++) {
            int[] match = all.get(backwards ? all.size() - 1 - i : i);
            if (backwards ? match[1] < from || (match[0] < from && match[1] <= from) : match[0] >= from) {
                chosen = match;
                break;
            }
        }
        editor.select(chosen[0], chosen[1]);
        refresh();
    }

    // The text or the code changed (or another file is shown): the highlights and the count follow.
    public void refresh() {
        clearMarks();
        if (!isVisible()) {
            return;
        }
        List<int[]> all;
        try {
            all = matches();
            find.putClientProperty("JComponent.outline", null);
        } catch (PatternSyntaxException e) {
            find.putClientProperty("JComponent.outline", "error");
            count.setText("bad pattern");
            return;
        }
        int at = 0;
        for (int i = 0; i < all.size(); i++) {
            int[] match = all.get(i);
            if (match[0] == editor.getSelectionStart() && match[1] == editor.getSelectionEnd()) {
                at = i + 1;
                continue; // the selected match shows in the selection's colour; a mark would paint over it
            }
            try {
                marks.add(editor.getHighlighter().addHighlight(match[0], match[1], painter));
            } catch (BadLocationException e) {
                // the text changed while drawing; the next refresh fixes it
            }
        }
        editor.getCaret().setSelectionVisible(true); // the editor hides its selection while the keyboard is here
        count.setText(find.getText().isEmpty() ? "" : all.isEmpty() ? "no matches"
                : (at > 0 ? at + " of " : "") + all.size() + (at > 0 ? "" : all.size() == 1 ? " match" : " matches"));
    }

    private void typed() {
        // Find as you type: the first match from where the selection starts.
        List<int[]> all;
        try {
            all = matches();
        } catch (PatternSyntaxException e) {
            refresh();
            return;
        }
        for (int[] match : all) {
            if (match[0] >= editor.getSelectionStart()) {
                editor.select(match[0], match[1]);
                break;
            }
        }
        refresh();
    }

    private void replaceOne() {
        if (!editor.isEditable()) {
            return;
        }
        for (int[] match : matches()) {
            if (match[0] == editor.getSelectionStart() && match[1] == editor.getSelectionEnd()) {
                editor.replaceSelection(replacementFor(editor.getSelectedText()));
                break;
            }
        }
        next(false);
    }

    private void replaceAll() {
        if (!editor.isEditable()) {
            return;
        }
        List<int[]> all = matches();
        if (all.isEmpty()) {
            return;
        }
        edits.asOne(() -> {
            for (int i = all.size() - 1; i >= 0; i--) { // from the end, so earlier positions stay right
                int[] match = all.get(i);
                editor.select(match[0], match[1]);
                editor.replaceSelection(replacementFor(editor.getSelectedText()));
            }
        });
        count.setText("replaced " + all.size());
        clearMarks();
    }

    // What one match becomes: the replace text, with $1 and the like for a regular expression.
    private String replacementFor(String matched) {
        if (!regex.isSelected()) {
            return replace.getText();
        }
        Matcher m = pattern().matcher(matched);
        return m.matches() ? m.replaceFirst(replace.getText()) : replace.getText();
    }

    // Every match in the code as [start, end], in order.
    private List<int[]> matches() {
        List<int[]> found = new ArrayList<>();
        if (find.getText().isEmpty()) {
            return found;
        }
        Matcher m = pattern().matcher(editor.getText());
        while (m.find() && found.size() < 10_000) {
            if (m.end() > m.start()) {
                found.add(new int[] {m.start(), m.end()});
            }
        }
        return found;
    }

    private Pattern pattern() {
        String text = regex.isSelected() ? find.getText() : Pattern.quote(find.getText());
        if (wholeWord.isSelected()) {
            text = "\\b" + text + "\\b";
        }
        return Pattern.compile(text, matchCase.isSelected() ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    private void clearMarks() {
        for (Object mark : marks) {
            editor.getHighlighter().removeHighlight(mark);
        }
        marks.clear();
    }

    private static JToggleButton toggle(String text, String tip) {
        JToggleButton button = new JToggleButton(text);
        button.setToolTipText(tip);
        button.getAccessibleContext().setAccessibleName(tip);
        button.putClientProperty("JButton.buttonType", "toolBarButton");
        button.setFont(Style.CODE_FONT.deriveFont(12f));
        button.setFocusable(false);
        return button;
    }

    private static JButton small(String text, String tip, Runnable action) {
        JButton button = new JButton(text);
        button.setToolTipText(Shortcuts.label(tip));
        button.getAccessibleContext().setAccessibleName(tip);
        button.putClientProperty("JButton.buttonType", "toolBarButton");
        button.setFocusable(false);
        button.addActionListener(e -> action.run());
        return button;
    }

    private static void key(JComponent field, String key, Runnable action) {
        field.getInputMap().put(Shortcuts.key(key), key);
        field.getActionMap().put(key, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    // For the tests.
    JTextField findField() {
        return find;
    }

    JTextField replaceField() {
        return replace;
    }

    String countText() {
        return count.getText();
    }
}
