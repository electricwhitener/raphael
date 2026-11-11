package raphael.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import raphael.io.ExampleCatalog;
import raphael.lang.Languages;

class ExamplesPanelTest {

    // The tree holds every Java case; a search keeps only the ones whose words match.
    @Test
    void searchFiltersTheCases() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            List<ExampleCatalog.Example> previewed = new ArrayList<>();
            ExamplesPanel panel = new ExamplesPanel(new ExamplesPanel.Actions() {
                @Override
                public void preview(ExampleCatalog.Example example) {
                    previewed.add(example);
                }

                @Override
                public void open(ExampleCatalog.Example example) {
                }
            });
            panel.showLanguage(Languages.JAVA);
            assertEquals(ExampleCatalog.all().stream().filter(e -> e.language() == Languages.JAVA).count(), panel.shownCases());
            panel.setSearch("red-black");
            assertEquals(2, panel.shownCases());
            panel.setSearch("insert tail");
            assertTrue(panel.shownCases() >= 1);
            panel.showLanguage(Languages.C);
            assertEquals(0, panel.shownCases(), "no C examples yet");
        });
    }
}
