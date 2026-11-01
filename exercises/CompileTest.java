import javax.tools.*;
import java.util.List;

public class CompileTest {
    public static void main(String[] args) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if(compiler == null) {
            System.out.println("No compiler");
        } else {
            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
            Iterable<? extends JavaFileObject> files = fileManager.getJavaFileObjects("Broken.java");

            boolean ok = compiler.getTask(null, fileManager, diagnostics, List.of("-g"), null, files).call();

            if(ok == true) {
                System.out.println("Compilation successful");
                System.out.println("Diagnostics: " + diagnostics.getDiagnostics().size());

            } else {
                for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                    System.out.println("Line " + diagnostic.getLineNumber() + ": " + diagnostic.getMessage(null));
                }
            }
        }
    }
}
