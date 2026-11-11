package raphael.trace;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import raphael.compile.CompileResult;
import raphael.model.ArrayNode;
import raphael.model.EndReason;
import raphael.model.Exit;
import raphael.model.FrameSnapshot;
import raphael.model.HeapObject;
import raphael.model.NullValue;
import raphael.model.ObjectNode;
import raphael.model.PrimitiveValue;
import raphael.model.ReferenceValue;
import raphael.model.Snapshot;
import raphael.model.Trace;
import raphael.model.Value;
import raphael.toolchain.Toolchains;

// Traces a C program (compiled by CCompiler) through gdb (lldb on a Mac). A Python script inside it
// (resources/c/raphael_gdb.py, or raphael_lldb.py) stops before every line of the student's files and prints each
// step as a line of JSON; this class reads those lines and builds the same Snapshots as Java's
// tracer, so every picture, view and panel works for C unchanged.
// The program's input and output go through files (the runtime shim opens them), and a watchdog
// ends a program that stays on one line (while (x) ;) or stops responding.
// OOP: implements the Tracer interface (polymorphism: the window can't tell it from JdiTracer);
// encapsulation (the gdb process and the run's state are private).
public class CTracer implements Tracer {

    private static final String MARK = "@@R ";
    private static final long POLL_MS = 100;
    private static final long STOP_GRACE_MS = 2000; // how long a stop request may take before gdb is killed
    private static final long FIRST_LINE_MS = 30_000; // the debugger starting up, on a slow machine

    static {
        // If Raphael quits during a run, gdb and the program it's running end too.
        Runtime.getRuntime().addShutdownHook(new Thread(
                () -> ProcessHandle.current().descendants().forEach(ProcessHandle::destroyForcibly), "end traced C programs"));
    }

    private volatile RunLimits limits = RunLimits.DEFAULTS;
    private volatile List<Path> inputFiles = List.of();
    private volatile boolean stopRequested;
    private volatile int steps;

    @Override
    public void setLimits(RunLimits limits) {
        this.limits = limits;
    }

    @Override
    public void setInputFiles(List<Path> files) {
        inputFiles = List.copyOf(files);
    }

    @Override
    public void stop() {
        stopRequested = true;
    }

    @Override
    public int stepsSoFar() {
        return steps;
    }

