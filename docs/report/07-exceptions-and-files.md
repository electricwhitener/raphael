# 7. Exception handling and file handling

*Section 7.3 is written with ROADMAP stage 8.*

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
A student learning exceptions needs to see the jump: the `throw`, the lines that are skipped, and the `catch` where the program continues. Raphael shows this for every exception the student's code throws or catches.

**Finding them.** JDI reports every exception thrown in the traced program, with the location where it was thrown and the location of the handler that will catch it (`ExceptionEvent.catchLocation()`). The JDK throws and catches many exceptions internally, so `JdiTracer.caught()` keeps an exception only if the student's code throws it or catches it. A `NumberFormatException` thrown inside `Integer.parseInt` and caught by the student is kept. Walking the paused thread's stack from the throwing frame to the frame with the handler gives:
- the student's line the exception came from;
- the methods it left on the way, each interrupted mid-execution.

These are stored in a `ThrownException` record on the next step, the first line of the `catch` block. A timing comparison showed no measurable cost from listening for caught exceptions.

**Showing them.** On that step Raphael draws:
- an outlined card in the picture (outlined, not filled, because the exception was handled): the exception, "thrown at line 27 · caught at line 52 · left pop()", and its meaning;
- a red bracket in the editor's line numbers from the `throw` line to the `catch` line;
- the methods it left, greyed, above the catching method in the call stack.

**Explaining them.** `ErrorExplainer` is an abstract class with one subclass per common exception: null references, indexes out of range, division by zero, invalid numbers, wrong or missing input, invalid casts, runaway recursion, modifying a collection during iteration, and running out of memory. A static `explain()` finds the subclass that handles the type and calls its `describe()` polymorphically, with a fallback for anything else. The explanations draw on the detail in Java's messages:

| Java's message | Raphael's explanation |
|---|---|
| `Cannot read field "next" because "curr" is null` | curr was null (it pointed to no object), so the line couldn't read its field next. Check where curr gets its value, or test for null before using it. |
| `Index 5 out of bounds for length 5` | The array has 5 cells, numbered 0 to 4, so there's no position 5. A loop that uses <= instead of < often does this. |
| `For input string: "twenty"` | "twenty" was turned into a number, but it isn't one. |
| `/ by zero` | The line divided by zero (with / or %). Whole numbers can't be divided by zero; check the divisor first. |

For exceptions a program throws on purpose (its own classes, `IllegalArgumentException` and the like), there is no advice to give, so a caught one is explained by what the `catch` did: the rest of the `try` was skipped, the methods it left stopped where they were, and the program continues in the `catch`. An uncaught one gets the same kind of card, filled red, titled "crashed at line N".

**Compile errors.** `CompileHints` adds a plain hint to javac's common messages. For example, "cannot find symbol … variable totl" becomes "Java doesn't know the variable totl. Check that it's declared before this line, in this block or an outer one, and spelled the same way (capitals count)." The hint appears in the error card and in the console.

**The Exceptions example** shows all of this in one program:
- a stack class with its own checked exception;
- an `IllegalStateException` when the stack is full;
- a method that wraps a `NumberFormatException` in an `IllegalArgumentException`, keeping it as the cause;
- `try`/`catch`/`finally` around these.

**One card for every error.** After the stage, the design review found the three error messages (a caught exception, a crash, a compile error) looked like three different things, and that a card could cover the drawing. They now share one card: a red title, the exception or error in the code font, a grey line of facts (where it was thrown and caught), and the explanation. A crash or compile error fills the card with light red; a caught exception, which the program handled, is only outlined. The picture keeps a strip at its bottom for the tallest card of the whole run, so nothing moves when a card appears.

## 7.3 File handling
*ROADMAP stage 8.*

Version 1 had no files at all: a program lived only in the editor, and a run disappeared when the window closed. Stage 8 adds three kinds of file, and lets the student's own program use files safely.

### Saved recordings (`.raphael`)
A recording holds the program, its input and every recorded step, so it can be opened and played without running anything. A teacher can record a program once and replay it in class. `RunFile` writes it with **Java serialization**: the model classes (`Trace`, `Snapshot`, `FrameSnapshot`, `HeapObject` and its subclasses, the `Value` kinds) implement `Serializable`, each with a `serialVersionUID`. Java then turns the whole object graph into bytes and back. Objects that several steps share, which Raphael's memory saving (stage 4) produces, are written once and come back shared, so a 163-step run of BSTDelete takes 3 KB.

The bytes pass through **streams stacked on each other**, each adding one job. This is the **Decorator pattern** built into `java.io`:

| Stream | Its job |
|---|---|
| `ObjectOutputStream` | turns objects into bytes |
| `GZIPOutputStream` | compresses them |
| `DataOutputStream` | writes the header: `RAPH` and a format version |
| `BufferedOutputStream` | collects bytes and writes them in large pieces |
| `Files.newOutputStream` | puts them in the file |

The header lets Raphael say clearly what's wrong with a bad file. Every problem becomes a `RunFileException`, the newest member of the `RaphaelException` family, with a plain message: "isn't a run saved by Raphael", "was saved by a newer Raphael", or "is damaged: it ends too soon". A file is first written under a temporary name and then moved into place, so a crash halfway never leaves half a file.

**Reading objects from a file is a known security risk.** Java would build whatever classes a crafted file names, and some JDK classes do harmful things while being rebuilt. So reading uses an `ObjectInputFilter` that allows only Raphael's model classes and the basic `java.lang` and `java.util` types they're made of, with limits on size and depth. A test proves that a file holding any other object is refused.

`NullValue` is a singleton. Its `readResolve()` makes reading return the one shared instance, not a second "null".

### The CSV step log
File → Export steps as CSV writes the run as a table, one row per step: the step, the line, the method, a column per variable, and what that step printed. It's the hand trace students do on paper. `StepLog` writes it with a `PrintWriter` on a `BufferedWriter` and quotes cells by the CSV rules. Because a `PrintWriter` never throws, `checkError()` is asked at the end.

### The student's program and its files
Before, the program ran in whatever folder Raphael was started from, so a program writing `out.txt` wrote into the install folder or the home folder. Now each run gets its own new folder (`RunFolder`), which is deleted afterwards. `RunFolder` is `AutoCloseable`, so a try-with-resources block deletes it however the run ends.

JDI's ready-made launcher can't choose the folder. So `JdiTracer` now starts the JVM itself with a `ProcessBuilder` set to that folder, and the program connects back to a port Raphael listens on (JDI's *listening connector*, on 127.0.0.1 only).

At every step Raphael reads the small text files in the folder, so the new Files panel shows what the program has written so far. A file that changed has an amber dot, and its new text is tinted amber. Steps where nothing changed share one map. The FileIO examples show:
- try-with-resources;
- `FileNotFoundException` as a checked exception;
- appending;
- the buffering lesson every student meets: a `PrintWriter`'s lines reach the file only when it's flushed or closed, and the panel shows the file staying empty until then.

The user can also add input files, which are copied into the folder before each run.

### Opening and finding files
- **The welcome page and File → Open recent** list the last eight files opened or saved, kept in `Preferences`. A file that was moved or deleted drops out.
- **Double-clicking a `.raphael` file opens Raphael.** The installers register the type (`jpackage --file-associations`). The file arrives as a command-line argument, or on a Mac through `Desktop.setOpenFileHandler`.
- **`.java` isn't claimed,** because that would take it away from the user's IDE.
