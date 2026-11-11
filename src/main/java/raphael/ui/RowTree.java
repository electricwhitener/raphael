package raphael.ui;

import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Predicate;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import raphael.render.Style;

// A tree for the side bar (the examples, the Explorer's files), whose rows show their state the
// way every list in Raphael does: grey under the mouse, darker grey with an ink bar when chosen,
// across the whole row. A click anywhere on a folder's row opens or closes it; a row that opens
// something (an example, a file) shows the hand cursor.
// OOP: inheritance (extends JTree, overriding updateUI and paintComponent); the rows that open
// something are told by a Predicate passed in, so the tree doesn't know what they hold.
public class RowTree extends JTree {

    private int hoverRow = -1; // the row under the mouse, or -1

    public RowTree(Predicate<Object> opens) {
        super(new DefaultMutableTreeNode());
        setRootVisible(false);
        setShowsRootHandles(true);
        setToggleClickCount(1); // a click anywhere on a folder's row opens or closes it, not only on its arrow
        setOpaque(false); // it paints its own background, under the rows' hover and selection
        putClientProperty("JTree.paintSelection", false); // FlatLaf's full-width blue selection: painted here instead
        MouseAdapter hover = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int row = getClosestRowForLocation(e.getX(), e.getY());
                Rectangle bounds = row >= 0 ? getRowBounds(row) : null;
                int now = bounds != null && e.getY() >= bounds.y && e.getY() < bounds.y + bounds.height ? row : -1;
                if (now != hoverRow) {
                    hoverRow = now;
                    repaint();
                }
                TreePath path = now >= 0 ? getPathForRow(now) : null;
                boolean hand = path != null && opens.test(path.getLastPathComponent());
                setCursor(Cursor.getPredefinedCursor(hand ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hoverRow = -1;
                repaint();
            }
        };
        addMouseMotionListener(hover);
        addMouseListener(hover);
    }

    @Override
    public void updateUI() { // also when the theme changes, which makes a new UI
        super.updateUI();
        if (getUI() instanceof javax.swing.plaf.basic.BasicTreeUI) {
            ((javax.swing.plaf.basic.BasicTreeUI) getUI()).setLeftChildIndent(8); // small steps in, so titles get the room
            ((javax.swing.plaf.basic.BasicTreeUI) getUI()).setRightChildIndent(12);
        }
    }

    // Each row's hover and selection across the whole row, under the rows themselves.
    @Override
    protected void paintComponent(Graphics g) {
        g.setColor(Style.BACKGROUND);
        g.fillRect(0, 0, getWidth(), getHeight());
        Graphics2D g2 = (Graphics2D) g.create();
        int selected = getSelectionRows() == null || getSelectionRows().length == 0 ? -1 : getSelectionRows()[0];
        for (int row : new int[] {hoverRow, selected}) {
            Rectangle bounds = row >= 0 ? getRowBounds(row) : null;
            if (bounds != null) {
                g2.translate(0, bounds.y);
                Section.paintRow(g2, 0, getWidth(), bounds.height, row == selected, row == hoverRow);
                g2.translate(0, -bounds.y);
            }
        }
        g2.dispose();
        super.paintComponent(g);
    }
}
