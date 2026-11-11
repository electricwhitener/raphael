package raphael.trace;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import raphael.model.TypedLine;

// The input of a program that runs with the terminal: each line typed there goes to the
// program's stdin as soon as Enter is pressed, and the recording waits while the program waits
// for it. Ctrl+D ends the input (the program reads the end, as in a real terminal).
// Lines given beforehand (an example's input) are typed for the user, one each time the program
// is found waiting, so the terminal shows them where a person would have typed them.
// The program counts as waiting when no step has come for WAIT_MS and the line it's running
// reads input (scanf, nextInt…); the tracer asks readsAt(), and pauses its timeout meanwhile.
// Threads: the terminal calls type() and end() on Swing's thread; the tracer calls the rest on
// its own; a pump thread writes the lines to the program.
// OOP: encapsulation (the queue and the record of typed lines are private; a thread-safe class).
public final class LiveInput {

    public static final long WAIT_MS = 300; // no step for this long, on a line that reads: it's waiting

    private static final String END = new String("end"); // the queue's "no more input" (compared by identity)

    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
    private final Deque<String> ahead = new ArrayDeque<>();
    private final BiPredicate<String, Integer> readsAt;
    private final List<TypedLine> typed = new ArrayList<>();
    private final StringBuilder text = new StringBuilder();
    private volatile boolean ended;
    private volatile boolean waiting;
    private volatile IntSupplier steps = () -> 0;
    private volatile IntSupplier printed = () -> 0;

    // given: lines to type for the user (may be empty); readsAt: whether a line of a file (null:
    // the program's only file) reads input.
    public LiveInput(String given, BiPredicate<String, Integer> readsAt) {
        this.readsAt = readsAt;
        if (given != null && !given.isEmpty()) {
            for (String line : given.split("(?<=\n)")) {
                ahead.add(line.endsWith("\n") ? line : line + "\n");
            }
        }
    }

    // ---- the terminal's side ----

    // A line typed (its line end is added if missing), after what the program has printed so far.
    // Ignored once the input has ended.
    public synchronized void type(String line) {
        type(line, printed.getAsInt());
    }

    // The same, typed where the terminal showed `at` characters of output (where the user saw the prompt).
    public synchronized void type(String line, int at) {
        if (ended) {
            return;
        }
        String withEnd = line.endsWith("\n") ? line : line + "\n";
        typed.add(new TypedLine(steps.getAsInt(), at, withEnd));
        text.append(withEnd);
        queue.add(withEnd);
    }

    // Ctrl+D: no more input.
    public synchronized void end() {
        if (!ended) {
            ended = true;
            queue.add(END);
        }
    }

    public boolean isEnded() {
        return ended;
    }

    // True while the program waits for a line.
    public boolean isWaiting() {
        return waiting && !ended;
    }

    // The lines typed so far (the given ones too), in order.
    public synchronized List<TypedLine> typed() {
        return List.copyOf(typed);
    }

    // Everything the program was given, as one text (what it read, for the Files view and the moments).
    public synchronized String text() {
        return text.toString();
    }

    // ---- the tracer's side ----

    // How the tracer counts its steps and what the program has printed (in characters), so each
    // typed line can be placed in the run.
    void attach(IntSupplier steps, IntSupplier printed) {
        this.steps = steps;
        this.printed = printed;
    }

    // Whether the program, stopped inside this line for WAIT_MS, is waiting for input.
    boolean readsAt(String file, int line) {
        return !ended && line > 0 && readsAt.test(file, line);
    }

    // The tracer found the program waiting: a given line is typed for the user, if one is left
    // and nothing typed is still on its way.
    void programWaits() {
        waiting = true;
        synchronized (this) {
            if (queue.isEmpty() && !ahead.isEmpty()) {
                type(ahead.poll());
            }
        }
    }

    // A step came: the program isn't waiting (any more).
    void programMoved() {
        waiting = false;
    }

    // Starts the thread that writes the typed lines to the program, opening its stdin with
    // `open` (which may wait until the program has made it), until the input ends or `over` says
    // the run is over. Returns the thread, so the tracer can wait for it.
    Thread pump(Opener open, BooleanSupplier over) {
        Thread thread = new Thread(() -> {
            OutputStream stdin = null;
            try {
                stdin = open.open(over);
                if (stdin == null) {
                    return; // the run ended before the program opened its input
                }
                while (!over.getAsBoolean()) {
                    String line = queue.poll(50, TimeUnit.MILLISECONDS);
                    if (line == END) {
                        break;
                    }
                    if (line != null) {
                        stdin.write(line.getBytes(StandardCharsets.UTF_8));
                        stdin.flush();
                    }
                }
            } catch (IOException e) {
                // the program ended (its stdin closed) while lines were still being typed
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                if (stdin != null) {
                    try {
                        stdin.close(); // the program reads the end of its input
                    } catch (IOException ignored) {
                        // it had already ended
                    }
                }
            }
        }, "terminal input");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    // Opens the program's stdin for writing; null if the run ended first.
    interface Opener {
        OutputStream open(BooleanSupplier over) throws IOException, InterruptedException;
    }
}
