package raphael.io;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.io.StreamCorruptedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipException;
import java.util.List;
import raphael.model.SourceFile;
import raphael.model.Trace;

// A recorded run saved in a .raphael file: the program, its input and every step, so it can be
// opened again and played without running anything. A teacher can record once and replay it in class.
//
// The file is written through streams stacked on each other, each adding one job:
//   ObjectOutputStream     turns the objects (the Trace and everything in it) into bytes
//   GZIPOutputStream       compresses those bytes (a run repeats itself a lot, so it shrinks well)
//   DataOutputStream       writes the header: "RAPH" and the format's version, before the rest
//   BufferedOutputStream   collects the bytes and writes them in big pieces, not one at a time
//   FileOutputStream       puts them in the file (here Files.newOutputStream)
// Reading stacks the matching input streams in the same order.
//
// Reading objects from a file is risky if the file came from someone else: Java would build
// whatever classes the file names. So reading only allows Raphael's own model classes and the
// basic Java types they're made of (ObjectInputFilter), and limits how big and deep they can be.
//
// OOP: the Decorator pattern (each stream wraps another and adds a job, behind the same
// InputStream/OutputStream type); a record (Saved) as the small object that's written;
// exception handling: try-with-resources closes every stream, and the low-level exceptions
// become one RunFileException with a plain message.
public final class RunFile {

    public static final String EXTENSION = ".raphael";
    private static final int MAGIC = 0x52415048; // "RAPH" in ASCII: says "this is a Raphael run"
    // Goes up when the saved classes change. 2: a program can have several files (Saved.files).
    // Version 1 files still open (their files come back as null); Raphael 1.0 refuses version 2.
    private static final int VERSION = 2;

    // What a .raphael file holds. source is the program of one file; files, for a program of
    // several files, holds each of them (null for one file, and in version 1 files).
    // A record is read back by its parts' names, so a part added later is simply null in older files.
    public record Saved(String source, String input, Trace trace, List<SourceFile> files) implements Serializable {

        public Saved(String source, String input, Trace trace) {
            this(source, input, trace, null);
        }
    }

    // Only these classes may come out of a file; anything else stops the reading.
    private static final ObjectInputFilter ALLOWED = ObjectInputFilter.Config.createFilter(
            "maxdepth=64;maxrefs=10000000;maxbytes=1000000000;maxarray=10000000;"
            + "raphael.io.RunFile$Saved;raphael.model.*;"
            + "java.lang.*;java.util.*;!*");

    private RunFile() {
    }

    public static void write(Path file, Saved saved) throws IOException {
        Path partial = Files.createTempFile(file.toAbsolutePath().getParent(), "." + file.getFileName() + "-", ".tmp");
        try (OutputStream bytes = new BufferedOutputStream(Files.newOutputStream(partial))) {
            DataOutputStream header = new DataOutputStream(bytes);
            header.writeInt(MAGIC);
            header.writeInt(VERSION);
            header.flush();
            try (ObjectOutputStream objects = new ObjectOutputStream(new GZIPOutputStream(bytes))) {
                objects.writeObject(saved);
            }
        } catch (IOException e) {
            Files.deleteIfExists(partial);
            throw e;
        }
        SafeFiles.replace(partial, file); // the old file stays whole until the new one is complete
    }

    public static Saved read(Path file) throws RunFileException, IOException {
        try (InputStream bytes = new BufferedInputStream(Files.newInputStream(file))) {
            DataInputStream header = new DataInputStream(bytes);
            if (header.readInt() != MAGIC) {
                throw new RunFileException(file.getFileName() + " isn't a run saved by Raphael.");
            }
            int version = header.readInt();
            if (version > VERSION) {
                throw new RunFileException(file.getFileName() + " was saved by a newer Raphael. Update Raphael to open it.");
            }
            if (version < 1) {
                throw new RunFileException(file.getFileName() + " isn't a run saved by Raphael.");
            }
            try (ObjectInputStream objects = new ObjectInputStream(new GZIPInputStream(bytes))) {
                objects.setObjectInputFilter(ALLOWED);
                Object read = objects.readObject();
                if (!(read instanceof Saved)) {
                    throw new RunFileException(file.getFileName() + " isn't a run saved by Raphael.");
                }
                return (Saved) read;
            }
        } catch (EOFException | ZipException | StreamCorruptedException e) {
            throw new RunFileException(file.getFileName() + " is damaged: it ends too soon or has been changed.", e);
        } catch (InvalidClassException e) { // also what the filter throws for a class it doesn't allow
            throw new RunFileException(file.getFileName() + " was saved by a different version of Raphael, "
                    + "or holds something Raphael won't open.", e);
        } catch (ClassNotFoundException e) {
            throw new RunFileException(file.getFileName() + " was saved by a different version of Raphael.", e);
        }
    }
}
