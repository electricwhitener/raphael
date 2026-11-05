package raphael.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import raphael.render.Style;
import raphael.trace.RunLimits;

// The settings window: the limits for each run, the text size and the theme. ask() shows it and
// waits; it returns what the user chose, or null if they cancelled. The window then saves and
// applies the values.
// Each setting has its name, a line saying what it does, and its unit. The theme is a three-way
// switch: follow the computer, or always light, or always dark.
// OOP: inheritance (extends JDialog); a nested record (Values) and enum (Theme); the spinners
// can't go outside RunLimits' ranges, so the record's checks never fail here.
public class SettingsDialog extends JDialog {

    public enum Theme { SYSTEM, LIGHT, DARK }

    // Everything the dialog shows and returns.
    public record Values(RunLimits limits, int textSize, Theme theme, boolean checkUpdates) {
    }

    private final JSpinner steps = spinner(RunLimits.MIN_STEPS, RunLimits.MAX_STEPS, 100);
    private final JSpinner timeout = spinner(RunLimits.MIN_TIMEOUT, RunLimits.MAX_TIMEOUT, 1);
    private final JSpinner memory = spinner(RunLimits.MIN_MEMORY, RunLimits.MAX_MEMORY, 64);
    private final JSpinner textSize = spinner(Style.MIN_CODE_SIZE, Style.MAX_CODE_SIZE, 1);
    private final JToggleButton system = segment("System");
    private final JToggleButton light = segment("Light");
    private final JToggleButton dark = segment("Dark");
    private final javax.swing.JCheckBox updates = new javax.swing.JCheckBox();
    private Values chosen; // set by Save

