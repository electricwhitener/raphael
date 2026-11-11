package raphael.lang;

import java.util.List;
import raphael.compile.CompileResult;
import raphael.compile.CompileSetupException;
import raphael.model.SourceFile;
import raphael.trace.Tracer;

// A language Raphael can already edit, colour and file, but can't run yet (C, C++ and Python run
// in Stages 14 to 16). compile() says so, as a compile error the window shows like any other.
// OOP: an abstract class between Language and the languages that use it (multilevel inheritance).
abstract class NotRunnableYet extends Language {

    @Override
    public String cantRun() {
        return "Raphael can't run " + name() + " programs yet: that's coming soon. You can write and save them now.";
    }

    @Override
    public CompileResult compile(String code) throws CompileSetupException {
        return new CompileResult(false, List.of(new CompileResult.CompileError(0, cantRun())), null, null);
    }

    @Override
    public CompileResult compileFolder(List<SourceFile> files, String preferredMain) throws CompileSetupException {
        return compile("");
    }

    @Override
    public Tracer newTracer() {
        throw new UnsupportedOperationException(cantRun());
    }
}
