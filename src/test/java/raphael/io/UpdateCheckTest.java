package raphael.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UpdateCheckTest {

    @Test
    void versionsAreComparedAsNumbers() {
        assertTrue(UpdateCheck.isNewer("1.10.0", "1.9.2"));
        assertTrue(UpdateCheck.isNewer("2.0", "1.9.9"));
        assertTrue(UpdateCheck.isNewer("1.0.1", "1.0.0"));
        assertFalse(UpdateCheck.isNewer("1.0.0", "1.0.0"));
        assertFalse(UpdateCheck.isNewer("0.9.0", "1.0.0"));
        assertFalse(UpdateCheck.isNewer("1.0", "1.0.0"));
    }

    @Test
    void readsTheFieldsOfGitHubsAnswer() {
        String json = "{\"url\": \"x\", \"html_url\": \"https://github.com/electricwhitener/raphael/releases/tag/v1.1.0\",\n"
                + "  \"tag_name\" : \"v1.1.0\", \"name\": \"Raphael 1.1.0\"}";
        assertEquals("v1.1.0", UpdateCheck.field(json, "tag_name"));
        assertEquals("https://github.com/electricwhitener/raphael/releases/tag/v1.1.0", UpdateCheck.field(json, "html_url"));
        assertNull(UpdateCheck.field(json, "missing"));
    }
}
