package raphael.trace;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

// Copies everything the program prints on one stream (stdout or stderr) into a shared buffer.
// It runs on its own thread: if nobody read the output, the pipe would fill up and the program would freeze.
// OOP: inheritance (extends Thread) and overriding (run).
public class OutputDrainer extends Thread {

    private final InputStream in;
    private final StringBuffer out; // StringBuffer is safe to share between threads

    public OutputDrainer(InputStream in, StringBuffer out, String name) {
        super(name);
        this.in = in;
        this.out = out;
        setDaemon(true); // don't keep Raphael alive just for this thread
    }

    @Override
    public void run() {
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            char[] buffer = new char[1024];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                out.append(buffer, 0, count);
            }
        } catch (IOException e) {
            // The program has ended, so there's nothing more to read.
        }
    }
}
