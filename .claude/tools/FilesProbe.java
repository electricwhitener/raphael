import raphael.io.ExampleCatalog;
public class FilesProbe {
    public static void main(String[] a) throws Exception {
        var e = ExampleCatalog.find(a[0]);
        var tr = new raphael.trace.JdiTracer().trace(new raphael.compile.SourceCompiler().compile(ExampleCatalog.source(e)), "");
        System.out.println(tr.getEndReason() + " " + tr.getOutput().trim().replace("\n", " / "));
        Object last = null;
        for (int i = 0; i < tr.size(); i++) {
            var f = tr.get(i).getFiles();
            if (f != last) System.out.println("step " + i + " line " + tr.get(i).getLine() + " " + f.toString().replace("\n", "⏎"));
            last = f;
        }
        System.exit(0);
    }
}
