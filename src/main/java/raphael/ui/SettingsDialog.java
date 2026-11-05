package raphael.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import raphael.render.Style;
import raphael.trace.RunLimits;

// The settings window: the limits for each run, the text size and dark mode. ask() shows it and
// waits; it returns what the user chose, or null if they cancelled. The window then saves and
// applies the values.
// OOP: inheritance (extends JDialog); a nested record (Values) to hand back three values as one;
// the spinners can't go outside RunLimits' ranges, so the record's checks never fail here.
public class SettingsDialog extends JDialog {

    // Everything the dialog shows and returns.
    public record Values(RunLimits limits, int textSize, boolean dark) {
    }

    private final JSpinner steps = spinner(RunLimits.MIN_STEPS, RunLimits.MAX_STEPS, 100);
    private final JSpinner timeout = spinner(RunLimits.MIN_TIMEOUT, RunLimits.MAX_TIMEOUT, 1);
    private final JSpinner memory = spinner(RunLimits.MIN_MEMORY, RunLimits.MAX_MEMORY, 64);
    private final JSpinner textSize = spinner(Style.MIN_CODE_SIZE, Style.MAX_CODE_SIZE, 1);
    private final JCheckBox dark = new JCheckBox("dark mode");
    private Values chosen; // set by "ok"

    SettingsDialog(Frame owner, Values current) {
        super(owner, "raphael · settings", true); // true: the window waits until this closes
        fill(current);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(14, 16, 6, 16));
        row(form, 0, "steps before a run stops", steps, "an infinite loop stops here; more steps take longer to record");
        row(form, 1, "seconds a program may do nothing", timeout, "a long Thread.sleep, or a program stuck waiting");
        row(form, 2, "memory for the program (MB)", memory, "past this, the program ends with OutOfMemoryError");
        row(form, 3, "text size", textSize, "the code, variables and console");
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 4;
        c.gridx = 1;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(6, 8, 6, 0);
        form.add(dark, c);

        JButton defaults = new JButton("defaults");
        defaults.addActionListener(e -> fill(new Values(RunLimits.DEFAULTS, Style.DEFAULT_CODE_SIZE, dark.isSelected())));
        JButton cancel = new JButton("cancel");
        cancel.addActionListener(e -> dispose());
        JButton ok = new JButton("ok");
        ok.addActionListener(e -> {
            chosen = read();
            dispose();
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.setBorder(BorderFactory.createEmptyBorder(0, 10, 8, 10));
        buttons.add(defaults);
        buttons.add(cancel);
        buttons.add(ok);
        getRootPane().setDefaultButton(ok);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        add(form, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        pack();
        setResizable(false);
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
        dark.setSelected(values.dark());
    }

    Values read() {
        RunLimits limits = new RunLimits((Integer) steps.getValue(), (Integer) timeout.getValue(), (Integer) memory.getValue());
        return new Values(limits, (Integer) textSize.getValue(), dark.isSelected());
    }

    private static JSpinner spinner(int min, int max, int step) {
        return new JSpinner(new SpinnerNumberModel(min, min, max, step));
    }

    private static void row(JPanel form, int y, String name, JSpinner field, String hint) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = y;
        c.insets = new Insets(6, 0, 6, 8);
        c.anchor = GridBagConstraints.WEST;
        form.add(new JLabel(name), c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        field.setToolTipText(hint);
        form.add(field, c);
        c.gridx = 2;
        c.fill = GridBagConstraints.NONE;
        JLabel note = new JLabel(hint);
        note.setForeground(Style.MUTED);
        form.add(note, c);
    }
}
