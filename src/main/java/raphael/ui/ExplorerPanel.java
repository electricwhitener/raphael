package raphael.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import raphael.render.Style;

// The Explorer: the files of a program of several files (a folder) as a tree of its folders, as
// in VS Code, under the folder's name. A click on a file shows it in the editor; Enter, a double
// click or Esc go on into the code. The file in the editor is the one chosen; an unsaved one has a dot.
// OOP: inheritance (extends JPanel); composition (a RowTree in a scroll pane); a Consumer passed
// in is how it asks for a file to be shown, so it knows nothing about tabs or editors; a nested
// node class (encapsulation: a file's path, or a folder's name).
public class ExplorerPanel extends JPanel {

    // A row of the tree: a folder (path null) or a file (its path in the program, "shapes/Circle.java").
    private record Entry(String name, String path) {
        @Override
        public String toString() {
            return name;
        }
    }

    private final RowTree tree = new RowTree(node -> pathOf(node) != null); // a file opens; a folder only folds
    private final javax.swing.JLabel heading = Section.header("");
    private final DefaultMutableTreeNode root = new DefaultMutableTreeNode();
    private final DefaultTreeModel model = new DefaultTreeModel(root);
    private List<String> shownPaths = List.of();
    private String shownFolder = "";
    private boolean choosing; // the tree is being set to the file in the editor: not a click

    // show: put a file in the editor; intoCode: give the keyboard to the editor; unsaved: whether a file has unsaved changes.
    public ExplorerPanel(Consumer<String> show, Runnable intoCode, java.util.function.Predicate<String> unsaved) {
        super(new BorderLayout());
        setBackground(Style.BACKGROUND);
        tree.setModel(model);
        tree.setRootVisible(false); // the folder's name is the heading above, so the files start at the left
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        Zoom.rowHeight(tree, Style.PALETTE_ROW_H - 4);
        tree.setCellRenderer(new Renderer(unsaved));
        Runnable openSelected = () -> {
            String path = tree.getSelectionPath() == null ? null : pathOf(tree.getSelectionPath().getLastPathComponent());
            if (path != null) {
                show.accept(path);
                intoCode.run();
            }
        };
        for (String key : new String[] {"ENTER", "ESCAPE"}) {
            tree.getInputMap().put(javax.swing.KeyStroke.getKeyStroke(key), "intoCode");
        }
        tree.getActionMap().put("intoCode", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                openSelected.run();
            }
        });
        tree.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && tree.getPathForLocation(e.getX(), e.getY()) != null) {
                    openSelected.run();
                }
            }
        });
        tree.addTreeSelectionListener(e -> {
            String path = e.getPath() == null ? null : pathOf(e.getPath().getLastPathComponent());
            if (!choosing && path != null) {
                show.accept(path);
            }
        });
        JScrollPane scroll = new JScrollPane(tree);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setOpaque(false);
        scroll.setOpaque(false);
        add(heading, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
    }

    // The program's files: the folder's name and the paths of its files ("shapes/Circle.java"),
    // and the one in the editor. Built again only when the files change.
    public void show(String folderName, List<String> paths, String shown) {
        if (!paths.equals(shownPaths) || !folderName.equals(shownFolder)) {
            shownPaths = List.copyOf(paths);
            shownFolder = folderName;
            heading.setText(folderName.toUpperCase(java.util.Locale.ROOT));
            heading.setToolTipText(folderName);
            root.removeAllChildren();
            root.setUserObject(new Entry(folderName, null));
            List<String> sorted = paths.stream().sorted(ExplorerPanel::foldersFirst).toList();
            for (String path : sorted) {
                add(path);
            }
            model.reload();
            for (int row = 0; row < tree.getRowCount(); row++) {
                tree.expandRow(row); // every folder open: a student's program has few files
            }
        }
        choose(shown);
        tree.repaint(); // the unsaved dots may have changed
    }

    // The file in the editor is the chosen row.
    public void choose(String path) {
        choosing = true;
        try {
            DefaultMutableTreeNode node = find(root, path);
            if (node == null) {
                tree.clearSelection();
            } else {
                TreePath at = new TreePath(node.getPath());
                tree.setSelectionPath(at);
                tree.scrollPathToVisible(at);
            }
        } finally {
            choosing = false;
        }
    }

    // A file under its folders, making the folders that aren't there yet.
    private void add(String path) {
        String[] parts = path.split("/");
        DefaultMutableTreeNode at = root;
        for (int i = 0; i < parts.length - 1; i++) {
            DefaultMutableTreeNode folder = null;
            for (int k = 0; k < at.getChildCount(); k++) {
                DefaultMutableTreeNode child = (DefaultMutableTreeNode) at.getChildAt(k);
                Entry entry = (Entry) child.getUserObject();
                if (entry.path() == null && entry.name().equals(parts[i])) {
                    folder = child;
                }
            }
            if (folder == null) {
                folder = new DefaultMutableTreeNode(new Entry(parts[i], null));
                at.add(folder);
            }
            at = folder;
        }
        at.add(new DefaultMutableTreeNode(new Entry(parts[parts.length - 1], path)));
    }

    private static DefaultMutableTreeNode find(DefaultMutableTreeNode node, String path) {
        if (path == null) {
            return null;
        }
        if (path.equals(pathOf(node))) {
            return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            DefaultMutableTreeNode found = find((DefaultMutableTreeNode) node.getChildAt(i), path);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    // In each folder, its folders come before its files, as in every file explorer.
    private static int foldersFirst(String a, String b) {
        String[] x = a.split("/");
        String[] y = b.split("/");
        for (int i = 0; i < Math.min(x.length, y.length); i++) {
            boolean xFolder = i < x.length - 1;
            boolean yFolder = i < y.length - 1;
            if (xFolder != yFolder) {
                return xFolder ? -1 : 1;
            }
            int byName = x[i].compareToIgnoreCase(y[i]);
            if (byName != 0) {
                return byName;
            }
        }
        return x.length - y.length;
    }

    private static String pathOf(Object node) {
        Object value = node instanceof DefaultMutableTreeNode ? ((DefaultMutableTreeNode) node).getUserObject() : null;
        return value instanceof Entry ? ((Entry) value).path() : null;
    }

    // Folders in the sans font's bold, files in the plain weight, each with its icon; a file with
    // unsaved changes has a dot before its name, as its tab has.
    private static final class Renderer extends DefaultTreeCellRenderer {
        private final java.util.function.Predicate<String> unsaved;

        Renderer(java.util.function.Predicate<String> unsaved) {
            this.unsaved = unsaved;
        }

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded,
                                                      boolean leaf, int row, boolean focused) {
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, focused);
            setBackgroundSelectionColor(null); // the tree paints the row's selection, the whole row wide
            setBorderSelectionColor(null);
            boolean file = pathOf(value) != null;
            setIcon(Icons.of(file ? Icons.Glyph.FILE : Icons.Glyph.FOLDER, Icons.Role.TOOL));
            setIconTextGap(Style.SPACE_2);
            Zoom.font(this, Style.sans(!file, file ? 12.5f : 12f));
            setForeground(Style.INK);
            if (file && unsaved.test(pathOf(value))) {
                setText("● " + getText());
            }
            return this;
        }
    }

    // ---- for tests ----

    JTree tree() {
        return tree;
    }
}
