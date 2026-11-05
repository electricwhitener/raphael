package raphael.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DraftStoreTest {

    @TempDir
    Path folder;

    @Test
    void savesLoadsAndClears() throws IOException {
        DraftStore draft = new DraftStore(folder.resolve("draft.java"));
        assertNull(draft.load(), "no draft at first");
        draft.save("class A {}");
        draft.save("class B {}");
        assertEquals("class B {}", draft.load());
        draft.clear();
        assertNull(draft.load());
        draft.clear(); // clearing twice is fine
    }
}
