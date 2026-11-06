package raphael.ui;

import java.awt.BorderLayout;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import raphael.model.Trace;
import raphael.render.Style;

// What the program printed up to the current step (the part the last line printed is tinted
// amber), how the run ended, and the input box for programs that read with Scanner (type the
// input before pressing run). The box is hidden for programs that never read input.
// OOP: inheritance (extends JPanel); implements TraceListener (Observer pattern).
public class ConsolePanel extends JPanel implements TraceListener {

    private final JTextArea output = new JTextArea();
    private final JTextArea input = new JTextArea(2, 20);
    private final JPanel inputArea = new JPanel(new BorderLayout());
    private final DefaultHighlighter.DefaultHighlightPainter newOutput =
            new DefaultHighlighter.DefaultHighlightPainter(null) {
                @Override
                public java.awt.Color getColor() {
                    return Style.CHANGE_TINT; // read when it paints, so it follows dark mode
                }
            };

    public ConsolePanel() {
        super(new BorderLayout());
        output.setEditable(false);
        output.setLineWrap(true);
        output.setWrapStyleWord(true);
        output.setFont(Style.CODE_FONT);
        output.setMargin(new java.awt.Insets(Style.SPACE_1, Style.SPACE_3, Style.SPACE_1, Style.SPACE_3));
        input.setFont(Style.CODE_FONT);
        input.setMargin(new java.awt.Insets(Style.SPACE_1, Style.SPACE_3, Style.SPACE_1, Style.SPACE_3));

        JScrollPane outputScroll = new JScrollPane(output);
        outputScroll.setBorder(BorderFactory.createEmptyBorder());
        JScrollPane inputScroll = new JScrollPane(input);
        inputScroll.setBorder(Section.hairline(1, 0, 0, 0));

        inputArea.setVisible(false);
        inputArea.setBackground(Style.BACKGROUND);
        JLabel inputHeader = Section.header("stdin · for Scanner, type before run");
        inputHeader.setBorder(BorderFactory.createCompoundBorder(Section.hairline(1, 0, 0, 0),
                BorderFactory.createEmptyBorder(Style.SPACE_2, Style.SPACE_3, Style.SPACE_1, Style.SPACE_3)));
        inputArea.add(inputHeader, BorderLayout.NORTH);
        inputArea.add(inputScroll, BorderLayout.CENTER);

        JPanel body = new JPanel(new BorderLayout());
        body.add(outputScroll, BorderLayout.CENTER);
        body.add(inputArea, BorderLayout.SOUTH);
        add(Section.wrap(Section.header("console"), body));
    }

    // A crash's stack trace, without the lines from inside Java itself, which only hide the student's.
    private static String withoutJavaLines(String output) {
        return output.replaceAll("(?m)^\\s+at (java|jdk|sun)\\S*.*\\R", "");
    }

    public String getInput() {
        return input.getText();
    }

    public void setInput(String text) {
        input.setText(text);
    }

    public void showInput(boolean show) {
        inputArea.setVisible(show);
    }

    public boolean isInputShown() {
        return inputArea.isVisible();
    }

    public void showMessage(String message) {
        output.setText(message);
        output.setCaretPosition(0); // the first error, not the end of the list
    }

    @Override
    public void stepChanged(TraceModel model) {
        Trace trace = model.getTrace();
        if (trace == null) {
            output.setText("");
            return;
        }
        if (!model.hasSteps()) { // the program ended before any of its lines ran
            output.setText(trace.getOutput() + "\n— " + trace.getEndMessage());
            return;
        }
        String printed = withoutJavaLines(trace.getOutputAt(model.getStep()));
        String before = model.getStep() > 0 ? withoutJavaLines(trace.getOutputAt(model.getStep() - 1)) : "";
        String text = printed;
        if (model.isLastStep()) {
            text += (text.isEmpty() || text.endsWith("\n") ? "" : "\n") + "— " + trace.getEndMessage();
        }
        output.setText(text);
        if (printed.length() > before.length() && printed.startsWith(before)) {
            try { // tint what the last line printed
                output.getHighlighter().addHighlight(before.length(), printed.length(), newOutput);
            } catch (BadLocationException e) {
                // nothing to tint
            }
        }
    }
}
