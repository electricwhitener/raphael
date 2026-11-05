package raphael.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.GraphicsEnvironment;
import org.junit.jupiter.api.Test;
import raphael.trace.RunLimits;

class SettingsDialogTest {

    // What the dialog is opened with is exactly what it hands back when nothing is changed.
    @Test
    void showsAndReturnsTheCurrentValues() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "needs a screen");
        SettingsDialog.Values current = new SettingsDialog.Values(new RunLimits(5000, 12, 512), 16, true);
        SettingsDialog dialog = new SettingsDialog(null, current);
        assertEquals(current, dialog.read());
        dialog.dispose();
    }
}
