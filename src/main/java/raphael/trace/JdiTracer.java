package raphael.trace;

import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.Bootstrap;
import com.sun.jdi.ClassType;
import com.sun.jdi.Field;
import com.sun.jdi.IncompatibleThreadStateException;
import com.sun.jdi.StackFrame;
import com.sun.jdi.Location;
import com.sun.jdi.Method;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.StringReference;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.VMDisconnectedException;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.IllegalConnectorArgumentsException;
import com.sun.jdi.connect.ListeningConnector;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventQueue;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.ExceptionEvent;
import com.sun.jdi.event.ModificationWatchpointEvent;
import com.sun.jdi.event.VMDeathEvent;
import com.sun.jdi.event.VMDisconnectEvent;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.ExceptionRequest;
import com.sun.jdi.request.ModificationWatchpointRequest;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import raphael.compile.CompileResult;
import raphael.model.EndReason;
import raphael.model.FrameSnapshot;
import raphael.model.Names;
import raphael.model.Snapshot;
import raphael.model.ThrownException;
import raphael.model.Trace;

// Runs the student's program in a second JVM in debug mode, using JDI (the Java Debug Interface),
// and pauses it before every line of the student's code. At each pause, HeapReader takes a Snapshot.
// How it works: when one of the student's classes is loaded, we put a breakpoint on the start of
// each of its lines. JDI then sends us events ("a class was loaded", "a breakpoint was hit",
// "an exception was thrown"), each one with the program paused. We handle the event, then let the
// program continue. (A breakpoint fires on every loop iteration, even in a one-line loop.)
// OOP: implements the Tracer interface. Encapsulation: every JDI detail stays private in here.
public class JdiTracer implements Tracer {

    // Java's own packages. We never pause inside them, and we don't draw their objects.
    private static final String[] JDK_PACKAGES = {"java.", "javax.", "sun.", "jdk."};
    // Raphael's own class that runs inside the program (the ParentWatch agent), left out by its
    // exact name: a student's program may have a package called raphael too.
    private static final String AGENT_CLASS = ParentWatch.class.getName();
    private static final long POLL_MS = 100; // how often to look up from waiting, to see if stop was pressed

    private static final Logger LOG = Logger.getLogger(JdiTracer.class.getName());

    private volatile Run current; // the run in progress, or null; volatile because the window's thread reads it
    private volatile RunLimits limits = RunLimits.DEFAULTS;
    private volatile List<Path> inputFiles = List.of();
    private static final int START_TIMEOUT_MS = 20_000; // a program that hasn't connected by then never will
    boolean remember = true; // tests turn this off to check that remembering objects changes nothing
    boolean recordExits = true; // the timing check turns this off, to see what recording returns costs
    int lastObjectsRead;     // how many objects the last run read from the program, for tests

