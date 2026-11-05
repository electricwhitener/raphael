package raphael.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SafeFilesTest {

    @TempDir
    Path folder;

    @Test
    void writeReplacesTheFileAndLeavesNothingElse() throws IOException {
        Path file = folder.resolve("Main.java");
        SafeFiles.write(file, "first");
        SafeFiles.write(file, "héllo 世界");
        assertEquals("héllo 世界", SafeFiles.readCode(file));
        try (Stream<Path> files = Files.list(folder)) {
            assertEquals(1, files.count(), "no temporary file is left behind");
        }
    }

    @Test
    void writingIntoAMissingFolderFailsWithoutLeavingAnything() {
        assertThrows(IOException.class, () -> SafeFiles.write(folder.resolve("gone").resolve("Main.java"), "x"));
    }

    @Test
    void readCodeRefusesHugeAndBinaryFiles() throws IOException {
        Path huge = folder.resolve("Huge.java");
        Files.write(huge, new byte[(int) SafeFiles.MAX_CODE_BYTES + 1]);
        assertTrue(assertThrows(IOException.class, () -> SafeFiles.readCode(huge)).getMessage().contains("too big"));
        Path binary = folder.resolve("Picture.java");
        Files.write(binary, new byte[] {(byte) 0x89, 'P', 'N', 'G', 0, 0});
        assertTrue(assertThrows(IOException.class, () -> SafeFiles.readCode(binary)).getMessage().contains("isn't a text file"));
    }

    @Test
    void readCodeAcceptsAFileThatIsNotValidUtf8() throws IOException {
        Path old = folder.resolve("Old.java");
        Files.write(old, new byte[] {'i', 'n', 't', ' ', (byte) 0xE9, ';'}); // é in an old Windows encoding
        assertTrue(SafeFiles.readCode(old).startsWith("int "));
    }

    @Test
    void describeGivesAPlainReason() {
        assertTrue(SafeFiles.describe(new NoSuchFileException("/x/Main.java")).contains("doesn't exist"));
        assertEquals("disk full", SafeFiles.describe(new IOException("disk full")));
    }
}
