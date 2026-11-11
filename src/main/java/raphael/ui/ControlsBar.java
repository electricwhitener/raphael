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

// The step controls: first, back, play/pause, forward, last, then skip call and finish call, the
// slider, "step 46 of 67" and the speed. "Skip call" (step over) skips the inside of method calls
// on this line; "finish call" (step out) runs until this method returns.
// Playing uses a Swing Timer that moves one step at a time. Moving to a step any other way (a
// key, a button, the slider, a line number) pauses it, so you stay on the step you asked for.
// OOP: inheritance (extends JPanel); implements TraceListener (Observer pattern).
public class ControlsBar extends JPanel implements TraceListener, MomentMode.Watcher {

    private static final String[] SPEEDS = {"0.5×", "1×", "2×", "4×", "8×"};
    private static final double[] SPEED_VALUES = {0.5, 1, 2, 4, 8};

    private final TraceModel model;
    private final JButton first = button(Icons.Glyph.FIRST, "First step", "First step  (Home)");
    private final JButton back = button(Icons.Glyph.BACK, "Step back", "Step back  (← or Alt+←)");
    private final JButton forward = button(Icons.Glyph.FORWARD, "Step forward", "Step forward  (→ or Alt+→)");
    private final JButton last = button(Icons.Glyph.LAST, "Last step", "Last step  (End)");
    private final JButton over = button(Icons.Glyph.STEP_OVER, "Skip call",
            "Skip call (step over): don't go inside the calls on this line  (Alt+↓)");
    private final JButton out = button(Icons.Glyph.STEP_OUT, "Finish call",
            "Finish call (step out): run until this method returns  (Alt+↑)");
    private final JButton play = button(Icons.Glyph.PLAY, "Play", "Play or pause  (Space)");
    private Runnable onPausedAtMoment = () -> { }; // the program says so in the status bar

    public void setOnPausedAtMoment(Runnable onPausedAtMoment) {
        this.onPausedAtMoment = onPausedAtMoment;
    }

    // The step slider, with a small mark under each key moment: blue where play pauses, grey for a note.
    private final JSlider slider = new JSlider(0, 0, 0) {
        @Override
        protected void paintComponent(java.awt.Graphics g) {
            super.paintComponent(g);
            if (getMaximum() <= 0) {
                return;
            }
            int thumb = 8; // about half the thumb's width: where the track starts and ends
            int left = getInsets().left + thumb;
            int width = getWidth() - getInsets().left - getInsets().right - 2 * thumb;
            for (raphael.explain.Moment moment : model.getMoments()) {
                int x = left + (int) Math.round(width * (moment.step() / (double) getMaximum()));
                boolean here = moment.step() == model.getStep();
                int w = moment.pauses() || here ? 3 : 2;      // a key moment is wider and taller than a note,
                int h = here ? 8 : moment.pauses() ? 6 : 4;   // so the colour isn't the only difference
                g.setColor(moment.pauses() ? Style.ACCENT : Style.MUTED);
                g.fillRect(x - w / 2, getHeight() - h - 1, w, h);
            }
        }
    };
    // "step 5 of 47", in the softer ink (it's a readout, not a control); asked each time it paints, for dark mode.
    private final JLabel stepLabel = new JLabel("step – of –") {
        @Override
        public java.awt.Color getForeground() {
            return Style.INK_SOFT;
        }
    };
    private final JComboBox<String> speed = new JComboBox<>(SPEEDS);
    // Key moments: off, shown, or shown and paused at (one choice for every tab; see MomentMode).
    private final JComboBox<MomentMode> moments = new JComboBox<>(MomentMode.values());
    private boolean showingMode; // true while the box is being set to the choice made elsewhere
    private final Timer playTimer;
    private boolean movingSlider; // true while we move the slider ourselves
    private boolean timerMoving;  // true while the play timer is moving to the next step

