package raphael.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
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
    private final JTree tree = new JTree(new DefaultMutableTreeNode());
    private final JLabel empty = new JLabel();
    private final JPanel switcher = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    private final List<JToggleButton> languageButtons = new ArrayList<>();
    private Language language = Languages.preferred();

    public ExamplesPanel(Actions actions) {
        super(new BorderLayout());
        this.actions = actions;
        ButtonGroup group = new ButtonGroup();
        for (Language each : Languages.all()) {
            JToggleButton button = new JToggleButton(each.name());
            button.putClientProperty("JButton.buttonType", "toolBarButton");
            button.setFocusable(false);
            button.setToolTipText(each.name() + " examples");
            button.addActionListener(e -> showLanguage(each));
            group.add(button);
            switcher.add(button);
            languageButtons.add(button);
        }
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
        top.setBorder(BorderFactory.createEmptyBorder(Style.SPACE_2, Style.SPACE_2, Style.SPACE_2, Style.SPACE_2));
        top.add(switcher, BorderLayout.NORTH);
        top.add(search, BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);

        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setRowHeight(Style.PALETTE_ROW_H - 4);
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
        empty.setVerticalAlignment(JLabel.TOP);
        empty.setBorder(BorderFactory.createEmptyBorder(Style.SPACE_3, Style.SPACE_3, Style.SPACE_3, Style.SPACE_3));
        middle.add(empty, BorderLayout.NORTH);
        add(middle, BorderLayout.CENTER);
        showLanguage(language);
    }

    // Shows one language's examples (the switch follows).
    public void showLanguage(Language chosen) {
        language = chosen;
        for (int i = 0; i < languageButtons.size(); i++) {
            languageButtons.get(i).setSelected(Languages.all().get(i) == chosen);
        }
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
        empty.setVisible(none);
        empty.setText(none ? "<html>" + (wanted.isEmpty() ? "No " + language.name() + " examples yet: they come when Raphael can run "
                + language.name() + "." : "No " + language.name() + " example matches.") + "</html>" : "");
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
                setFont(Style.sans(false, 13f));
            } else if (node.getLevel() == 1) {
                setText(String.valueOf(node.getUserObject()).toUpperCase(Locale.ROOT));
                setFont(Style.sans(true, 10.5f));
                if (!selected) {
                    setForeground(Style.MUTED);
                }
            } else {
                setText(String.valueOf(node.getUserObject()));
                setFont(Style.sans(node.getLevel() == 2, 13f));
            }
            return this;
        }
    }

    // ---- for tests ----

    JComponent tree() {
        return tree;
    }
}
