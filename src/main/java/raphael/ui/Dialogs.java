package raphael.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import raphael.render.Style;

// Every question and message Raphael shows looks the same: a short bold heading that says what's
// going on, a sentence or two under it, and buttons that say what they do ("Save", "Don't save"),
// never just "OK". The buttons are in each system's own order: on Windows the main one comes
// first; on macOS and Linux it comes last, on the right. Enter presses the main button, which is
// always the safe one, and Esc cancels.
// A dialog is described step by step, then shown:
//     String answer = Dialogs.question(window, "Save changes to BSTInsert.java?")
//             .body("Your changes will be lost if you don't save them.")
//             .buttons("Save", "Cancel", "Don't save")
//             .ask();
// OOP: the Builder pattern (Question collects the parts, ask() builds the dialog), so a dialog
// with many optional parts doesn't need a method with a dozen parameters; a nested class;
// an anonymous class implementing Swing's Icon interface.
public final class Dialogs {

    private static final int BODY_WIDTH = 360;
    private static final int PADDING = 24;

    private Dialogs() {
    }

    public static Question question(Component owner, String heading) {
        return new Question(owner, heading);
    }

    // A message with one button.
    public static void tell(Component owner, String heading, String body) {
        question(owner, heading).body(body).buttons("OK", null).ask();
    }

    public static final class Question {

        private final Component owner;
        private final String heading;
        private String title = "Raphael";
        private String body;
        private JComponent extra;
        private boolean error;
        private String primary = "OK";
        private String cancel;
        private final List<String> others = new ArrayList<>();
        private final List<JButton> helpers = new ArrayList<>();
        private String answer;

        private Question(Component owner, String heading) {
            this.owner = owner;
            this.heading = heading;
        }

        public Question title(String text) {
            title = text;
            return this;
        }

        public Question body(String text) {
            body = text;
            return this;
        }

        // Something to show under the text, like a text field or the details of an error.
        public Question extra(JComponent component) {
            extra = component;
            return this;
        }

        // Marks the dialog as reporting a problem: a red sign beside the heading.
        public Question error() {
            error = true;
            return this;
        }

        // The main button (pressed by Enter), the cancel button (pressed by Esc; null for none),
        // and any others.
        public Question buttons(String main, String cancelLabel, String... otherLabels) {
            primary = main;
            cancel = cancelLabel;
            others.clear();
            others.addAll(List.of(otherLabels));
            return this;
        }

        // A button at the left of the footer that does something without closing the dialog.
        public Question helper(String label, java.util.function.Consumer<JButton> action) {
            JButton button = new JButton(label);
            button.addActionListener(e -> action.accept(button));
            helpers.add(button);
            return this;
        }