    @Override
    public Trace trace(CompileResult program, String input) throws TraceException {
        stopRequested = false;
        steps = 0;
        RunLimits runLimits = limits;
        Path debugger = Toolchains.find(Toolchains.debugger());
        if (debugger == null) {
            throw new TraceException(Toolchains.howToInstall(Toolchains.debugger() + " to run C"), null);
        }
        Path build = program.classDir();
        try (RunFolder folder = new RunFolder(inputFiles)) {
            Path script = build.resolve("raphael_" + Toolchains.debugger() + ".py");
            try (InputStream in = CTracer.class.getResourceAsStream("/c/" + script.getFileName())) {
                Files.write(script, in.readAllBytes());
            }
            Path stdin = build.resolve("stdin.txt");
            Path stdout = build.resolve("stdout.txt");
            Path stopFile = build.resolve("stop");
            Files.writeString(stdin, input == null ? "" : input);
            Files.deleteIfExists(stdout);
            Files.deleteIfExists(stopFile);

            String exe = build.resolve(program.mainClass()).toString();
            List<String> command = Toolchains.MAC
                    ? List.of(debugger.toString(), "--batch", "--no-lldbinit", "-o", "command script import \"" + script + "\"")
                    : List.of(debugger.toString(), "-q", "-batch", "-nx", "-x", script.toString(), exe);
            ProcessBuilder builder = new ProcessBuilder(command).directory(folder.path().toFile());
            Map<String, String> env = builder.environment();
            env.put("RAPHAEL_EXE", exe);
            env.put("RAPHAEL_SRC", build.resolve("src").toString());
            env.put("RAPHAEL_RUN", folder.path().toString());
            env.put("RAPHAEL_STDIN", stdin.toString());
            env.put("RAPHAEL_STDOUT", stdout.toString());
            env.put("RAPHAEL_STOP", stopFile.toString());
            env.put("RAPHAEL_MAX_STEPS", String.valueOf(runLimits.maxSteps()));
            env.put("RAPHAEL_MAX_FRAMES", String.valueOf(TraceLimits.MAX_FRAMES));
            env.put("RAPHAEL_MAX_OBJECTS", String.valueOf(TraceLimits.MAX_OBJECTS));
            env.put("RAPHAEL_MAX_CELLS", String.valueOf(TraceLimits.MAX_ARRAY_CELLS));
            env.put("LC_ALL", "C");
            Toolchains.prepare(env);
            Process process = builder.start();
            process.getOutputStream().close();
            StringBuffer gdbErrors = new StringBuffer();
            new OutputDrainer(process.getErrorStream(), gdbErrors, "debugger stderr").start();
            Run run = new Run(program, process, stopFile, stdout, runLimits);
            try {
                run.read();
            } finally {
                run.kill();
            }
            byte[] printed = Files.exists(stdout) ? Files.readAllBytes(stdout) : new byte[0];
            if (run.failed != null) {
                throw new TraceException(Toolchains.debugger() + " couldn't run the program: " + run.failed
                        + (gdbErrors.length() > 0 ? "\n" + gdbErrors.toString().strip() : ""), null);
            }
            return run.trace(printed);
        } catch (IOException e) {
            throw new TraceException("Raphael couldn't start " + Toolchains.debugger() + " to run the program.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TraceException("The run was interrupted.", e);
        } finally {
            steps = 0;
        }
    }

    // One run: gdb's lines as they arrive, turned into steps.
    private final class Run {
        private final Process process;
        private final Path build;  // the build folder: the program's files are in its src
        private final Path stdout; // what the program has printed
        private final Path stopFile;
        private final RunLimits limits;
        private final boolean namedFiles; // a program of several files: frames name their file
        private final List<Step> recorded = new ArrayList<>();
        private Map<String, String> files = Map.of();
        private long programPid = -1;
        private EndReason endReason = EndReason.FINISHED;
        private String endMessage = "Program finished.";
        private String failed;
        private boolean forced; // the watchdog ended the run, not the script
        private boolean ended;

        Run(CompileResult program, Process process, Path stopFile, Path stdout, RunLimits limits) {
            this.build = program.classDir();
            this.stdout = stdout;
            this.process = process;
            this.stopFile = stopFile;
            this.limits = limits;
            this.namedFiles = !program.sourceFiles().isEmpty();
        }

        void read() throws IOException, InterruptedException {
            BlockingQueue<String> lines = new LinkedBlockingQueue<>();
            Thread reader = new Thread(() -> {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = in.readLine()) != null) {
                        int mark = line.indexOf(MARK);
                        if (mark >= 0) {
                            lines.add(line.substring(mark + MARK.length()));
                        }
                    }
                } catch (IOException e) {
                    // gdb has ended
                }
                lines.add(""); // the end of gdb's output
            }, "gdb stdout");
            reader.setDaemon(true);
            reader.start();

