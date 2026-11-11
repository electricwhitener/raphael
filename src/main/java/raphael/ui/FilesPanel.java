package raphael.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import raphael.model.Snapshot;
import raphael.model.Trace;
import raphael.render.Style;

// The files in the program's folder at the current step: a tab for each file, and the chosen
// file's text below. A file that changed on this step has an amber dot, and the text it just
// gained is tinted amber, as in the console. Before a run it lists the input files the user
// added, which are copied into the folder for the program to read.
// OOP: inheritance (extends JPanel); implements TraceListener (Observer pattern); Runnables for
// the window's actions, so the panel doesn't need to know the window.
public class FilesPanel extends JPanel implements TraceListener {

    private final JPanel tabs = new JPanel(new FlowLayout(FlowLayout.LEFT, Style.SPACE_1, 0));
    private final JTextArea text = new JTextArea();
    private final JButton add = link("add file…");
    private final JButton clear = link("clear");
    private List<Path> inputs = List.of();
    private String chosen; // the file whose text is shown
    private final DefaultHighlighter.DefaultHighlightPainter newText =
            new DefaultHighlighter.DefaultHighlightPainter(null) {
                @Override
                public Color getColor() {
                    return Style.CHANGE_TINT;
                }
            };

    public FilesPanel(Runnable onAddFile, Runnable onClearFiles) {
        super(new BorderLayout());
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setFont(Style.CODE_FONT);
        text.setMargin(new java.awt.Insets(Style.SPACE_1, Style.SPACE_3, Style.SPACE_1, Style.SPACE_3));
        tabs.setOpaque(false);
        tabs.setBorder(Zoom.gap(0, Style.SPACE_2, Style.SPACE_1, Style.SPACE_2));
        add.setToolTipText("Copy a file from your computer into the program's folder before each run");
        add.addActionListener(e -> onAddFile.run());
        clear.setToolTipText("Stop copying the input files in");
        clear.addActionListener(e -> onClearFiles.run());
        clear.setVisible(false); // until there are input files to clear
        JScrollPane scroll = new JScrollPane(text, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.add(tabs, BorderLayout.NORTH);
        body.add(scroll, BorderLayout.CENTER);
        JLabel header = Section.header("files");
        header.setToolTipText("The files in the program's own folder, at this step");
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(header, BorderLayout.CENTER);
        JPanel links = new JPanel(new java.awt.GridBagLayout()); // centred on the header's line
        links.setOpaque(false);
        links.setBorder(Zoom.gap(Style.SPACE_1, 0, 0, Style.SPACE_2));
        links.add(clear);
        links.add(add);
        top.add(links, BorderLayout.EAST);
        setBackground(Style.BACKGROUND);
        setBorder(Section.hairline(0, 0, 0, 1));
        add(top, BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);
        showInputs();
    }

    // The files to copy into the program's folder before each run.
    public void setInputs(List<Path> files) {
        inputs = List.copyOf(files);
        clear.setVisible(!inputs.isEmpty());
        showInputs();
    }

    @Override
    public void stepChanged(TraceModel model) {
        Trace trace = model.getTrace();
        if (trace == null || !model.hasSteps()) {
            showInputs();
            return;
        }
        Snapshot now = model.getSnapshot();
        Map<String, String> files = now.getFiles();
        Map<String, String> before = model.getStep() > 0 ? trace.get(model.getStep() - 1).getFiles() : Map.of();
        if (files.isEmpty()) {
            show("No files yet. When the program writes one, for example new FileWriter(\"out.txt\"), it appears here.");
            return;
        }
        // Show the file that just changed; otherwise keep showing the one chosen.
        List<String> changed = new ArrayList<>();
        for (String name : files.keySet()) {
            if (!files.get(name).equals(before.get(name))) {
                changed.add(name);
            }
        }
        if (!changed.isEmpty()) {
            chosen = changed.get(0);
        } else if (chosen == null || !files.containsKey(chosen)) {
            chosen = files.keySet().iterator().next();
        }
        tabs.removeAll();
        for (String name : files.keySet()) {
            tabs.add(tab(name, name.equals(chosen), changed.contains(name), () -> {
                chosen = name;
                stepChanged(model);
            }));
        }
        tabs.revalidate();
        tabs.repaint();
        String content = files.get(chosen);
        String old = before.getOrDefault(chosen, "");
        text.setText(content.isEmpty() ? "(empty)" : content);
        text.setForeground(content.isEmpty() ? Style.MUTED : Style.INK);
        text.setCaretPosition(0);
        int from = content.startsWith(old) ? old.length() : 0; // what it gained, or all of it if it was rewritten
        if (!content.isEmpty() && !content.equals(old) && from < content.length()) {
            try {
                text.getHighlighter().addHighlight(from, content.length(), newText);
                text.setCaretPosition(content.length()); // keep what's new in view
            } catch (BadLocationException e) {
                // nothing to tint
            }
        }
    }

    private void showInputs() {
        if (inputs.isEmpty()) {
            show("Files the program writes appear here, step by step.\n\nTo give it a file to read, press add file….");
        } else {
            StringBuilder names = new StringBuilder("Copied into the program's folder before each run:\n");
            for (Path input : inputs) {
                names.append("  ").append(input.getFileName()).append('\n');
            }
            show(names.toString());
        }
    }

    private void show(String message) {
        tabs.removeAll();
        tabs.revalidate();
        tabs.repaint();
        text.setText(message);
        text.setForeground(Style.MUTED);
        text.setCaretPosition(0);
    }

    // A file's tab: its name, with an amber dot when it changed on this step.
    private static JButton tab(String name, boolean chosen, boolean changed, Runnable choose) {
        JButton tab = new JButton(name) {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics;
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (chosen) {
                    g.setColor(Style.ACCENT_TINT);
                    g.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                }
                super.paintComponent(graphics);
                if (changed) { // amber, with an edge so it shows on white too
                    g.setColor(Style.CHANGE);
                    g.fillOval(getWidth() - 12, 3, 7, 7);
                    g.setColor(Style.INK_SOFT);
                    g.drawOval(getWidth() - 12, 3, 7, 7);
                }
            }

            @Override
            public Color getForeground() {
                return chosen ? Style.ACCENT : Style.INK_SOFT;
            }
        };
        tab.putClientProperty("JButton.buttonType", "borderless");
        tab.setContentAreaFilled(false);
        Zoom.font(tab, Style.UI_FONT.deriveFont(12f));
        tab.setBorder(Zoom.gap(2, Style.SPACE_2, 2, Style.SPACE_2 + 8));
        tab.setRequestFocusEnabled(false);
        tab.setToolTipText(changed ? name + " changed on this step" : name);
        tab.addActionListener(e -> choose.run());
        return tab;
    }

    // A small blue link-like button in the header, with the usual focus ring.
    private static JButton link(String text) {
        JButton link = new JButton(text) {
            @Override
            public Color getForeground() {
                return Style.ACCENT;
            }
        };
        link.putClientProperty("JButton.buttonType", "borderless");
        link.putClientProperty("FlatLaf.style", "margin: 2,6,2,6");
        Zoom.font(link, Style.sans(false, 12f));
        link.setRequestFocusEnabled(false);
        return link;
    }
}
