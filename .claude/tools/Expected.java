import raphael.io.ExampleCatalog;
public class Expected {
    public static void main(String[] a) throws Exception {
        StringBuilder out = new StringBuilder();
        for (ExampleCatalog.Example e : ExampleCatalog.all()) {
            String src = ExampleCatalog.source(e);
            var tr = new raphael.trace.JdiTracer().trace(new raphael.compile.SourceCompiler().compile(src), "");
            if (tr.getEndReason() != raphael.model.EndReason.FINISHED) continue;
            out.append("## ").append(e.path()).append("\n").append(tr.getOutput().trim().replace("\r\n", "\n")).append("\n\n");
        }
        java.nio.file.Files.writeString(java.nio.file.Path.of(a[0]), out.toString());
        System.exit(0);
    }
}