    SettingsDialog(Frame owner, Values current) {
        super(owner, "Settings", true); // true: the window waits until this closes
        ButtonGroup themes = new ButtonGroup();
        themes.add(system);
        themes.add(light);
        themes.add(dark);
        fill(current);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        form.setBorder(BorderFactory.createEmptyBorder(Style.SPACE_6, Style.SPACE_6, Style.SPACE_3, Style.SPACE_6));
        int y = 0;
        group(form, y++, "Run limits", false);
        row(form, y++, "Steps before a run stops", "An endless loop stops here. More steps take longer to record.", steps, "steps");
        row(form, y++, "Time a program may do nothing", "For example a long Thread.sleep, or waiting for input.", timeout, "seconds");
        row(form, y++, "Memory for the program", "Past this, the program ends with OutOfMemoryError.", memory, "MB");
        group(form, y++, "Appearance", true);
        row(form, y++, "Text size", "The code, the variables and the console.", textSize, "pt");
        JPanel segments = new JPanel(new GridLayout(1, 3, 0, 0)); // one row that never wraps
        segments.setOpaque(false);
        segments.add(system);
        segments.add(light);
        segments.add(dark);
        row(form, y++, "Theme", "System follows your computer's light or dark mode.", segments, null);
        group(form, y++, "Updates", true);
        updates.getAccessibleContext().setAccessibleName("Check for new versions");
        row(form, y, "Check for new versions", "Once a day, Raphael asks GitHub whether a newer version exists. "
                + "Nothing about you or your code is sent.", updates, null);

        JButton defaults = new JButton("Restore defaults");
        defaults.putClientProperty("JButton.buttonType", "borderless");
        defaults.setForeground(Style.ACCENT); // looks like a link: it changes the fields, not the settings
        defaults.addActionListener(e -> fill(new Values(RunLimits.DEFAULTS, Style.DEFAULT_CODE_SIZE, Theme.SYSTEM, true)));
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(Style.BAR);
        footer.setBorder(BorderFactory.createCompoundBorder(Section.hairline(1, 0, 0, 0),
                BorderFactory.createEmptyBorder(Style.SPACE_3, Style.SPACE_6, Style.SPACE_3, Style.SPACE_6)));
        footer.add(defaults, BorderLayout.WEST);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, Style.SPACE_2, 0));
        buttons.setOpaque(false);
        for (String label : Dialogs.order("Save", "Cancel", java.util.List.of(), Shortcuts.isWindows(), Shortcuts.isMac())) {
            JButton button = new JButton(label);
            button.setPreferredSize(new Dimension(Math.max(88, button.getPreferredSize().width), 32));
            if (label.equals("Save")) {
                button.addActionListener(e -> {
                    chosen = read();
                    dispose();
                });
                getRootPane().setDefaultButton(button);
            } else {
                button.addActionListener(e -> dispose());
            }
            buttons.add(button);
        }
        footer.add(buttons, BorderLayout.EAST);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        getContentPane().setBackground(Style.BACKGROUND);
        add(form, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);
        setResizable(false); // before pack(), so the packed size is the final one (it changes the frame on Windows)
        pack();
        setLocationRelativeTo(owner);
    }

    // Shows the dialog and waits. The chosen values, or null if the user cancelled.
    public static Values ask(Frame owner, Values current) {
        SettingsDialog dialog = new SettingsDialog(owner, current);
        dialog.setVisible(true); // returns when the dialog closes
        return dialog.chosen;
    }

    private void fill(Values values) {
        steps.setValue(values.limits().maxSteps());
        timeout.setValue(values.limits().timeoutSeconds());
        memory.setValue(values.limits().memoryMb());
        textSize.setValue(values.textSize());
        (values.theme() == Theme.LIGHT ? light : values.theme() == Theme.DARK ? dark : system).setSelected(true);
        updates.setSelected(values.checkUpdates());
    }

    Values read() {
        RunLimits limits = new RunLimits((Integer) steps.getValue(), (Integer) timeout.getValue(), (Integer) memory.getValue());
        Theme theme = light.isSelected() ? Theme.LIGHT : dark.isSelected() ? Theme.DARK : Theme.SYSTEM;
        return new Values(limits, (Integer) textSize.getValue(), theme, updates.isSelected());
    }

    private static JSpinner spinner(int min, int max, int step) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(min, min, max, step));
        spinner.setPreferredSize(new Dimension(104, 32));
        spinner.setFont(Style.UI_FONT);
        return spinner;
    }

    // One part of a three-way switch.
    private static JToggleButton segment(String text) {
        JToggleButton button = new JToggleButton(text);
        button.setFont(Style.sans(false, 13f));
        button.setPreferredSize(new Dimension(Math.max(72, button.getPreferredSize().width + Style.SPACE_2), 32));
        button.putClientProperty("FlatLaf.style", "selectedBackground: " + hex(Style.ACCENT)
                + "; selectedForeground: " + hex(Style.ON_ACCENT));
        return button;
    }

    private static void group(JPanel form, int y, String name, boolean ruleAbove) {
        JLabel caption = Section.header(name);
        caption.setBorder(BorderFactory.createCompoundBorder(ruleAbove ? Section.hairline(1, 0, 0, 0) : BorderFactory.createEmptyBorder(),
                BorderFactory.createEmptyBorder(ruleAbove ? Style.SPACE_6 - Style.SPACE_2 : 0, 0, Style.SPACE_1, 0)));
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = y;
        c.gridwidth = 3;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(ruleAbove ? Style.SPACE_3 : 0, 0, Style.SPACE_1, 0);
        form.add(caption, c);
    }

    // A setting: its name with what it does underneath, then its field and unit on the right.
    private static void row(JPanel form, int y, String name, String hint, JComponent field, String unit) {
        JPanel text = new JPanel(new BorderLayout(0, 2));
        text.setOpaque(false);
        text.add(label(name, Style.sans(false, 13f), Style.INK), BorderLayout.NORTH);
        JLabel note = label("<html><div style='width:280px'>" + hint + "</div></html>", Style.sans(false, 12f), Style.MUTED);
        text.add(note, BorderLayout.CENTER);
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = y;
        c.weightx = 1;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(Style.SPACE_2, 0, Style.SPACE_2, Style.SPACE_6);
        form.add(text, c);
        c.gridx = 1;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        c.insets = new Insets(Style.SPACE_2, 0, Style.SPACE_2, Style.SPACE_2);
        form.add(field, c);
        if (unit != null) {
            c.gridx = 2;
            c.insets = new Insets(Style.SPACE_2, 0, Style.SPACE_2, 0);
            form.add(label(unit, Style.sans(false, 12f), Style.INK_SOFT), c);
        }
    }

    private static JLabel label(String text, java.awt.Font font, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(font);
        label.setForeground(color);
        return label;
    }

    private static String hex(Color color) {
        return String.format("#%06x", color.getRGB() & 0xFFFFFF);
    }
}
