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
// in VS Code. A click on a file shows it in the editor; the file in the editor is the one chosen.
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
    private final DefaultMutableTreeNode root = new DefaultMutableTreeNode();
    private final DefaultTreeModel model = new DefaultTreeModel(root);
    private List<String> shownPaths = List.of();
    private String shownFolder = "";
    private boolean choosing; // the tree is being set to the file in the editor: not a click

    public ExplorerPanel(Consumer<String> show) {
        super(new BorderLayout());
        setBackground(Style.BACKGROUND);
        tree.setModel(model);
        tree.setRootVisible(true); // the folder's own name on top, as VS Code shows it
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        Zoom.rowHeight(tree, Style.PALETTE_ROW_H - 4);
        tree.setCellRenderer(new Renderer());
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
        add(scroll, BorderLayout.CENTER);
    }

    // The program's files: the folder's name and the paths of its files ("shapes/Circle.java"),
    // and the one in the editor. Built again only when the files change.
    public void show(String folderName, List<String> paths, String shown) {
        if (!paths.equals(shownPaths) || !folderName.equals(shownFolder)) {
            shownPaths = List.copyOf(paths);
            shownFolder = folderName;
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

    // Folders in the sans font's bold, files in the plain weight, each with its icon.
    private static final class Renderer extends DefaultTreeCellRenderer {
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
            return this;
        }
    }

    // ---- for tests ----

    JTree tree() {
        return tree;
    }
}
