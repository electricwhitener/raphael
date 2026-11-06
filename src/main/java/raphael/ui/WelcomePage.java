package raphael.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import raphael.io.ExampleCatalog;
import raphael.render.Style;

// What Raphael shows when no program is open, like an IDE's welcome page: how to start (a new
// program, a file, or a pasted program), the files opened recently, and the examples, grouped
// by topic. Picking any of them opens the editor.
// Every colour is read while painting, so the page follows dark mode by itself.
// OOP: inheritance (extends JPanel); composition (built from lists and buttons); anonymous
// subclasses that override how a list is coloured; the window's actions are passed in as
// Runnables and Consumers, so the page doesn't need to know the window.
public class WelcomePage extends JPanel {

    private static final int MAX_WIDTH = 1160;
    private static final int START_WIDTH = 240;

    private final JPanel page;
    private final JPanel recentList = new JPanel();
    private final Consumer<File> onRecent;
    private final JList<ExampleCatalog.Group> groups;
    private final JList<ExampleCatalog.Example> examples;

    // The actions behind the page's buttons, given by the window.
    public record Actions(Runnable newProgram, Runnable open, Runnable paste, Consumer<ExampleCatalog.Example> example,
                          Consumer<File> recent) {
    }

    public WelcomePage(Actions actions) {
        super(null); // doLayout() places the page
        this.onRecent = actions.recent();
        page = new JPanel(new BorderLayout(Style.SPACE_6, Style.SPACE_6));
        page.setOpaque(false);
        page.add(header(), BorderLayout.NORTH);

        // Left: how to start, then the recent files.
        JPanel start = new JPanel();
        start.setOpaque(false);
        start.setLayout(new BoxLayout(start, BoxLayout.Y_AXIS));
        start.add(heading("Start"));
        start.add(action(Icons.Glyph.NEW, "New program", "Ctrl+N", actions.newProgram()));
        start.add(action(Icons.Glyph.OPEN, "Open file…", "Ctrl+O", actions.open()));
        start.add(action(Icons.Glyph.PASTE, "Paste a program", "Ctrl+V", actions.paste()));
        start.add(Box.createVerticalStrut(Style.SPACE_6));
        start.add(heading("Recent"));
        recentList.setOpaque(false);
        recentList.setLayout(new BoxLayout(recentList, BoxLayout.Y_AXIS));
        recentList.setAlignmentX(Component.LEFT_ALIGNMENT);
        start.add(recentList);
        start.add(Box.createVerticalGlue());
        start.setPreferredSize(new Dimension(START_WIDTH, 0));
        page.add(start, BorderLayout.WEST);
        showRecent(List.of());

        // Right: the examples. The groups on the left, the chosen group's examples beside them.
        groups = new JList<>(ExampleCatalog.groups().toArray(new ExampleCatalog.Group[0])) {
            @Override
            public Color getBackground() {
                return Style.BAR;
            }
        };
        groups.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        groups.setCellRenderer(new GroupRenderer());
        examples = new JList<>(new DefaultListModel<>()) {
            @Override
            public Color getBackground() {
                return Style.BACKGROUND;
            }
        };
        examples.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        ExampleRenderer exampleRenderer = new ExampleRenderer();
        examples.setCellRenderer(exampleRenderer);
        groups.addListSelectionListener(e -> {
            DefaultListModel<ExampleCatalog.Example> model = (DefaultListModel<ExampleCatalog.Example>) examples.getModel();
            model.clear();
            if (groups.getSelectedValue() != null) {
                model.addAll(groups.getSelectedValue().examples());
            }
            examples.ensureIndexIsVisible(0);
        });
        groups.setSelectedIndex(0);
        // One click opens an example, as on a web page; the keyboard picks one with the arrows and Enter.
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int index = examples.locationToIndex(e.getPoint());
                if (index >= 0 && examples.getCellBounds(index, index).contains(e.getPoint())) {
                    actions.example().accept(examples.getModel().getElementAt(index));
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                int index = examples.locationToIndex(e.getPoint());
                exampleRenderer.hovered = index >= 0 && examples.getCellBounds(index, index).contains(e.getPoint()) ? index : -1;
                examples.setCursor(Cursor.getPredefinedCursor(exampleRenderer.hovered >= 0 ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                examples.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                exampleRenderer.hovered = -1;
                examples.repaint();
            }
        };
        examples.addMouseListener(mouse);
        javax.swing.ToolTipManager.sharedInstance().registerComponent(examples); // the renderer's tooltips
        examples.addMouseMotionListener(mouse);
        examples.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "open");
        examples.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "open");
        java.awt.event.FocusAdapter showRing = new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                JList<?> list = (JList<?>) e.getComponent();
                if (list.getSelectedIndex() < 0 && list.getModel().getSize() > 0) {
                    list.setSelectedIndex(0); // so Tab lands somewhere visible
                }
                list.repaint();
            }

            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                e.getComponent().repaint();
            }
        };
        examples.addFocusListener(showRing);
        groups.addFocusListener(showRing);
        examples.getActionMap().put("open", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (examples.getSelectedValue() != null) {
                    actions.example().accept(examples.getSelectedValue());
                }
            }
        });
        // From the groups, → moves into their examples.
        groups.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "into");
        groups.getActionMap().put("into", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                examples.requestFocusInWindow();
                if (examples.getSelectedIndex() < 0 && examples.getModel().getSize() > 0) {
                    examples.setSelectedIndex(0);
                }
            }
        });
        examples.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "back");
        examples.getActionMap().put("back", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                groups.requestFocusInWindow();
            }
        });

        JScrollPane groupScroll = scroll(groups);
        groupScroll.setPreferredSize(new Dimension(224, 0));
        groupScroll.setBorder(Section.hairline(0, 0, 0, 1));
        JPanel browser = new JPanel(new BorderLayout()) {
            @Override
            protected void paintBorder(Graphics graphics) {
                Graphics2D g = smooth(graphics);
                g.setColor(Style.HAIRLINE);
                g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, Style.ARC, Style.ARC);
            }
        };
        browser.setOpaque(false);
        browser.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
        browser.add(groupScroll, BorderLayout.WEST);
        browser.add(scroll(examples), BorderLayout.CENTER);
        JPanel right = new JPanel(new BorderLayout(0, Style.SPACE_2));
        right.setOpaque(false);
        JLabel examplesHeading = heading("Examples · " + ExampleCatalog.all().size() + " programs · click one, then press Run");
        right.add(examplesHeading, BorderLayout.NORTH);
        right.add(browser, BorderLayout.CENTER);
        page.add(right, BorderLayout.CENTER);

        add(page);
    }

    // The page fills the window, less a margin, up to MAX_WIDTH wide and centred.
    @Override
    public void doLayout() {
        int width = Math.min(MAX_WIDTH, getWidth() - 2 * Style.SPACE_6);
        page.setBounds((getWidth() - width) / 2, Style.SPACE_6, width, getHeight() - 2 * Style.SPACE_6);
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(640, 340);
    }

    @Override
    public Color getBackground() {
        return Style.BACKGROUND;
    }

    // The files opened lately, newest first; each opens with one click.
    public void showRecent(List<File> files) {
        recentList.removeAll();
        if (files.isEmpty()) {
            recentList.add(label("Files you open appear here.", Style.sans(false, 12.5f), () -> Style.MUTED));
        }
        for (File file : files) {
            JButton link = link(file.getName(), () -> onRecent.accept(file));
            link.setToolTipText(file.getPath());
            recentList.add(link);
            File folder = file.getParentFile();
            if (folder != null) {
                JLabel where = label(folder.getName(), Style.sans(false, 11.5f), () -> Style.MUTED);
                where.setBorder(BorderFactory.createEmptyBorder(0, Style.SPACE_2, Style.SPACE_2, 0));
                recentList.add(where);
            }
        }
        recentList.revalidate();
        recentList.repaint();
    }

    // Puts the keyboard in the examples, so the arrows and Enter work at once.
    public void focusExamples() {
        groups.requestFocusInWindow();
    }

    // The logo, the name and one line on what Raphael does.
    private static JComponent header() {
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));
        BufferedImage logo = Logo.draw(96);
        header.add(new JComponent() {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = smooth(graphics);
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.drawImage(logo, 0, 0, 48, 48, null);
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(48, 48);
            }

            @Override
            public Dimension getMaximumSize() {
                return getPreferredSize();
            }
        });
        header.add(Box.createHorizontalStrut(Style.SPACE_3));
        JPanel words = new JPanel();
        words.setOpaque(false);
        words.setLayout(new BoxLayout(words, BoxLayout.Y_AXIS));
        words.add(label("Raphael", Style.sans(true, 22f), () -> Style.INK));
        words.add(label("Watch your Java program run, one line at a time.", Style.sans(false, 13.5f), () -> Style.INK_SOFT));
        header.add(words);
        header.add(Box.createHorizontalGlue());
        return header;
    }

    private static JLabel heading(String text) {
        JLabel heading = label(text.toUpperCase(java.util.Locale.ROOT), Style.sans(true, 11f), () -> Style.MUTED);
        heading.setBorder(BorderFactory.createEmptyBorder(0, 0, Style.SPACE_2, 0));
        return heading;
    }

    // A start action: an icon, its name and its keys, as a wide flat button.
    private static JComponent action(Icons.Glyph glyph, String text, String keys, Runnable run) {
        JButton button = new JButton(text, Icons.of(glyph, Icons.Role.TOOL)) {
            @Override
            public Color getForeground() {
                return Style.INK;
            }
        };
        button.putClientProperty("JButton.buttonType", "toolBarButton"); // like the toolbar: a focus ring, a hover fill
        button.putClientProperty("FlatLaf.style", "margin: 6,8,6,4; arc: " + Style.ARC);
        button.setFont(Style.sans(false, 13.5f));
        button.setHorizontalAlignment(JButton.LEFT);
        button.setIconTextGap(Style.SPACE_3);
        button.setLayout(new BorderLayout());
        button.add(EmptyState.keyChip(Shortcuts.keycap(keys)), BorderLayout.EAST);
        button.setMaximumSize(new Dimension(START_WIDTH, 40));
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.addActionListener(e -> run.run());
        return button;
    }

    private static JButton link(String text, Runnable run) {
        JButton link = new JButton(text) {
            @Override
            public Color getForeground() {
                return Style.ACCENT;
            }
        };
        link.putClientProperty("JButton.buttonType", "toolBarButton");
        link.putClientProperty("FlatLaf.style", "margin: 3,8,1,8; arc: " + Style.ARC);
        link.setFont(Style.sans(false, 13f));
        link.setHorizontalAlignment(JButton.LEFT);
        link.setAlignmentX(Component.LEFT_ALIGNMENT);
        link.setMaximumSize(new Dimension(START_WIDTH, 28));
        link.addActionListener(e -> run.run());
        return link;
    }

    private static JScrollPane scroll(JComponent list) {
        JScrollPane scroll = new JScrollPane(list, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    private static JLabel label(String text, Font font, Supplier<Color> color) {
        JLabel label = new JLabel(text) {
            @Override
            public Color getForeground() {
                return color.get();
            }
        };
        label.setFont(font);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private static Graphics2D smooth(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g;
    }

    // The keyboard's place in a list: a 2 px ring in the accent colour, as FlatLaf draws on buttons.
    private static void ring(Graphics2D g, int top, int width, int height) {
        g.setColor(Style.ACCENT);
        g.setStroke(new java.awt.BasicStroke(Style.FOCUS_WIDTH));
        g.drawRoundRect(Style.SPACE_1 + 1, top + 1, width - 2 * Style.SPACE_1 - 3, height - top - 3, 6, 6);
    }

    // A group in the list, with its section's name above it when it's the first of that section.
    private static class GroupRenderer extends JPanel implements ListCellRenderer<ExampleCatalog.Group> {

        private final JLabel section = new JLabel();
        private final JLabel name = new JLabel();
        private final JLabel count = new JLabel();
        private boolean selected;
        private boolean ringed; // chosen with the keyboard: a focus ring, not just a fill

        GroupRenderer() {
            super(new BorderLayout());
            setOpaque(false);
            section.setFont(Style.sans(true, 10.5f));
            section.setBorder(BorderFactory.createEmptyBorder(Style.SPACE_3, Style.SPACE_3, Style.SPACE_1, Style.SPACE_3));
            name.setFont(Style.sans(false, 13f));
            count.setFont(Style.sans(false, 11.5f));
            JPanel row = new JPanel(new BorderLayout());
            row.setOpaque(false);
            row.setBorder(BorderFactory.createEmptyBorder(Style.SPACE_1 + 1, Style.SPACE_3, Style.SPACE_1 + 1, Style.SPACE_3));
            row.add(name, BorderLayout.CENTER);
            row.add(count, BorderLayout.EAST);
            add(section, BorderLayout.NORTH);
            add(row, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends ExampleCatalog.Group> list, ExampleCatalog.Group group,
                                                      int index, boolean isSelected, boolean focused) {
            boolean first = index == 0 || !list.getModel().getElementAt(index - 1).section().equals(group.section());
            section.setVisible(first);
            section.setText(group.section().toUpperCase(java.util.Locale.ROOT));
            section.setForeground(Style.MUTED);
            name.setText(group.name());
            count.setText(String.valueOf(group.examples().size()));
            selected = isSelected;
            ringed = isSelected && list.isFocusOwner();
            name.setForeground(isSelected ? Style.ACCENT : Style.INK);
            name.setFont(Style.sans(isSelected, 13f));
            count.setForeground(Style.MUTED);
            return this;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(0, super.getPreferredSize().height); // as wide as the list, not the text
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            if (selected) {
                Graphics2D g = smooth(graphics);
                int top = section.isVisible() ? section.getPreferredSize().height : 0;
                g.setColor(Style.ACCENT_TINT);
                g.fillRoundRect(Style.SPACE_1, top, getWidth() - 2 * Style.SPACE_1, getHeight() - top, 6, 6);
                if (ringed) {
                    ring(g, top, getWidth(), getHeight());
                }
            }
        }
    }

    // An example in the list: its title, and a line on what it shows.
    private static class ExampleRenderer extends JPanel implements ListCellRenderer<ExampleCatalog.Example> {

        private final JLabel title = new JLabel();
        private final JLabel blurb = new JLabel();
        private boolean lit;
        private boolean ringed;
        int hovered = -1;

        ExampleRenderer() {
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(BorderFactory.createEmptyBorder(Style.SPACE_2, Style.SPACE_3 + Style.SPACE_1, Style.SPACE_2, Style.SPACE_3));
            title.setFont(Style.sans(true, 13f));
            blurb.setFont(Style.sans(false, 12.5f));
            add(title);
            add(Box.createVerticalStrut(2));
            add(blurb);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends ExampleCatalog.Example> list, ExampleCatalog.Example example,
                                                      int index, boolean isSelected, boolean focused) {
            title.setText(example.title());
            blurb.setText(example.blurb());
            setToolTipText(example.blurb()); // the whole sentence, when the row is too narrow for it
            title.setForeground(Style.INK);
            blurb.setForeground(Style.INK_SOFT);
            ringed = isSelected && list.isFocusOwner();
            lit = index == hovered || ringed;
            return this;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(0, super.getPreferredSize().height); // a long blurb ends in "…", not a scroll bar
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            if (lit) {
                Graphics2D g = smooth(graphics);
                g.setColor(Style.HOVER);
                g.fillRoundRect(Style.SPACE_1, 1, getWidth() - 2 * Style.SPACE_1, getHeight() - 2, 6, 6);
            }
            if (ringed) {
                ring(smooth(graphics), 0, getWidth(), getHeight());
            }
        }
    }
}
