package raphael.trace;

import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.Bootstrap;
import com.sun.jdi.ClassType;
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
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.connect.VMStartException;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventQueue;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.ExceptionEvent;
import com.sun.jdi.event.VMDisconnectEvent;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.EventRequestManager;
import com.sun.jdi.request.ExceptionRequest;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import raphael.compile.CompileResult;
import raphael.model.EndReason;
import raphael.model.Names;
import raphael.model.Snapshot;
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
    private static final long POLL_MS = 100; // how often to look up from waiting, to see if stop was pressed

    private volatile Run current; // the run in progress, or null; volatile because the window's thread reads it

    @Override
    public Trace trace(CompileResult program, String input) throws TraceException {
        VirtualMachine vm = launch(program);
        current = new Run(vm);
        try {
            return current.record(input);
        } finally {
            current = null;
        }
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

    // Starts the program in a new JVM, paused before it does anything.
    private VirtualMachine launch(CompileResult program) throws TraceException {
        LaunchingConnector connector = Bootstrap.virtualMachineManager().defaultConnector();
        Map<String, Connector.Argument> arguments = connector.defaultArguments();
        arguments.get("main").setValue(program.mainClass());
        // The quotes keep a folder name with spaces in one piece; UTF-8 keeps printed text intact.
        arguments.get("options").setValue("-cp \"" + program.classDir() + "\""
                + " -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8");
        try {
            return connector.launch(arguments);
        } catch (IOException | IllegalConnectorArgumentsException | VMStartException e) {
            throw new TraceException("Could not start the program: " + e.getMessage(), e);
        }
    }

    // One run of one program: everything that changes while it runs.
    // OOP: a private nested class, only used inside JdiTracer.
    private static class Run {

        private final VirtualMachine vm;
        private final EventRequestManager requests;
        private final StringBuffer output = new StringBuffer();
        private final List<ReferenceType> studentClasses = new ArrayList<>();
        private final HeapReader reader = new HeapReader(studentClasses);
        private final List<Snapshot> snapshots = new ArrayList<>();
        private EndReason endReason = EndReason.FINISHED;
        private String endMessage = "Program finished.";
        private volatile boolean stopRequested;
        private volatile int steps;

        Run(VirtualMachine vm) {
            this.vm = vm;
            this.requests = vm.eventRequestManager();
        }

        Trace record(String input) throws TraceException {
            Process process = vm.process();
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
            classLoaded.enable();
            // ...and when an exception is thrown that nothing catches.
            ExceptionRequest crash = requests.createExceptionRequest(null, false, true);
            crash.enable();

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
                    if (System.currentTimeMillis() - lastEvent >= TraceLimits.EVENT_TIMEOUT_MS) {
                        end(EndReason.TIMEOUT, "Stopped: the program did nothing for "
                                + TraceLimits.EVENT_TIMEOUT_MS / 1000 + " seconds.");
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
                crashed((ExceptionEvent) event);
            }
        }

        // A class of the student's was loaded: remember it (for its static fields) and put a
        // breakpoint on the start of each of its lines. A bridge method is skipped: javac adds it
        // (for a generic method like compareTo) and gives it the line of the class header, so the
        // class header would seem to run.
        private void classLoaded(ReferenceType type) {
            studentClasses.add(type);
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
            snapshots.add(reader.capture(thread, output::length, null));
            steps = snapshots.size();
            if (snapshots.size() >= TraceLimits.MAX_STEPS) {
                end(EndReason.STEP_LIMIT, "Stopped after " + TraceLimits.MAX_STEPS
                        + " steps. If it isn't an infinite loop, try a smaller input.");
                stopProgram();
            }
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
            Snapshot last = reader.capture(event.thread(), output::length, text);
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
