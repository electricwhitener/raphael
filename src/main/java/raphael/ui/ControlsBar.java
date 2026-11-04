package raphael.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.Timer;
import raphael.render.Style;

// The step controls, in terminal style:  |<  <  >  >|  skip call  finish call  play  ──●──  step 046/067  1x
// "skip call" skips the inside of method calls on this line; "finish call" runs until this method returns.
// Playing uses a Swing Timer that moves one step at a time. Moving to a step any other way (a
// key, a button, the slider, a line number) pauses it, so you stay on the step you asked for.
// OOP: inheritance (extends JPanel); implements TraceListener (Observer pattern).
public class ControlsBar extends JPanel implements TraceListener {

    private static final String[] SPEEDS = {"0.5x", "1x", "2x", "4x", "8x"};
    private static final double[] SPEED_VALUES = {0.5, 1, 2, 4, 8};

    private final TraceModel model;
    private final JButton first = button("|<", "first step  (Home)");
    private final JButton back = button("<", "step back  (← or Alt+←)");
    private final JButton forward = button(">", "step forward  (→ or Alt+→)");
    private final JButton last = button(">|", "last step  (End)");
    private final JButton over = button("skip call", "step over: don't go inside the calls on this line  (Alt+↓)");
    private final JButton out = button("finish call", "step out: run until this method returns  (Alt+↑)");
    private final JButton play = button("play", "play / pause  (Space)");
    private final JSlider slider = new JSlider(0, 0, 0);
    private final JLabel stepLabel = new JLabel("step ---/---");
    private final JComboBox<String> speed = new JComboBox<>(SPEEDS);
    private final Timer playTimer;
    private boolean movingSlider; // true while we move the slider ourselves
    private boolean timerMoving;  // true while the play timer is moving to the next step

    public ControlsBar(TraceModel model) {
        super(new BorderLayout(10, 0)); // the slider in the centre gets all the spare width
        this.model = model;
        setOpaque(false);
        speed.setSelectedIndex(1);
        speed.setToolTipText("playing speed");
        speed.setFocusable(false);
        slider.setFocusable(false);
        stepLabel.setToolTipText("step / steps");

        first.addActionListener(e -> model.first());
        back.addActionListener(e -> model.previous());
        forward.addActionListener(e -> model.next());
        last.addActionListener(e -> model.last());
        over.addActionListener(e -> model.stepOver());
        out.addActionListener(e -> model.stepOut());
        slider.addChangeListener(e -> {
            if (!movingSlider) {
                model.setStep(slider.getValue());
            }
        });
        playTimer = new Timer(Style.PLAY_STEP_MS, e -> {
            if (model.isLastStep()) {
                stopPlaying();
            } else {
                timerMoving = true;
                model.next();
                timerMoving = false;
            }
        });
        play.addActionListener(e -> togglePlay());
        speed.addActionListener(e -> {
            model.setSpeed(SPEED_VALUES[speed.getSelectedIndex()]);
            playTimer.setDelay((int) (Style.PLAY_STEP_MS / model.getSpeed()));
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        buttons.setOpaque(false);
        buttons.add(first);
        buttons.add(back);
        buttons.add(forward);
        buttons.add(last);
        buttons.add(over);
        buttons.add(out);
        buttons.add(play);
        JPanel position = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        position.setOpaque(false);
        position.add(stepLabel);
        position.add(speed);
        add(buttons, BorderLayout.WEST);
        add(slider, BorderLayout.CENTER);
        add(position, BorderLayout.EAST);
        stepChanged(model);
    }

    public void togglePlay() {
        if (!model.hasSteps()) {
            return;
        }
        if (playTimer.isRunning()) {
            stopPlaying();
        } else {
            if (model.isLastStep()) {
                model.first(); // play again from the start
            }
            play();
        }
    }

    public void play() {
        if (model.hasSteps() && !model.isLastStep()) {
            playTimer.start();
            play.setText("pause");
        }
    }

    public void pause() {
        stopPlaying();
    }

    public boolean isPlaying() {
        return playTimer.isRunning();
    }

    @Override
    public void traceChanged(TraceModel model) {
        stopPlaying();
        stepChanged(model);
    }

    @Override
    public void stepChanged(TraceModel model) {
        if (!timerMoving) {
            stopPlaying();
        }
        boolean has = model.hasSteps();
        movingSlider = true;
        slider.setMaximum(Math.max(model.size() - 1, 0));
        slider.setValue(model.getStep());
        movingSlider = false;
        stepLabel.setText(has ? String.format("step %03d/%03d", model.getStep() + 1, model.size()) : "step ---/---");
        boolean canGoBack = has && model.getStep() > 0;
        boolean canGoOn = has && !model.isLastStep();
        first.setEnabled(canGoBack);
        back.setEnabled(canGoBack);
        forward.setEnabled(canGoOn);
        last.setEnabled(canGoOn);
        over.setEnabled(canGoOn);
        out.setEnabled(canGoOn);
        play.setEnabled(has);
        slider.setEnabled(has);
    }

    private void stopPlaying() {
        playTimer.stop();
        play.setText("play");
    }

    // A flat button that doesn't take the keyboard focus, so the shortcut keys keep working.
    private static JButton button(String text, String tip) {
        JButton button = new JButton(text);
        button.setToolTipText(Shortcuts.label(tip));
        button.setFocusable(false);
        button.putClientProperty("JButton.buttonType", "toolBarButton"); // flat, like the toolbar's
        return button;
    }
}
