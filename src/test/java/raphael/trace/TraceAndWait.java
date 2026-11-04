package raphael.trace;

import java.util.Optional;
import raphael.compile.CompileResult;
import raphael.compile.SourceCompiler;

// A stand-in for Raphael, used by TracerTest. It traces a program that only sleeps, prints the
// program's process id, and then either keeps going (so the test can kill it) or quits ("exit").
public class TraceAndWait {

    public static void main(String[] args) throws Exception {
        CompileResult program = new SourceCompiler().compile(
                "public class Main { public static void main(String[] a) throws Exception { Thread.sleep(600000); } }");
        Thread report = new Thread(() -> {
            while (true) {
                Optional<ProcessHandle> child = ProcessHandle.current().children()
                        .filter(p -> p.info().command().map(c -> c.contains("java")).orElse(true))
                        .findFirst();
                if (child.isPresent()) {
                    System.out.println("program " + child.get().pid());
                    System.out.flush();
                    if (args.length > 0 && args[0].equals("exit")) {
                        System.exit(0);
                    }
                    return;
                }
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    return;
                }
            }
        });
        report.start();
        new JdiTracer().trace(program, "");
    }
}
