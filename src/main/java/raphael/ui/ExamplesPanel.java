package raphael.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.ToolTipManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import raphael.io.ExampleCatalog;
import raphael.lang.Language;
import raphael.lang.Languages;
import raphael.render.Style;

// The examples side bar: a switch for the language, a search field, and a tree of the language's
// examples: title (Data structures), category (Trees), subcategory (Red-black tree), then the
// cases. One click on a case previews it (in a tab the next click reuses); a double click, or
// Enter, opens it to keep. The example on screen is selected in the tree.
// OOP: inheritance (extends JPanel); composition (it's made of a switch, a field and a tree);
// an interface (Actions) is how it asks the window to open an example, so it knows nothing about
// tabs; a nested renderer class (inheritance, overriding).
public class ExamplesPanel extends JPanel {

    // What the panel asks of the window.
    public interface Actions {
        void preview(ExampleCatalog.Example example);

        void open(ExampleCatalog.Example example);
    }

    private final Actions actions;
    private final JTextField search = new JTextField();
    private final JTree tree = new JTree(new DefaultMutableTreeNode()) {
        @Override
        public void updateUI() { // also when the theme changes, which makes a new UI
            super.updateUI();
            if (getUI() instanceof javax.swing.plaf.basic.BasicTreeUI) {
                ((javax.swing.plaf.basic.BasicTreeUI) getUI()).setLeftChildIndent(8);
                ((javax.swing.plaf.basic.BasicTreeUI) getUI()).setRightChildIndent(12);
            }
        }
    };
    private final JLabel empty = new JLabel();
    private final Segmented switcher = new Segmented(Languages.all().stream().map(Language::name).toList(),
            index -> showLanguage(Languages.all().get(index)));
    private final JPanel emptyBox = new JPanel();
    private Language language = Languages.preferred();

