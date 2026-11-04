# 3. Background: the JDK tools Raphael is built on

Raphael needs a full JDK (Java Development Kit), not just a JRE (Java Runtime Environment), because it compiles and debugs the code it is given. This chapter explains the four parts of the JDK it relies on.

## 3.1 From source to running program
A Java program runs in two steps:

```
Program.java  →  javac  →  Program.class (bytecode)  →  JVM  →  machine code
```

`javac` turns the source text into bytecode, one `.class` file per class. Bytecode is not machine code: the JVM (Java Virtual Machine) translates it while the program runs. This is why the same `.class` file runs on Windows, macOS and Linux.

## 3.2 Compiling from inside a program: `javax.tools`
The compiler is also a library inside the JDK, so Raphael calls it as an object instead of running a terminal command:
- `ToolProvider.getSystemJavaCompiler()` returns a `JavaCompiler`. This is an interface, and a hidden JDK class implements it. On a JRE the method returns `null`, which is why Raphael needs a JDK.
- `getTask(...)` sets up a compilation. A `DiagnosticCollector` gathers each problem as a `Diagnostic` object, with its kind (error or warning), line number and message. These line numbers are what mark errors in Raphael's editor.
- `call()` returns `true` only when there are no errors. Warnings don't make it fail.

Raphael compiles with `-g`, which keeps the names of local variables in the bytecode. Without it, the debugger could only call them `arg0` and `local1`.

## 3.3 Pausing and reading a running program: JDI
JDI (Java Debug Interface) is the JDK's library for controlling another JVM. It is what IDE debuggers are built on.
- **Launching.** A `LaunchingConnector` starts the student's program in a second JVM, in debug mode. The two JVMs talk over a local connection.
- **Requests and events.** Raphael asks the second JVM to report events: a class was loaded (`ClassPrepareRequest`), a line was reached (`BreakpointRequest`), an exception was thrown (`ExceptionRequest`). The program stays paused while Raphael handles each event, and Raphael then resumes it.
- **Breakpoints on every line.** When one of the student's classes loads, Raphael puts a breakpoint on the first instruction of every line in it. Java's own classes (`java.*`, `javax.*`, `sun.*`, `jdk.*`) get none, so the run never pauses inside `println`. Raphael doesn't use JDI's "step to the next line", because in a one-line loop such as `while (true) { x++; }` the line never changes and that step never finishes. A breakpoint fires on every pass through the loop.
- **Mirrors.** At each pause, JDI gives *mirrors* of the paused program: a `StackFrame` for each running method, a `LocalVariable` for each variable, an `ObjectReference` for each object, each with a unique id. Raphael copies what it needs from them into its own model classes (chapter 4), so the rest of Raphael never depends on JDI.
- **Calling into the paused program.** `ObjectReference.invokeMethod` runs a method inside the paused JVM. Raphael uses it to call `getMessage()` on an exception, and `toArray()` on a Java collection to list its items, whichever way the collection stores them.

## 3.4 Drawing: Swing and Java 2D
Swing is the JDK's library for windows, buttons, menus and text areas. Java 2D (`Graphics2D`) draws lines, curves, shapes and text onto a component. Two of Swing's rules shape Raphael's design:
- **One UI thread.** Swing has one thread, the event dispatch thread, that draws the window and handles clicks. Long work must happen on another thread, or the window freezes. Raphael records programs on a background thread with `SwingWorker`.
- **Timers.** `javax.swing.Timer` calls code on the UI thread at a fixed rate. Raphael's animation and its play button both run on timers.

Java 2D only draws: "a line from here to there", "this text at this point". Where each shape goes, when it moves and how it eases is all Raphael's own code (chapter 4).

FlatLaf, the one outside library, replaces Swing's default look with a modern flat one, in light and dark themes.

## 3.5 Building and testing: Maven and JUnit 5
Maven builds the project from a recipe, `pom.xml`. It:
- downloads the libraries (FlatLaf, JUnit) from Maven Central
- compiles the code in `src/main/java`
- runs the tests in `src/test/java`

JUnit 5 runs the tests. Each test is a method that calls Raphael's code and checks the result automatically, for example "compile code missing a `;` on line 3 and check the error says line 3". `mvn test` runs them all.