        // Shows the dialog and waits. The label of the button pressed; the cancel label (or null)
        // if the dialog was closed or Esc was pressed.
        public String ask() {
            answer = cancel;
            Window window = owner == null ? null : SwingUtilities.getWindowAncestor(owner) != null
                    ? SwingUtilities.getWindowAncestor(owner) : owner instanceof Window ? (Window) owner : null;
            JDialog dialog = new JDialog(window, title, Dialog.ModalityType.APPLICATION_MODAL);
            dialog.getContentPane().setBackground(Style.BACKGROUND);
            dialog.add(content(), BorderLayout.CENTER);
            dialog.add(footer(dialog), BorderLayout.SOUTH);
            dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                    KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
            dialog.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowOpened(java.awt.event.WindowEvent e) {
                    // The keyboard starts on the main (safe) button, or on the extra part if it's
                    // something to type into, like the find field.
                    JButton main = dialog.getRootPane().getDefaultButton();
                    if (extra instanceof javax.swing.text.JTextComponent && ((javax.swing.text.JTextComponent) extra).isEditable()) {
                        extra.requestFocusInWindow();
                    } else if (main != null) {
                        main.requestFocusInWindow();
                    }
                }
            });
            dialog.setResizable(false); // before pack(), so the packed size is the final one
            dialog.pack();
            dialog.setLocationRelativeTo(window);
            dialog.setVisible(true); // waits here until the dialog closes
            return answer;
        }

        private JComponent content() {
            JPanel content = new JPanel();
            content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
            content.setOpaque(false);
            content.setBorder(Zoom.gap(PADDING, PADDING, PADDING - 4, PADDING));
            if (heading != null) { // null when the title bar already says it all
                JLabel title = new JLabel(heading);
                Zoom.font(title, Style.sans(true, 15f));
                title.setForeground(Style.INK);
                if (error) {
                    title.setIcon(ERROR_SIGN);
                    title.setIconTextGap(Style.SPACE_3);
                }
                content.add(left(title));
            }
            if (body != null) {
                content.add(Box.createVerticalStrut(Style.SPACE_2));
                JLabel text = new JLabel("<html><div style='width:" + BODY_WIDTH + "px'>" + escape(body) + "</div></html>");
                Zoom.font(text, Style.sans(false, 13f));
                text.setForeground(Style.INK_SOFT);
                content.add(left(text));
            }
            if (extra != null) {
                if (heading != null || body != null) {
                    content.add(Box.createVerticalStrut(Style.SPACE_3));
                }
                content.add(left(extra));
            }
            return content;
        }

        private JComponent footer(JDialog dialog) {
            JPanel footer = new JPanel(new BorderLayout());
            footer.setBackground(Style.BAR);
            footer.setBorder(BorderFactory.createCompoundBorder(Section.hairline(1, 0, 0, 0),
                    Zoom.gap(Style.SPACE_3, PADDING, Style.SPACE_3, PADDING)));
            JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, Style.SPACE_2, 0));
            left.setOpaque(false);
            for (JButton helper : helpers) {
                left.add(sized(helper));
            }
            JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, Style.SPACE_2, 0));
            right.setOpaque(false);
            for (String label : order(primary, cancel, others, Shortcuts.isWindows(), Shortcuts.isMac())) {
                JButton button = sized(new JButton(label));
                button.addActionListener(e -> {
                    answer = label;
                    dialog.dispose();
                });
                right.add(button);
                if (label.equals(primary)) {
                    dialog.getRootPane().setDefaultButton(button);
                }
            }
            footer.add(left, BorderLayout.WEST);
            footer.add(right, BorderLayout.EAST);
            return footer;
        }
    }

    // The buttons from left to right, as each system orders them:
    //   Windows:  main, others, Cancel
    //   macOS:    others, Cancel, main     ("Don't save" kept apart from "Save")
    //   Linux:    Cancel, others, main
    static List<String> order(String primary, String cancel, List<String> others, boolean windows, boolean mac) {
        List<String> order = new ArrayList<>();
        if (windows) {
            order.add(primary);
            order.addAll(others);
            if (cancel != null) {
                order.add(cancel);
            }
        } else {
            if (mac) {
                order.addAll(others);
            }
            if (cancel != null) {
                order.add(cancel);
            }
            if (!mac) {
                order.addAll(others);
            }
            order.add(primary);
        }
        return order;
    }

    private static JButton sized(JButton button) {
        Dimension size = button.getPreferredSize();
        button.setPreferredSize(new Dimension(Math.max(Zoom.px(88), size.width), Zoom.px(32)));
        return button;
    }

    private static JComponent left(JComponent component) {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        return component;
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
    }

    // A red circle with "!", drawn rather than taken from an image, so it matches Style.ERROR.
    private static final Icon ERROR_SIGN = new Icon() {
        @Override
        public void paintIcon(Component c, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Style.ERROR);
            g.fillOval(x, y, 22, 22);
            g.setColor(Style.BACKGROUND);
            g.fillRoundRect(x + 10, y + 5, 2, 8, 2, 2);
            g.fillOval(x + 10, y + 15, 2, 2);
            g.dispose();
        }

        @Override
        public int getIconWidth() {
            return 22;
        }

        @Override
        public int getIconHeight() {
            return 22;
        }
    };
}