            long lastLine = System.currentTimeMillis();
            long stopAskedAt = -1;
            while (true) {
                if (stopRequested && stopAskedAt < 0) {
                    Files.writeString(stopFile, "stop"); // the script ends the run at its next step
                    stopAskedAt = System.currentTimeMillis();
                }
                long now = System.currentTimeMillis();
                if (stopAskedAt >= 0 && now - stopAskedAt > STOP_GRACE_MS) {
                    end(EndReason.STOPPED, "Stopped by you."); // stuck inside one line: it can't see the request
                    forced = true;
                    return;
                }
                // Before the first line, the debugger is still starting (seconds on a cold machine).
                long allowed = recorded.isEmpty() ? Math.max(FIRST_LINE_MS, limits.timeoutSeconds() * 1000L)
                        : limits.timeoutSeconds() * 1000L;
                if (now - lastLine >= allowed) {
                    forced = true;
                    end(EndReason.TIMEOUT, recorded.isEmpty()
                            ? "Stopped: the program didn't reach its first line in " + allowed / 1000 + " seconds."
                            : "Stopped: line " + recorded.get(recorded.size() - 1).line() + " kept running for "
                                    + limits.timeoutSeconds() + " seconds without reaching another line. Is it an infinite loop?");
                    return;
                }
                String line = lines.poll(POLL_MS, TimeUnit.MILLISECONDS);
                if (line == null) {
                    continue;
                }
                if (line.isEmpty()) { // gdb ended without saying how the run ended
                    if (!ended && failed == null) {
                        failed = "gdb stopped unexpectedly (exit code " + process.waitFor() + ").";
                    }
                    return;
                }
                lastLine = System.currentTimeMillis();
                @SuppressWarnings("unchecked")
                Map<String, Object> message = (Map<String, Object>) Json.read(line);
                if (handle(message)) {
                    return;
                }
            }
        }

        // One line from the script; true once the run is over.
        @SuppressWarnings("unchecked")
        private boolean handle(Map<String, Object> message) {
            switch ((String) message.get("kind")) {
                case "start" -> programPid = ((Number) message.get("pid")).longValue();
                case "step" -> {
                    recorded.add(step(message, null));
                    steps = recorded.size();
                }
                case "end" -> {
                    String reason = (String) message.get("reason");
                    switch (reason) {
                        case "finished" -> {
                            Object code = message.get("code");
                            int exit = code instanceof Number ? ((Number) code).intValue() : 0;
                            end(EndReason.FINISHED, exit == 0 ? "Program finished." : "Program finished, returning " + exit + " from main.");
                        }
                        case "stopped" -> end(EndReason.STOPPED, "Stopped by you.");
                        case "steps" -> end(EndReason.STEP_LIMIT,
                                JdiTracer.stepLimitMessage(recorded.get(recorded.size() - 1).snapshot(0), limits.maxSteps()));
                        case "crash" -> {
                            String signal = (String) message.get("signal");
                            Map<String, Object> at = (Map<String, Object>) message.get("step");
                            if (!signal.startsWith("SIG") && raphael.explain.CCrash.detail("SIGABRT", null, null, printedSoFar()) != null) {
                                signal = "SIGABRT"; // Windows' abort() reaches gdb without a signal name; its words are in the output
                            }
                            Step plain = step(at, null);
                            String detail = raphael.explain.CCrash.detail(signal, plain.snapshot(0), lineText(at), printedSoFar());
                            String text = crashText(signal) + (detail == null ? "" : ": " + detail);
                            Step last = step(at, text);
                            recorded.add(last);
                            end(EndReason.EXCEPTION, text + (last.line() > 0 ? " (line " + last.line() + ")" : ""));
                        }
                        default -> failed = String.valueOf(message.get("message"));
                    }
                    return true;
                }
                default -> {
                }
            }
            return false;
        }

        // The text of the line a step stopped on, from the program's files.
        @SuppressWarnings("unchecked")
        private String lineText(Map<String, Object> step) {
            List<Object> frames = (List<Object>) step.get("frames");
            if (frames.isEmpty()) {
                return null;
            }
            Map<String, Object> top = (Map<String, Object>) frames.get(0);
            try {
                List<String> lines = Files.readAllLines(build.resolve("src").resolve((String) top.get("file")));
                int line = ((Number) top.get("line")).intValue();
                return line >= 1 && line <= lines.size() ? lines.get(line - 1) : null;
            } catch (IOException | RuntimeException e) {
                return null;
            }
        }

        private String printedSoFar() {
            try {
                return Files.exists(stdout) ? Files.readString(stdout, StandardCharsets.UTF_8) : "";
            } catch (IOException | RuntimeException e) {
                return "";
            }
        }

        private void end(EndReason reason, String message) {
            ended = true;
            endReason = reason;
            endMessage = message;
        }

        // Ends gdb and the program. After the script's own end it gets a moment to finish; when the
        // watchdog ended the run (the program is stuck inside a line), both are killed at once.
        void kill() throws InterruptedException {
            if (!process.waitFor(forced ? 0 : 2, TimeUnit.SECONDS)) {
                if (programPid > 0) {
                    ProcessHandle.of(programPid).ifPresent(ProcessHandle::destroyForcibly);
                }
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        }

        @SuppressWarnings("unchecked")
        private Step step(Map<String, Object> message, String exception) {
            Map<Long, HeapObject> heap = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) message.get("heap")).entrySet()) {
                long id = Long.parseLong(entry.getKey());
                Map<String, Object> object = (Map<String, Object>) entry.getValue();
                String type = (String) object.get("type");
                if ("a".equals(object.get("k"))) {
                    List<Value> items = new ArrayList<>();
                    for (Object item : (List<Object>) object.get("items")) {
                        items.add(value(item));
                    }
                    heap.put(id, new ArrayNode(id, type, (String) object.get("elem"), items, ((Number) object.get("len")).intValue(),
                            Boolean.TRUE.equals(object.get("freed"))));
                } else {
                    Map<String, Value> fields = new LinkedHashMap<>();
                    for (Map.Entry<String, Object> field : ((Map<String, Object>) object.get("fields")).entrySet()) {
                        fields.put(field.getKey(), value(field.getValue()));
                    }
                    Map<String, String> types = new LinkedHashMap<>();
                    ((Map<String, Object>) object.get("ftypes")).forEach((name, t) -> types.put(name, (String) t));
                    heap.put(id, new ObjectNode(id, type, fields, types, null, Boolean.TRUE.equals(object.get("freed")),
                            Boolean.TRUE.equals(object.get("tag"))));
                }
            }
            List<FrameSnapshot> frames = new ArrayList<>();
            for (Object f : (List<Object>) message.get("frames")) {
                Map<String, Object> frame = (Map<String, Object>) f;
                String file = (String) frame.get("file");
                frames.add(new FrameSnapshot(unitOf(file), (String) frame.get("fn"), ((Number) frame.get("line")).intValue(),
                        values((Map<String, Object>) frame.get("locals")), namedFiles ? file : null));
            }
            Map<String, Value> globals = new LinkedHashMap<>();
            String unit = frames.isEmpty() ? "main" : frames.get(0).getClassName();
            values((Map<String, Object>) message.get("globals")).forEach((name, v) -> globals.put(unit + "." + name, v));
            List<Exit> exits = new ArrayList<>();
            for (Object e : (List<Object>) message.get("exits")) {
                Map<String, Object> exit = (Map<String, Object>) e;
                Object returned = exit.get("returned");
                exits.add(new Exit(unitOf((String) exit.get("file")), (String) exit.get("fn"), returned == null ? null : value(returned)));
            }
            Object newFiles = message.get("files");
            if (newFiles != null) {
                Map<String, String> now = new TreeMap<>();
                ((Map<String, Object>) newFiles).forEach((name, text) -> now.put(name, (String) text));
                files = Collections.unmodifiableMap(now);
            }
            return new Step(frames, heap, globals, ((Number) message.get("out")).intValue(), exception, files, exits);
        }

        private Map<String, Value> values(Map<String, Object> json) {
            Map<String, Value> values = new LinkedHashMap<>();
            json.forEach((name, v) -> values.put(name, value(v)));
            return values;
        }

        @SuppressWarnings("unchecked")
        private Value value(Object json) {
            Map<String, Object> v = (Map<String, Object>) json;
            if (v.containsKey("r")) {
                return new ReferenceValue(((Number) v.get("r")).longValue());
            }
            if (v.containsKey("n")) {
                return NullValue.C; // C's word
            }
            return new PrimitiveValue((String) v.get("p"));
        }

        // The whole run. printed: everything the program wrote, to turn each step's byte count into characters.
        Trace trace(byte[] printed) {
            String output = new String(printed, StandardCharsets.UTF_8);
            List<Snapshot> snapshots = new ArrayList<>();
            for (Step step : recorded) {
                int bytes = Math.min(step.outputBytes(), printed.length);
                snapshots.add(step.snapshot(new String(printed, 0, bytes, StandardCharsets.UTF_8).length()));
            }
            return new Trace(snapshots, output, endReason, endMessage);
        }
    }

    // A frame's "class" in C is its file, without the ending: main.c → main.
    private static String unitOf(String file) {
        String name = file == null ? "main" : file.substring(file.lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    // What a signal means, in words.
    static String crashText(String signal) {
        if (signal == null) {
            return "Crashed";
        }
        return switch (signal) {
            case "SIGSEGV" -> "Segmentation fault";
            case "SIGFPE" -> "Arithmetic error";
            case "SIGABRT" -> "Aborted";
            case "SIGBUS" -> "Bus error";
            case "SIGILL" -> "Illegal instruction";
            default -> "Crashed (" + signal + ")";
        };
    }

    // A step as read, before its output length is known in characters.
    private record Step(List<FrameSnapshot> frames, Map<Long, HeapObject> heap, Map<String, Value> globals,
                        int outputBytes, String exception, Map<String, String> files, List<Exit> exits) {

        int line() {
            return frames.isEmpty() ? -1 : frames.get(0).getLine();
        }

        Snapshot snapshot(int outputLength) {
            return new Snapshot(frames, heap, globals, outputLength, exception, null, files, exits, List.of(), 0);
        }
    }
}
