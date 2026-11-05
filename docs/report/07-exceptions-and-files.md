# 7. Exception handling and file handling

*Sections 7.2 and 7.3 are written with ROADMAP stages 7 and 8.*

## 7.1 How Raphael handles its own errors
Raphael separates two kinds of failure:
- **Problems** are expected, and Raphael can explain them to the user: there is no Java compiler, the temporary folder can't be written, or the program's JVM won't start.
- **Bugs** are unexpected, for example a `NullPointerException` inside a panel.

### An exception hierarchy for problems
Every problem is a subclass of one abstract, checked exception:

```
Exception
 └ RaphaelException (abstract)
    ├ CompileSetupException   can't get ready to compile
    └ TraceException          can't run or record the program
```

- **Abstract:** only the specific kinds can be thrown.
- **Checked:** the compiler forces every caller to catch or declare it.
- **Chained:** each keeps the low-level exception that caused it (`super(message, cause)`). The user sees a plain sentence, while the log keeps the original `IOException` or JDI error.

```java
try {
    dir = Files.createTempDirectory("raphael");
    file = dir.resolve(className + ".java");
    Files.writeString(file, withoutFileRules(source, topLevel));
} catch (IOException e) {
    throw new CompileSetupException("Couldn't write the code to the temporary folder ("
            + System.getProperty("java.io.tmpdir") + "). Is the disk full?", e);
}
```

Compiling and tracing run on a background thread (`SwingWorker`). An exception there reaches the window wrapped in an `ExecutionException`, and `MainWindow.runFailed()` looks at its cause:
- a `RaphaelException` is shown as a plain message in the picture;
- anything else is a bug, and goes to the safety net.

### The safety net for bugs
`ErrorReporter.install()` sets Java's *default uncaught exception handler*. Java calls it when an exception reaches the top of any thread without being caught, including Swing's event thread. The handler:
1. writes the exception and its stack trace to the log;
2. shows a dialog that says what happened, says the user's code is safe, and gives the log folder, with buttons to copy the details or open the folder;
3. shows at most one dialog every 10 seconds, so a bug that repeats on every repaint can't flood the screen.

The handler wraps its own work in `catch (Throwable)`, so the safety net itself can never fail.

### Logging
Raphael uses the JDK's `java.util.logging`. Each class has a `Logger` named after itself. `AppLog.setup()` sends everything to a file in each system's usual place for logs (`AppFolders`):

| System | Log folder |
|---|---|
| Windows | `%LOCALAPPDATA%\Raphael\logs` |
| macOS | `~/Library/Logs/Raphael` |
| Linux | `~/.local/state/raphael/logs` |

- **Rotation:** a `FileHandler` keeps three files of up to 1 MB each.
- **Format:** `LineFormatter`, a subclass of `Formatter`, writes one line per message, followed by the stack trace if there is one:

```
2026-11-05 05:46:32 SEVERE ErrorReporter: Unexpected problem
java.lang.IllegalStateException: pretend bug in a panel
```

Every `catch` block in the code was reviewed. Most handle normal situations, such as a line that no longer exists after an edit. Those that hid a real failure without a trace now log it: the font not loading, the agent jar not being written, an example missing, a collection that couldn't be listed.

### Not losing the user's work
- **Autosave.** Every 5 seconds, unsaved code is copied to a draft file on a background thread (`DraftStore`). A normal quit deletes the draft. So a draft found at start-up means the last session ended abnormally, and Raphael offers to restore it.
- **Safe saving.** `SafeFiles.write()` writes to a temporary file in the same folder and then moves it over the original in one step. A full disk or a power cut can't leave a half-written file.
- **Safe opening.** Files over 1 MB, or containing zero bytes (not text), are refused before they can freeze the editor.
- **Plain reasons.** `SafeFiles.describe()` catches the specific `IOException` subclasses first: `NoSuchFileException` becomes "it doesn't exist (was it moved or deleted?)", and `AccessDeniedException` becomes "you don't have permission to use it".

### Settings with validated limits
The step limit, the idle timeout and the program's memory can be changed in a settings dialog. `RunLimits` is a record whose compact constructor throws `IllegalArgumentException` for any value outside its range, so an invalid configuration cannot exist. If stored settings are out of range, for example after being edited by hand, Raphael logs this and uses the defaults.

## 7.2 Exceptions in the student's program
*ROADMAP stage 7.*

## 7.3 File handling
*ROADMAP stage 8.*