    public ControlsBar(TraceModel model) {
        super(new BorderLayout(Style.SPACE_3, 0)); // the slider in the centre gets all the spare width
        this.model = model;
        setOpaque(false);
        speed.setSelectedIndex(1);
        speed.setToolTipText("playing speed");
        speed.getAccessibleContext().setAccessibleName("Playback speed");
        slider.getAccessibleContext().setAccessibleName("Step position");
        stepLabel.setToolTipText("this step, and how many the run has");
        stepLabel.setFont(Style.UI_FONT.deriveFont(12f));

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
                if (MomentMode.current() == MomentMode.PAUSE && model.pausesAt(model.getStep())) {
                    stopPlaying(); // a key moment: the card explains it; Space goes on
                    onPausedAtMoment.run();
                }
            }
        });
        play.addActionListener(e -> togglePlay());
        moments.setSelectedItem(MomentMode.current());
        moments.setToolTipText(MomentMode.current().description());
        moments.getAccessibleContext().setAccessibleName("Key moments");
        moments.addActionListener(e -> {
            if (!showingMode && moments.getSelectedItem() != null) {
                MomentMode.set((MomentMode) moments.getSelectedItem()); // every tab follows, this one too
            }
        });
        MomentMode.watch(this);
        speed.addActionListener(e -> {
            model.setSpeed(SPEED_VALUES[speed.getSelectedIndex()]);
            playTimer.setDelay((int) (Style.PLAY_STEP_MS / model.getSpeed()));
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        buttons.setOpaque(false);
        buttons.add(first);
        buttons.add(back);
        buttons.add(play);
        buttons.add(forward);
        buttons.add(last);
        buttons.add(javax.swing.Box.createHorizontalStrut(Style.SPACE_2));
        buttons.add(over);
        buttons.add(out);
        JPanel position = new JPanel(new FlowLayout(FlowLayout.RIGHT, Style.SPACE_2, 0));
        position.setOpaque(false);
        position.add(stepLabel);
        position.add(moments);
        position.add(speed);
        add(buttons, BorderLayout.WEST);
        add(slider, BorderLayout.CENTER);
        add(position, BorderLayout.EAST);
        stepChanged(model);
    }

    MomentMode momentChoice() { // what the box shows (for tests)
        return (MomentMode) moments.getSelectedItem();
    }

    @Override
    public void momentModeChanged(MomentMode mode) {
        showingMode = true;
        moments.setSelectedItem(mode);
        showingMode = false;
        moments.setToolTipText(mode.description());
        model.momentsChanged(); // the cards, marks and list follow
    }

    @Override
    public void momentsChanged(TraceModel model) {
        stepChanged(model);
        slider.repaint(); // the marks
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
            show(play, Icons.Glyph.PAUSE, "Pause");
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
        stepLabel.setText(has ? "step " + (model.getStep() + 1) + " of " + model.size() : "step – of –");
        // As wide as the run's last step needs, so the label doesn't grow while it counts.
        String widest = "step " + model.size() + " of " + model.size();
        stepLabel.setPreferredSize(new java.awt.Dimension(stepLabel.getFontMetrics(stepLabel.getFont()).stringWidth(widest),
                stepLabel.getPreferredSize().height));
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
        show(play, Icons.Glyph.PLAY, "Play");
    }

    // A flat 32 px icon button. Tab can reach it, but a click doesn't take the keyboard focus, so
    // after clicking, Space and the arrow keys still step the program.
    static JButton button(Icons.Glyph glyph, String name, String tip) {
        JButton button = new JButton(Icons.of(glyph, Icons.Role.TOOL));
        button.setToolTipText(Shortcuts.label(tip));
        button.getAccessibleContext().setAccessibleName(name); // what a screen reader says
        button.setRequestFocusEnabled(false);
        button.putClientProperty("JButton.buttonType", "toolBarButton"); // flat, like the toolbar's
        java.awt.Dimension square = new java.awt.Dimension(32, 32);
        button.setPreferredSize(square);
        button.setMinimumSize(square); // a toolbar would otherwise squeeze it to 24 px high
        button.setMaximumSize(square);
        return button;
    }

    private static void show(JButton button, Icons.Glyph glyph, String name) {
        button.setIcon(Icons.of(glyph, Icons.Role.TOOL));
        button.getAccessibleContext().setAccessibleName(name);
    }

    // Off on the welcome page, where there's nothing to play.
    public void setSpeedEnabled(boolean enabled) {
        speed.setEnabled(enabled);
    }
}
