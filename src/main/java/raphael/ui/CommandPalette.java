package raphael.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import raphael.render.Style;

// The command palette (Ctrl+Shift+P) and Quick Open (Ctrl+P), as in VS Code: a box at the top of
// the window with a search field and a list that narrows as you type. Typing "opfo" finds
// "Open Folder…": the letters only have to come in order. Enter runs the chosen entry, Esc closes.
// The palette's entries are the menu bar's own commands, read from it each time, so every
// command is listed once, with its keys, and switched-off commands are left out.
// OOP: inheritance (extends JDialog); a record for an entry, holding its action as a Runnable
// (a function passed around as an object).
public class CommandPalette extends JDialog {

    // One line of the list: what it is, a detail on the right (keys, a path), and what it does.
    public record Entry(String label, String detail, Runnable action) {
    }

    private final List<Entry> entries;
    private final JTextField search = new JTextField();
    private final DefaultListModel<Entry> shown = new DefaultListModel<>();
    private final JList<Entry> list = new JList<>(shown);

    private CommandPalette(Window owner, String placeholder, List<Entry> entries) {
        super(owner, ModalityType.APPLICATION_MODAL);
        this.entries = entries;
        setUndecorated(true);
        search.putClientProperty("JTextField.placeholderText", placeholder);
        Zoom.font(search, Style.sans(false, 14f));
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new EntryRenderer());
        list.setFocusable(false);
        Zoom.rowHeight(list, Style.PALETTE_ROW_H);
        list.setBackground(Style.BAR);
        list.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) { // the row under the mouse is the chosen one, as in VS Code
                int index = list.locationToIndex(e.getPoint());
                if (index >= 0 && index != list.getSelectedIndex()) {
                    list.setSelectedIndex(index);
                }
            }
        });
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int index = list.locationToIndex(e.getPoint());
                if (index >= 0) {
                    list.setSelectedIndex(index);
                    runChosen();
                }
            }
        });
        JPanel content = new JPanel(new BorderLayout(0, Style.SPACE_2));
        content.setBackground(Style.BAR); // a sheet lifted off the window behind it
        content.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Style.CONTROL_BORDER),
                Zoom.gap(Style.SPACE_2, Style.SPACE_2, Style.SPACE_2, Style.SPACE_2)));
        content.add(search, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(Style.BAR);
        Zoom.size(scroll, Style.PALETTE_W, 320);
        content.add(scroll, BorderLayout.CENTER);
        setContentPane(content);
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                filter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                filter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
            }
        });
        key("ESCAPE", this::dispose);
        key("ENTER", this::runChosen);
        key("DOWN", () -> move(1));
        key("UP", () -> move(-1));
        key("PAGE_DOWN", () -> move(8));
        key("PAGE_UP", () -> move(-8));
        filter();
        pack();
        // At the top of the window, in the middle, as in VS Code.
        setLocation(owner.getX() + (owner.getWidth() - getWidth()) / 2, owner.getY() + 80);
    }

    // The palette: every switched-on command in the menu bar, as "Menu: Command", with its keys.
    public static void showCommands(Window owner, JMenuBar menus) {
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < menus.getMenuCount(); i++) {
            JMenu menu = menus.getMenu(i);
            if (menu != null) {
                collect(menu, menu.getText(), entries);
            }
        }
        new CommandPalette(owner, "Type a command", entries).setVisible(true);
    }

    // Quick Open: the entries given (files, recent files, examples).
    public static void showEntries(Window owner, String placeholder, List<Entry> entries) {
        new CommandPalette(owner, placeholder, entries).setVisible(true);
    }

    private static void collect(JMenu menu, String path, List<Entry> into) {
        for (Component part : menu.getMenuComponents()) {
            if (part instanceof JMenu) {
                JMenu inner = (JMenu) part;
                if (inner.getItemCount() <= 12) { // a short submenu (not the 100 examples, which Quick Open has)
                    collect(inner, path + ": " + inner.getText(), into);
                }
            } else if (part instanceof JMenuItem && part.isEnabled()) {
                JMenuItem item = (JMenuItem) part;
                String keys = item.getAccelerator() == null ? "" : keysOf(item.getAccelerator());
                into.add(new Entry(path + ": " + item.getText(), keys, item::doClick));
            }
        }
    }

    // A key as text, in the computer's own words ("Ctrl+Shift+O", or ⇧⌘O on a Mac).
    private static String keysOf(javax.swing.KeyStroke key) {
        String modifiers = java.awt.event.InputEvent.getModifiersExText(key.getModifiers());
        return (modifiers.isEmpty() ? "" : modifiers + "+") + java.awt.event.KeyEvent.getKeyText(key.getKeyCode());
    }

    // How well an entry matches what was typed: the letters must appear in order, and runs of
    // letters next to each other and at the start of words count more. -1 if they don't match.
    static int score(String label, String typed) {
        if (typed.isBlank()) {
            return 0;
        }
        String text = label.toLowerCase(Locale.ROOT);
        String wanted = typed.toLowerCase(Locale.ROOT).replace(" ", "");
        int score = 0;
        int at = 0;
        int previous = -2;
        for (char c : wanted.toCharArray()) {
            int found = text.indexOf(c, at);
            if (found < 0) {
                return -1;
            }
            score += found == previous + 1 ? 5 : 1; // letters together
            if (found == 0 || !Character.isLetterOrDigit(text.charAt(found - 1))) {
                score += 3; // the start of a word
            }
            previous = found;
            at = found + 1;
        }
        return score;
    }

    private void filter() {
        String typed = search.getText();
        if (typed.isBlank()) { // nothing typed yet: everything, in the menus' own order
            shown.clear();
            for (Entry entry : entries.subList(0, Math.min(entries.size(), 200))) {
                shown.addElement(entry);
            }
            if (!shown.isEmpty()) {
                list.setSelectedIndex(0);
            }
            return;
        }
        List<Entry> matches = new ArrayList<>();
        List<Integer> scores = new ArrayList<>();
        for (Entry entry : entries) {
            int score = score(entry.label(), typed);
            if (score >= 0) {
                int at = 0; // sorted by score, best first; among equal scores the shorter label first
                while (at < scores.size() && (scores.get(at) > score
                        || (scores.get(at) == score && matches.get(at).label().length() <= entry.label().length()))) {
                    at++;
                }
                matches.add(at, entry);
                scores.add(at, score);
            }
        }
        shown.clear();
        for (Entry entry : matches.subList(0, Math.min(matches.size(), 200))) {
            shown.addElement(entry);
        }
        if (!shown.isEmpty()) {
            list.setSelectedIndex(0);
        }
    }

    private void move(int by) {
        if (shown.isEmpty()) {
            return;
        }
        int next = Math.max(0, Math.min(shown.size() - 1, list.getSelectedIndex() + by));
        list.setSelectedIndex(next);
        list.ensureIndexIsVisible(next);
    }

    private void runChosen() {
        Entry chosen = list.getSelectedValue();
        dispose();
        if (chosen != null) {
            chosen.action().run();
        }
    }

    private void key(String key, Runnable action) {
        search.getInputMap(JComponent.WHEN_FOCUSED).put(Shortcuts.key(key), key);
        search.getActionMap().put(key, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    // For the tests: the labels shown after typing.
    List<String> shownFor(String typed) {
        search.setText(typed);
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < shown.size(); i++) {
            labels.add(shown.get(i).label());
        }
        return labels;
    }

    static CommandPalette forTests(Window owner, List<Entry> entries) {
        return new CommandPalette(owner, "", entries);
    }

    // One line: the label on the left, the detail (keys, a path) muted on the right.
    private static final class EntryRenderer implements ListCellRenderer<Entry> {
        private final JPanel row = new JPanel(new BorderLayout(Style.SPACE_3, 0));
        private final JLabel category = new JLabel(); // "File", muted, so each command starts in the same place
        private final JLabel label = new JLabel();
        private final JLabel detail = new JLabel();

        EntryRenderer() {
            row.setBorder(Zoom.gap(0, Style.SPACE_2, 0, Style.SPACE_2));
            Zoom.font(category, Style.sans(false, 12f));
            category.setPreferredSize(new Dimension(Zoom.px(56), 1));
            Zoom.font(label, Style.sans(false, 13f));
            Zoom.font(detail, Style.sans(false, 12f));
            row.add(category, BorderLayout.WEST);
            row.add(label, BorderLayout.CENTER);
            row.add(detail, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Entry> list, Entry entry, int index,
                                                      boolean selected, boolean focused) {
            String text = entry.label();
            int colon = text.indexOf(": ");
            boolean command = colon > 0 && colon < 12; // "File: Open…" (a menu command), not a file name
            category.setVisible(command);
            category.setText(command ? text.substring(0, colon) : "");
            label.setText(command ? text.substring(colon + 2) : text);
            detail.setText(entry.detail());
            row.setBackground(selected ? Style.ACCENT_TINT : Style.BAR);
            category.setForeground(Style.MUTED);
            label.setForeground(Style.INK);
            detail.setForeground(Style.MUTED);
            return row;
        }
    }
}