    // When Raphael quits, Java runs this hook on its way out. It ends every program Raphael
    // started, even one still starting up, whose Process isn't known until launch() returns.
    // (If Raphael is killed outright, hooks don't run; ParentWatch covers that.)
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(
                () -> ProcessHandle.current().children().forEach(ProcessHandle::destroyForcibly),
                "end traced programs"));
    }

    // Why a run hit the step limit, for any language's tracer.
    static String stepLimitMessage(Snapshot last, int maxSteps) {
        String method = recursingMethod(last);
        return method != null
                ? "Stopped after " + maxSteps + " steps, with " + method + " calling itself again and again. Is its base case missing?"
                : "Stopped after " + maxSteps + " steps. If it isn't an infinite loop, try a smaller input, or allow more steps in settings.";
    }

    // The method that fills the whole call stack, if one does: a recursion that never stops
    // long before Java's own StackOverflowError, which only comes thousands of calls later.
    private static String recursingMethod(Snapshot step) {
        List<FrameSnapshot> frames = step.getFrames();
        if (frames.size() < TraceLimits.MAX_FRAMES) {
            return null;
        }
        String method = frames.get(0).getMethodName();
        for (FrameSnapshot frame : frames) {
            if (!frame.getMethodName().equals(method)) {
                return null;
            }
        }
        return method + "()";
    }

    @Override
    public Trace trace(CompileResult program, String input) throws TraceException {
        RunLimits runLimits = limits; // one set of limits for the whole run, even if settings change meanwhile
        try (RunFolder folder = new RunFolder(inputFiles)) {
            Launched launched = launch(program, runLimits, folder.path());
            current = new Run(launched.vm(), launched.process(), folder, runLimits, remember, program.sourceFiles());
            current.recordExits = recordExits;
            current.facts = program.facts();
            try {
                return current.record(input);
            } finally {
                lastObjectsRead = current.reader.objectsRead();
                current = null;
                if (launched.process().isAlive()) { // whatever happened above, the program never runs on
                    launched.process().destroyForcibly();
                    launched.process().onExit().join(); // let go of its folder before the folder is deleted
                }
            }
        } catch (IOException e) {
            throw new TraceException("Could not make a folder for the program to run in: " + e.getMessage(), e);
        }
    }

    @Override
    public void setInputFiles(List<Path> files) {
        inputFiles = List.copyOf(files);
    }

    @Override
    public void setLimits(RunLimits limits) {
        this.limits = limits;
    }

    @Override
    public void stop() {
        Run run = current;
        if (run != null) {
            run.stopRequested = true;
        }
    }

    @Override
    public int stepsSoFar() {
        Run run = current;
        return run == null ? 0 : run.steps;
    }

    // True for classes that belong to Java itself rather than to the student.
    public static boolean isJdkClass(String className) {
        for (String prefix : JDK_PACKAGES) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static final Object CONNECTING = new Object(); // see launch()

    // A program started for one run: the debugger's connection to it, and its process.
    private record Launched(VirtualMachine vm, Process process) {
    }

    // Starts the program in a new JVM, paused before it does anything, in its own folder, so the
    // files it writes land there. JDI's ready-made launcher can't choose the folder, so Raphael
    // starts the JVM itself (ProcessBuilder) and tells it to connect back to a port Raphael is
    // listening on, on this computer only (JDI's listening connector).
    private Launched launch(CompileResult program, RunLimits runLimits, Path folder) throws TraceException {
        ListeningConnector connector = null;
        for (ListeningConnector listening : Bootstrap.virtualMachineManager().listeningConnectors()) {
            if (listening.transport().name().equals("dt_socket")) {
                connector = listening;
            }
        }
        if (connector == null) {
            throw new TraceException("Could not start the program: this Java has no debugger connection (dt_socket).", null);
        }
        Map<String, Connector.Argument> arguments = connector.defaultArguments();
        arguments.get("localAddress").setValue("127.0.0.1"); // only programs on this computer can connect
        arguments.get("port").setValue("0");                // any free port
        arguments.get("timeout").setValue(String.valueOf(START_TIMEOUT_MS));
        // JDI keeps one listener per set of arguments, and every run's arguments are the same, so
        // two programs starting at once (two tabs) would close each other's listener. One program
        // connects at a time; the runs themselves still go side by side.
        synchronized (CONNECTING) {
            return connect(program, runLimits, folder, connector, arguments);
        }
    }

    private Launched connect(CompileResult program, RunLimits runLimits, Path folder, ListeningConnector connector,
                             Map<String, Connector.Argument> arguments) throws TraceException {
        try {
            String address = connector.startListening(arguments);
            try {
                // UTF-8 keeps printed text intact; -Xmx caps the memory the program may use; the
                // agent ends it if Raphael is killed.
                List<String> command = new ArrayList<>(List.of(javaCommand(),
                        "-agentlib:jdwp=transport=dt_socket,server=n,suspend=y,address=" + address,
                        "-cp", program.classDir().toString(),
                        "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
                        "-Xmx" + runLimits.memoryMb() + "m"));
                try {
                    command.add("-javaagent:" + ParentWatch.jar());
                } catch (IOException e) {
                    // Without the agent the program still runs; only the "Raphael was killed" safety net is missing.
                    LOG.log(Level.WARNING, "Couldn't write the ParentWatch agent jar", e);
                }
                command.add(program.mainClass());
                Process process = new ProcessBuilder(command).directory(folder.toFile()).start();
                try {
                    return new Launched(connector.accept(arguments), process);
                } catch (IOException | IllegalConnectorArgumentsException e) {
                    process.destroyForcibly();
                    throw e;
                }
            } finally {
                connector.stopListening(arguments);
            }
        } catch (IOException | IllegalConnectorArgumentsException e) {
            throw new TraceException("Could not start the program: " + e.getMessage(), e);
        }
    }

    // The java command of the Java that Raphael itself runs on.
    private static String javaCommand() {
        boolean windows = System.getProperty("os.name", "").startsWith("Windows");
        return Path.of(System.getProperty("java.home"), "bin", windows ? "java.exe" : "java").toString();
    }

    // One run of one program: everything that changes while it runs.
    // OOP: a private nested class, only used inside JdiTracer.
    // The program's file a line is in (from the compiler's list), or null if it isn't known.
    static String fileOf(Location at, Map<String, String> sourceFiles) {
        try {
            String type = at.declaringType().name();
            int dot = type.lastIndexOf('.');
            String folder = dot < 0 ? "" : type.substring(0, dot).replace('.', '/') + "/";
            return sourceFiles.get(folder + at.sourceName());
        } catch (com.sun.jdi.AbsentInformationException e) {
            return null; // compiled without a source file name
        }
    }

    private static class Run {

        private final VirtualMachine vm;
        private final Process process;
        private final RunFolder folder;
        private final RunLimits limits;
        private final EventRequestManager requests;
        private final StringBuffer output = new StringBuffer();
        private final List<ReferenceType> studentClasses = new ArrayList<>();
        private final Map<String, String> sourceFiles; // Java's name for each running file → the program's path
        private final HeapReader reader;
        private final List<Snapshot> snapshots = new ArrayList<>();
        private EndReason endReason = EndReason.FINISHED;
        private String endMessage = "Program finished.";
        private ThreadReference firstThread; // the thread of the first step
        private ThrownException justCaught;  // shown on the next step, the first line of its catch
        boolean recordExits = true;
        raphael.analysis.CodeFacts facts = new raphael.analysis.CodeFacts();
        // Method-exit events make Java run the program without its fast compiled code, Java's
        // own library included, while they're on. So they're off while the JVM starts, and off
        // during a line of main that only calls Java's own methods (Collections.sort(list) would
        // take seconds instead of a fraction of one); they're on for every other line. See exitsFor().
        private com.sun.jdi.request.MethodExitRequest returns;
        private boolean returnsOn;
        // Since the step before: the student's methods that returned, and the fields written.
        // Both come from events that don't pause the program, and go on the next step.
        private final List<raphael.model.Exit> exits = new ArrayList<>();
        private final List<raphael.model.FieldWrite> writes = new ArrayList<>();
        private int writesLeftOut;
        private volatile boolean stopRequested;
        private volatile int steps;

        Run(VirtualMachine vm, Process process, RunFolder folder, RunLimits limits, boolean remember,
            Map<String, String> sourceFiles) {
            this.sourceFiles = sourceFiles;
            this.reader = new HeapReader(studentClasses, sourceFiles);
            this.vm = vm;
            this.process = process;
            this.folder = folder;
            this.limits = limits;
            if (!remember) {
                reader.stopRemembering();
            }
            this.requests = vm.eventRequestManager();
        }

        Trace record(String input) throws TraceException {
            OutputDrainer stdout = new OutputDrainer(process.getInputStream(), output, "stdout");
            OutputDrainer stderr = new OutputDrainer(process.getErrorStream(), output, "stderr");
            stdout.start();
            stderr.start();
            sendInput(process.getOutputStream(), input);

            // Tell us when each of the student's classes is loaded...
            ClassPrepareRequest classLoaded = requests.createClassPrepareRequest();
            for (String prefix : JDK_PACKAGES) {
                classLoaded.addClassExclusionFilter(prefix + "*");
            }
            classLoaded.addClassExclusionFilter(AGENT_CLASS + "*"); // also its nested classes, ParentWatch$1
            classLoaded.enable();
            // ...and when an exception is thrown, caught or not. One nothing catches ends the run;
            // one the student's code throws or catches is shown on the step its catch begins.
            ExceptionRequest exceptions = requests.createExceptionRequest(null, true, true);
            exceptions.enable();
            // ...and each time one of the student's methods returns, with what it returned, so a
            // return inside one line (return n * factorial(n - 1)) isn't lost. SUSPEND_NONE: the
            // program doesn't stop for these. (Class filters would all have to match at once, so
            // Java's own classes are left out with exclusion filters instead.)
            if (recordExits && vm.canGetMethodReturnValues()) {
                returns = requests.createMethodExitRequest();
                for (String prefix : JDK_PACKAGES) {
                    returns.addClassExclusionFilter(prefix + "*");
                }
                returns.addClassExclusionFilter(AGENT_CLASS + "*");
                returns.setSuspendPolicy(EventRequest.SUSPEND_NONE); // switched on at the first line (exitsFor)
            }

            try {
                // The program starts paused, and the first event ("the VM started") releases it.
                // Calling vm.resume() here as well would be one release too many: it could cancel
                // the pause for "a class was loaded", and the program would run with no breakpoints.
                handleEvents();
            } catch (VMDisconnectedException e) {
                // The program has ended, or we ended it.
            } catch (InterruptedException e) {
                stopProgram();
                Thread.currentThread().interrupt();
                throw new TraceException("Tracing was interrupted.", e);
            }
            waitFor(stdout);
            waitFor(stderr);
            return new Trace(snapshots, output.toString(), endReason, endMessage);
        }

        private void handleEvents() throws InterruptedException {
            EventQueue queue = vm.eventQueue();
            long lastEvent = System.currentTimeMillis();
            while (true) {
                if (stopRequested) {
                    end(EndReason.STOPPED, "Stopped by you.");
                    stopProgram();
                    return;
                }
                EventSet events = queue.remove(POLL_MS);
                if (events == null) {
                    if (System.currentTimeMillis() - lastEvent >= limits.timeoutSeconds() * 1000L) {
                        end(EndReason.TIMEOUT, "Stopped: the program did nothing for "
                                + limits.timeoutSeconds() + " seconds.");
                        stopProgram();
                        return;
                    }
                    continue;
                }
                lastEvent = System.currentTimeMillis();
                for (Event event : events) {
                    if (event instanceof VMDisconnectEvent) {
                        return;
                    }
                    if (event instanceof VMDeathEvent) {
                        // The program has finished. Let go of it now: while a debugger is
                        // attached, its JVM waits about a third of a second before exiting.
                        vm.dispose();
                        return;
                    }
                    handle(event);
                }
                // Let the program run on. (If we just stopped it, this throws VMDisconnectedException.)
                events.resume();
            }
        }

        private void handle(Event event) {
            if (event instanceof ClassPrepareEvent) {
                classLoaded(((ClassPrepareEvent) event).referenceType());
            } else if (event instanceof BreakpointEvent) {
                lineReached(((BreakpointEvent) event).thread());
            } else if (event instanceof ExceptionEvent) {
                ExceptionEvent exception = (ExceptionEvent) event;
                if (exception.catchLocation() == null) {
                    crashed(exception);
                } else {
                    caught(exception);
                }
            } else if (event instanceof ModificationWatchpointEvent) {
                ModificationWatchpointEvent write = (ModificationWatchpointEvent) event;
                ObjectReference object = write.object();
                if (object != null) {
                    reader.fieldWritten(object);
                }
                fieldWritten(write);
            } else if (event instanceof com.sun.jdi.event.MethodExitEvent) {
                returned((com.sun.jdi.event.MethodExitEvent) event);
            }
        }

        // Switches the method-exit events on or off for the line about to run: off for a line of
        // main that only calls Java's own methods (Collections.sort(list)), on otherwise.
        private void exitsFor(Snapshot step) {
            if (returns == null || step.getFrames().isEmpty()) {
                return;
            }
            raphael.model.FrameSnapshot top = step.getFrames().get(0);
            boolean onlyJava = false;
            java.util.List<raphael.analysis.CodeFacts.CallFact> calls = facts.callsAt(top.getFile(), top.getLine());
            if (!calls.isEmpty()) {
                onlyJava = true;
                for (raphael.analysis.CodeFacts.CallFact call : calls) {
                    if (call.declaringClass() == null || isStudentClass(call.declaringClass())) {
                        onlyJava = false;
                    }
                }
            }
            // Only main's lines are switched off: inside any other method a return could happen on
            // the line, and its exit must be heard (a constructor's hidden super() counts as a Java call).
            boolean want = !onlyJava || !top.getMethodName().equals("main");
            if (want != returnsOn) {
                returnsOn = want;
                returns.setEnabled(want);
            }
        }

        private boolean isStudentClass(String simpleName) {
            for (raphael.analysis.CodeFacts.MethodFact method : facts.methods()) {
                if (method.className().equals(simpleName)) {
                    return true;
                }
            }
            return facts.superclasses().containsKey(simpleName) || facts.interfaces().containsKey(simpleName);
        }

        // One of the student's methods returned: kept for the next step, with its value.
        private void returned(com.sun.jdi.event.MethodExitEvent event) {
            Method method = event.method();
            if (method.isBridge() || method.isSynthetic() || method.isStaticInitializer()) {
                return; // javac's own helpers and lambdas' bodies, and a class's static set-up
            }
            String className = Names.simple(method.declaringType().name());
            boolean constructor = method.isConstructor();
            raphael.model.Value value = null;
            if (!constructor && !method.returnTypeName().equals("void")) {
                try {
                    value = HeapReader.plainValue(event.returnValue());
                } catch (UnsupportedOperationException | com.sun.jdi.ObjectCollectedException e) {
                    value = null; // the value is gone; the return is still known
                }
            }
            exits.add(new raphael.model.Exit(className, constructor ? className : method.name(), value));
        }

        // A field written: kept for the next step (the first WRITES_KEPT of them), with the method
        // and line that wrote it. The program isn't paused, so only the event's own facts are used.
        private void fieldWritten(ModificationWatchpointEvent event) {
            if (writes.size() >= raphael.model.Snapshot.WRITES_KEPT) {
                writesLeftOut++;
                return;
            }
            try {
                Location at = event.location();
                long id = event.object() == null ? -1 : event.object().uniqueID();
                String method = at.method().isConstructor() ? Names.simple(at.declaringType().name()) : at.method().name();
                writes.add(new raphael.model.FieldWrite(id, Names.simple(event.field().declaringType().name()),
                        event.field().name(), HeapReader.plainValue(event.valueToBe()), method, at.lineNumber()));
            } catch (com.sun.jdi.ObjectCollectedException e) {
                writesLeftOut++;
            }
        }

        // A class of the student's was loaded: remember it (for its static fields) and put a
        // breakpoint on the start of each of its lines. A bridge method is skipped: javac adds it
        // (for a generic method like compareTo) and gives it the line of the class header, so the
        // class header would seem to run.
        private void classLoaded(ReferenceType type) {
            studentClasses.add(type);
            // Tell us about every write to this class's fields, so HeapReader only reads again the
            // objects that changed. SUSPEND_NONE: the program doesn't stop for these, so they cost
            // almost nothing; they arrive in order, before the next line's breakpoint.
            if (vm.canWatchFieldModification()) {
                for (Field field : type.fields()) {
                    if (!field.isStatic() || recordExits) { // static fields too, for who changed them
                        ModificationWatchpointRequest watch = requests.createModificationWatchpointRequest(field);
                        watch.setSuspendPolicy(EventRequest.SUSPEND_NONE);
                        watch.enable();
                    }
                }
            } else {
                reader.stopRemembering();
            }
            for (Method method : type.methods()) {
                if (method.isBridge()) {
                    continue;
                }
                try {
                    for (Location line : method.allLineLocations()) {
                        requests.createBreakpointRequest(line).enable();
                    }
                } catch (AbsentInformationException e) {
                    // No line numbers in this method, so there's nothing to pause on.
                }
            }
        }

        private void lineReached(ThreadReference thread) {
            if (firstThread == null) {
                firstThread = thread;
            } else if (!thread.equals(firstThread)) {
                reader.stopRemembering(); // a second thread runs the student's code
            }
            snapshots.add(reader.capture(thread, output::length, null, justCaught, folder.files(), exits, writes, writesLeftOut));
            justCaught = null;
            exits.clear();
            writes.clear();
            writesLeftOut = 0;
            exitsFor(snapshots.get(snapshots.size() - 1));
            steps = snapshots.size();
            if (snapshots.size() >= limits.maxSteps()) {
                end(EndReason.STEP_LIMIT, stepLimitMessage(snapshots.get(snapshots.size() - 1), limits.maxSteps()));
                stopProgram();
            }
        }


        // An exception that will be caught. Java's own code throws and catches many exceptions
        // inside itself (while loading classes, for instance), so only the ones the student's code
        // throws or catches are kept: Integer.parseInt throwing into the student's catch counts.
        private ThrownException cleanedUp; // an exception a try (…) is passing on after closing its resources

        private void caught(ExceptionEvent event) {
            Location thrownAt = event.location();
            Location caughtAt = event.catchLocation();
            if (!isStudentCode(thrownAt) && !isStudentCode(caughtAt)) {
                return;
            }
            ObjectReference exception = event.exception();
            String type = Names.simple(exception.referenceType().name());
            String message = messageOf(exception, event.thread());
            int throwLine = -1;
            String throwFile = null;
            List<String> left = new ArrayList<>();
            try {
                // From the frame that threw, outwards, to the frame whose method has the catch:
                // the student's methods in between are the ones the exception left.
                for (StackFrame frame : event.thread().frames()) {
                    Location at = frame.location();
                    if (at.method().equals(caughtAt.method())) {
                        if (throwLine < 0) {
                            throwLine = at.lineNumber(); // thrown in the same method that catches it
                            throwFile = fileOf(at, sourceFiles);
                        }
                        break;
                    }
                    if (isStudentCode(at)) {
                        if (throwLine < 0) {
                            throwLine = at.lineNumber(); // the student's line the exception came from
                            throwFile = fileOf(at, sourceFiles);
                        }
                        String method = at.method().name();
                        left.add(method.equals("<init>") ? Names.simple(at.declaringType().name()) : method); // a constructor by its class
                    }
                }
            } catch (IncompatibleThreadStateException e) {
                // The thread isn't paused after all: keep what's known.
            }
            String catchFile = fileOf(caughtAt, sourceFiles);
            if (facts != null && facts.isResourceCleanup(catchFile, caughtAt.lineNumber())) {
                // try (…) closing its resources: javac's hidden catch on the try line, which
                // closes them and throws the same exception on. The student's catch comes next.
                if (cleanedUp == null) {
                    cleanedUp = new ThrownException(type, message, throwLine, -1, left, throwFile, null);
                }
                return;
            }
            if (cleanedUp != null) { // thrown on by a try (…): say where it was really thrown
                List<String> all = new ArrayList<>(cleanedUp.leftMethods());
                all.addAll(left);
                throwLine = cleanedUp.throwLine();
                throwFile = cleanedUp.throwFile();
                left = all;
                cleanedUp = null;
            }
            justCaught = new ThrownException(type, message, throwLine, caughtAt.lineNumber(), left,
                    throwFile, catchFile);
        }

        private static boolean isStudentCode(Location at) {
            String name = at.declaringType().name();
            return !isJdkClass(name) && !name.startsWith(AGENT_CLASS);
        }

        // An exception nothing catches: record it as the last step, then let the program finish
        // crashing (Java prints the stack trace and the JVM exits).
        private void crashed(ExceptionEvent event) {
            event.request().disable();
            requests.deleteAllBreakpoints();
            ObjectReference exception = event.exception();
            String text = Names.simple(exception.referenceType().name());
            String message = messageOf(exception, event.thread());
            if (message != null) {
                text += ": " + message;
            }
            Snapshot last = reader.capture(event.thread(), output::length, text, null, folder.files());
            snapshots.add(last);
            end(EndReason.EXCEPTION, text + " (line " + last.getLine() + ")");
        }

        // Calls getMessage() on the exception inside the paused program.
        // That also gives the helpful NullPointerException messages ("because curr is null").
        private String messageOf(ObjectReference exception, ThreadReference thread) {
            try {
                Method getMessage = ((ClassType) exception.referenceType())
                        .concreteMethodByName("getMessage", "()Ljava/lang/String;");
                com.sun.jdi.Value message = exception.invokeMethod(thread, getMessage, List.of(),
                        ObjectReference.INVOKE_SINGLE_THREADED);
                return message instanceof StringReference ? ((StringReference) message).value() : null;
            } catch (Exception e) {
                return null; // no message available
            }
        }

        private void end(EndReason reason, String message) {
            endReason = reason;
            endMessage = message;
        }

        private void stopProgram() {
            try {
                vm.exit(1);
            } catch (VMDisconnectedException e) {
                // It had already ended.
            }
        }

        // Types the input into the program's System.in, then closes it. Closing means "no more
        // input", so Scanner sees the end instead of waiting forever.
        private static void sendInput(OutputStream stdin, String input) {
            try (stdin) {
                stdin.write(input.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                // The program ended before reading its input.
            }
        }

        private static void waitFor(Thread thread) {
            try {
                thread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