    public ExamplesPanel(Actions actions) {
        super(new BorderLayout());
        this.actions = actions;
        switcher.describe("examples");
        search.putClientProperty("JTextField.placeholderText", "Search examples");
        search.putClientProperty("JTextField.showClearButton", true);
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                fill();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                fill();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                fill();
            }
        });
        // Down from the search field goes into the tree, to the first match.
        search.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "intoTree");
        search.getActionMap().put("intoTree", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                tree.requestFocusInWindow();
                if (tree.getRowCount() > 0 && tree.getSelectionCount() == 0) {
                    tree.setSelectionRow(0);
                }
            }
        });
        JPanel top = new JPanel(new BorderLayout(0, Style.SPACE_2));
        top.setOpaque(false);
        top.setBorder(Zoom.gap(Style.SPACE_2, Style.SPACE_2, Style.SPACE_2, Style.SPACE_2));
        top.add(switcher, BorderLayout.NORTH);
        top.add(search, BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);

        // Four levels that each look different: the steps in from the left are small, so the
        // case titles get the room.
        tree.putClientProperty("FlatLaf.style",
                "selectionInactiveBackground:$Tree.selectionBackground;selectionInactiveForeground:$Tree.selectionForeground");
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setToggleClickCount(1); // a click anywhere on a group's row opens or closes it, not only on its arrow
        Zoom.rowHeight(tree, Style.PALETTE_ROW_H - 4);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.setCellRenderer(new Renderer());
        ToolTipManager.sharedInstance().registerComponent(tree);
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                ExampleCatalog.Example example = path == null ? null : exampleOf(path.getLastPathComponent());
                if (example != null && e.getButton() == MouseEvent.BUTTON1) {
                    if (e.getClickCount() >= 2) {
                        actions.open(example);
                    } else {
                        actions.preview(example);
                    }
                }
            }
        });
        tree.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "keep");
        tree.getActionMap().put("keep", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                ExampleCatalog.Example example = selectedExample();
                if (example != null) {
                    actions.open(example);
                }
            }
        });
        tree.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "preview");
        tree.getActionMap().put("preview", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                ExampleCatalog.Example example = selectedExample();
                if (example != null) {
                    actions.preview(example);
                }
            }
        });
        JScrollPane scroll = new JScrollPane(tree);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        JPanel middle = new JPanel(new BorderLayout());
        middle.setOpaque(false);
        middle.add(scroll, BorderLayout.CENTER);
        // A language with no examples yet: say so, and offer Java's.
        emptyBox.setOpaque(false);
        emptyBox.setLayout(new javax.swing.BoxLayout(emptyBox, javax.swing.BoxLayout.Y_AXIS));
        emptyBox.setBorder(Zoom.gap(Style.SPACE_3, Style.SPACE_3, Style.SPACE_3, Style.SPACE_3));
        empty.setAlignmentX(Component.LEFT_ALIGNMENT);
        emptyBox.add(empty);
        javax.swing.JButton showJava = new javax.swing.JButton("Show Java examples");
        showJava.putClientProperty("JButton.buttonType", "borderless");
        showJava.setForeground(Style.ACCENT);
        showJava.setAlignmentX(Component.LEFT_ALIGNMENT);
        showJava.addActionListener(e -> showLanguage(Languages.JAVA));
        emptyBox.add(showJava);
        middle.add(emptyBox, BorderLayout.NORTH);
        add(middle, BorderLayout.CENTER);
        showLanguage(language);
    }

    // Shows one language's examples (the switch follows).
    public void showLanguage(Language chosen) {
        language = chosen;
        switcher.choose(Languages.all().indexOf(chosen));
        fill();
    }

    public Language getLanguage() {
        return language;
    }

    // The keyboard goes to the search field, ready to type.
    public void focusSearch() {
        search.requestFocusInWindow();
        search.selectAll();
    }

    // Selects this example in the tree (the one on screen), switching language if it must.
    public void select(ExampleCatalog.Example example) {
        if (example == null) {
            tree.clearSelection();
            return;
        }
        if (example.language() != language) {
            showLanguage(example.language());
        }
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
        Enumeration<javax.swing.tree.TreeNode> all = root.depthFirstEnumeration();
        while (all.hasMoreElements()) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) all.nextElement();
            if (example.equals(node.getUserObject())) {
                TreePath path = new TreePath(node.getPath());
                tree.setSelectionPath(path);
                tree.scrollPathToVisible(path);
                return;
            }
        }
    }

    // How many cases are shown now (for tests).
    int shownCases() {
        int count = 0;
        Enumeration<javax.swing.tree.TreeNode> all = ((DefaultMutableTreeNode) tree.getModel().getRoot()).depthFirstEnumeration();
        while (all.hasMoreElements()) {
            if (exampleOf(all.nextElement()) != null) {
                count++;
            }
        }
        return count;
    }

    void setSearch(String text) {
        search.setText(text);
    }

    // The tree for the language and the search: every case whose title, blurb, subcategory or
    // category has the searched words. While searching, everything that matches is open.
    private void fill() {
        String wanted = search.getText().strip().toLowerCase(Locale.ROOT);
        DefaultMutableTreeNode root = new DefaultMutableTreeNode();
        for (ExampleCatalog.Group group : ExampleCatalog.groups(language)) {
            DefaultMutableTreeNode title = child(root, group.section());
            DefaultMutableTreeNode category = new DefaultMutableTreeNode(group.name());
            for (ExampleCatalog.Example example : group.examples()) {
                String text = (group.name() + " " + example.subcategory() + " " + example.title() + " " + example.blurb())
                        .toLowerCase(Locale.ROOT);
                if (!wanted.isEmpty() && !matchesAll(text, wanted)) {
                    continue;
                }
                DefaultMutableTreeNode under = example.subcategory() == null ? category : child(category, example.subcategory());
                under.add(new DefaultMutableTreeNode(example));
            }
            if (category.getChildCount() > 0) {
                title.add(category);
            }
        }
        for (int i = root.getChildCount() - 1; i >= 0; i--) {
            if (root.getChildAt(i).getChildCount() == 0) {
                root.remove(i);
            }
        }
        tree.setModel(new DefaultTreeModel(root));
        if (wanted.isEmpty()) {
            for (int row = 0; row < tree.getRowCount(); row++) {
                if (((DefaultMutableTreeNode) tree.getPathForRow(row).getLastPathComponent()).getLevel() == 1) {
                    tree.expandRow(row); // the titles open, their categories closed
                }
            }
        } else {
            for (int row = 0; row < tree.getRowCount(); row++) {
                tree.expandRow(row);
            }
        }
        boolean none = root.getChildCount() == 0;
        emptyBox.setVisible(none);
        search.setEnabled(!none || !wanted.isEmpty());
        empty.setText(none ? "<html><b>" + (wanted.isEmpty() ? "No " + language.name() + " examples yet" : "No " + language.name()
                + " example matches") + "</b><br>" + (wanted.isEmpty() ? "They're coming. For now you can write and save "
                + language.name() + " programs." : "Try fewer words.") + "</html>" : "");
    }

    private static boolean matchesAll(String text, String wanted) {
        for (String word : wanted.split("\\s+")) {
            if (!text.contains(word)) {
                return false;
            }
        }
        return true;
    }

    // The child of this node with this name, made if it isn't there yet.
    private static DefaultMutableTreeNode child(DefaultMutableTreeNode parent, String name) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) parent.getChildAt(i);
            if (name.equals(child.getUserObject())) {
                return child;
            }
        }
        DefaultMutableTreeNode made = new DefaultMutableTreeNode(name);
        parent.add(made);
        return made;
    }

    private ExampleCatalog.Example selectedExample() {
        TreePath path = tree.getSelectionPath();
        return path == null ? null : exampleOf(path.getLastPathComponent());
    }

    private static ExampleCatalog.Example exampleOf(Object node) {
        Object value = node instanceof DefaultMutableTreeNode ? ((DefaultMutableTreeNode) node).getUserObject() : null;
        return value instanceof ExampleCatalog.Example ? (ExampleCatalog.Example) value : null;
    }

    // Titles small and muted (like the panel headings elsewhere), categories in the normal
    // weight, cases by their title with their sentence as the tooltip.
    private static final class Renderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded,
                                                      boolean leaf, int row, boolean focused) {
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, focused);
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
            ExampleCatalog.Example example = exampleOf(node);
            setIcon(null);
            setToolTipText(null);
            if (example != null) {
                setText(example.title());
                setToolTipText(example.blurb());
                Zoom.font(this, Style.sans(false, 12.5f));
            } else if (node.getLevel() == 1) {
                setText(String.valueOf(node.getUserObject()).toUpperCase(Locale.ROOT));
                Zoom.font(this, Style.sans(true, 10.5f));
                if (!selected) {
                    setForeground(Style.MUTED);
                }
            } else if (node.getLevel() == 2) { // a category
                setText(String.valueOf(node.getUserObject()));
                Zoom.font(this, Style.sans(true, 13f));
            } else { // a subcategory: lighter than its category, so the levels step down
                setText(String.valueOf(node.getUserObject()));
                Zoom.font(this, Style.sans(false, 13f));
                if (!selected) {
                    setForeground(Style.INK_SOFT);
                }
            }
            return this;
        }
    }

    // ---- for tests ----

    JComponent tree() {
        return tree;
    }
}
