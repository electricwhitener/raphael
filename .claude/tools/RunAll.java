import raphael.io.ExampleCatalog;
import raphael.model.Trace;
public class RunAll {
    public static void main(String[] a) throws Exception {
        for (ExampleCatalog.Example e : ExampleCatalog.all()) {
            if (a.length > 0 && !e.path().startsWith(a[0])) continue;
            long t = System.nanoTime();
            String src = ExampleCatalog.source(e);
            var prog = new raphael.compile.SourceCompiler().compile(src);
            if (!prog.success()) { System.out.println("COMPILE " + e.path() + " " + prog.errors()); continue; }
            Trace tr = new raphael.trace.JdiTracer().trace(prog, "");
            var scenes = new raphael.render.SceneBuilder(src).build(tr);
            System.out.printf("%-34s %5d %-10s %5dms | %s%n", e.path(), tr.size(), tr.getEndReason(), (System.nanoTime() - t) / 1000000,
                    tr.getOutput().trim().replace("\n", " / ") + (tr.getEndMessage() == null ? "" : " !! " + tr.getEndMessage()));
        }
        System.exit(0);
    }
}
