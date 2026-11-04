# CLAUDE.md

## Project
**Raphael** is a desktop app for an OOP-in-Java course. A student types a single-file Java program into the app's built-in editor (or opens a `.java` file) and clicks Run. The app:
1. Compiles it with `javax.tools.JavaCompiler`.
2. Runs it in a second JVM and pauses after every line, using JDI.
3. Records a snapshot of variables and objects at each step.
4. Draws arrays, linked lists and binary trees in a Swing window, with step forward/back and animation.

Version 1, the course project, followed [PLAN.md](PLAN.md). Version 2 turns Raphael into an open-source product. Its stages are in [ROADMAP.md](ROADMAP.md): follow them in order and don't jump ahead.

## Progress
- **Stage 0 (Setup): done, 2026-10-17.** JDK 21 and Maven 3.8.7 are installed in WSL. `pom.xml` exists (JUnit 5, compiler, surefire and exec plugins). `App.java` prints Hello World. VS Code is connected to WSL with the Java extensions. The Stage 0 entry is in `DECISIONS.md`.
- **Stage 1 (Compiling): done, 2026-10-19.** `SourceCompiler` and `CompileResult`. `SourceCompiler` also finds the class that has `main`.
- **Stages 2–4 (Engine): done, 2026-10-26.** `trace/` (`Tracer`, `JdiTracer`, `HeapReader`, `OutputDrainer`, `TraceLimits`, `TraceException`) and `model/`. 9 tests pass.
- **Stage 5 (Detect): done, 2026-10-26.** `detect/` (`StructureDetector`, plus `Structure` → `ArrayStructure`, `GridStructure`, `GenericStructure` and `LinkedStructure` → `LinkedListStructure`/`BinaryTreeStructure`). 17 tests pass.
- **Stage 6 (Render): done, 2026-10-26.** `render/` (`SceneBuilder`, `StructureRenderer` + 5 subclasses, `ListOrder`, `Scene`/`SceneNode`/`SceneEdge`, `ScenePainter`, `Style`). The 6 examples are in `src/main/resources/examples/`. 20 tests pass.
- **Stages 7–8 (Window, Animation): done, 2026-10-26.** `ui/` (`MainWindow`, `TraceModel`/`TraceListener`, 6 panels), FlatLaf 3.5.4, and `render/Animator` + `Diff`. 27 tests pass.
- **Stages 9–10 (Style, Hand-in): done, 2026-10-26.** `Style` keeps its defaults. `README.md` (pipeline, file map, OOP map, screenshots in `docs/`) and `DEMO.md` (demo script) are written.
- **Polish (motion, markers, look): done, 2026-10-26.**
  - Spring-based `Animator` (`Spring`), with swinging arrows, value fly-overs and rolling values.
  - `IndexUsage` for `i`/`j` markers and read/write outlines.
  - Fixed widths for the whole run (`RenderContext`).
  - Two-line editor highlight, clickable line numbers, step over/out, shortcuts, zoom.
  - The terminal look (one monospace font, white, a blue accent for "next", amber for "changed"), plus the OffByOne example.
  - 42 tests pass.
- **B-tree view: done, 2026-10-30.** `MultiwayTreeStructure` and `MultiwayTreeRenderer` (a class with `Node[] children` is a multi-way tree). `IndexUsage` reads `x.keys[i]` and `keys[j + T]`. BTreeInsert example added. 45 tests pass.
- **Refine pass: done, 2026-10-31.**
  - Fixes: arrow keys and Home/End now step the program (scroll and split panes were taking them), focus goes to the picture after a run, undo/redo, Ctrl+S/Ctrl+O, a prompt before replacing unsaved code, the divider fits the longest line, the window fits the screen, pointer slots show their dot.
  - Look: JetBrains Mono NL, picture centred and scaled to fit with one scale per run (`VisualPanel.fitToRun`), softer outlines, tinted read/write cells, filled run button, stretching slider, flush bottom panels, stdin box only when the code uses `System.in`, variable text without ids (`describe(Snapshot)`).
  - `ArrayList` contents are drawn (`HeapReader.readArrayList`, stored as an `ArrayNode`). Other `java.util` classes stay name-only.
  - 61 tests pass.
- **Teacher-demo pass: done, 2026-10-31.** The goal: a teacher pastes any DSA program and it just runs.
  - `SourceCompiler` blanks the top-level `public` and `package` before compiling (spaces, so line numbers stay right), and cleans invisible web characters.
  - **new** button, Ctrl+V from anywhere, auto-play on run (any manual step pauses), IDE colours (`JavaTokens`, `CodePanel.ColoredView`).
  - All Java collections are drawn: `HeapReader.readCollection` calls `toArray()` inside the paused program. Enums show their name.
  - The design critic's fixes: red ghost only for a real crash, `new` cell, ghost cells for -1 and the length, arrays on top and sharing rows with room kept for the whole run, unlinked tree nodes beside their tree, markers on `x.keys` only while it's in use, tries, lists of lists as tables, named node values, a legend.
  - 89 tests pass.
  - `scripts/Run Raphael.bat` (a copy is on the Windows Desktop) starts the app through WSL. Open and save start at the Windows Desktop.
  - `scripts/build-windows.sh` makes a standalone `Raphael.exe` with its own Java. **It has never been run**: it downloads a JDK, so the user has to run it or allow it.
- **Final polish: done, 2026-11-01.**
  - Dark mode (`Style.useTheme`, `MainWindow.applyTheme`), Ctrl +/-/0 text size, both remembered with the JDK's `Preferences`.
  - `EditorKeys`: indentation, Tab/Shift+Tab, Ctrl+/, find. A **keys** help screen, and a prompt when quitting with unsaved code.
  - Stop button with a live step count. Only the newest 32 frames are read per step, so recursion with no base case ends quickly.
  - The launcher runs `dist/` (built by `scripts/build-app.sh`), not `target/classes`.
  - The critic's last findings: one red banner for crashes, missing input and compile errors; arrows leave a slot through its bottom; boxes show `X extends Y` and one shape per field; arrays named after the field that holds them; bridge methods skipped; a jump doesn't flash.
  - Fixed a timing bug from Stage 2: `JdiTracer` released the paused JVM twice, so about 1 run in 60 recorded 0 steps. 150 of 150 runs were right after the fix.
  - 107 tests pass.
- **Shipping: 2026-11-01.** BankAccounts example added (9 examples). `README.md` rewritten for anyone who finds the repo. `docs/OOP-HIGHLIGHTS.md` is the guide for showing the OOP to the teacher. Pushed to GitHub: `electricwhitener/raphael` (private).
- **Version 2, the product: started 2026-11-04.** The teacher liked the submission and asked for more Java (exceptions, files), edge-case testing and a detailed report.
  - The user's decisions:
    - production-grade on Windows, macOS and Linux
    - a desktop app first and IDE plugins later
    - open source
    - Java only for now
    - beat the competitors, mainly JavaWiz
  - All of it is in ROADMAP.md.
  - **Stage 0 (set up): done, 2026-11-04.** ROADMAP.md, this file, a DECISIONS.md entry, and the report skeleton in `docs/report/` with chapters 1–5 drafted.
- **Stage 1 (works on any machine): done, 2026-11-04.** The repo is public.
  - `.github/workflows/ci.yml` runs `mvn test` on Windows, macOS and Linux (xvfb) on every push. All 116 tests pass on all three, with none skipped.
  - `ui/Shortcuts`: Cmd and Option on a Mac, for both the keys and the labels.
  - `ui/SystemTheme`: the first launch matches the OS dark mode.
  - The start folder is the Desktop on any OS (the Windows one only inside WSL).
  - `Locale.ROOT` for case changes.
  - javac reads UTF-8 and reports in English.
  - Not done here: the HiDPI check needs real screens, and the native launchers moved to Stage 5 (`jpackage`).
- **Stage 2 (bulletproof tracing): done, 2026-11-04.**
  - The debuggee gets `-Xmx256m` (`TraceLimits.MAX_MEMORY_MB`).
  - A static shutdown hook in `JdiTracer` ends every child process when Raphael quits.
  - `trace/ParentWatch` is a Java agent (`-javaagent`, its jar written at runtime) that halts the debuggee if Raphael is killed. With the agent switched off, the test fails.
  - The exclusion filter `raphael.*` keeps the agent out of the trace.
  - Runaway recursion now says "f() calling itself again and again. Is its base case missing?".
  - `EdgeCaseTest`: 32 programs in `src/test/resources/edge/`, with expectations in `// key: value` headers.
  - `SoakTest` is tagged `soak`, excluded by default, and run nightly by `.github/workflows/soak.yml`.
  - JaCoCo 0.8.12: 78.5% lines, 70.5% branches.
  - 150 tests pass.
  - Known gap: Raphael killed during the half second while the program is starting (paused before the agent runs) can leave it waiting.
- **Next:** ROADMAP Stage 3, Raphael catches its own errors (exception hierarchy, safety net, log file, autosave, settings dialog).

## Who you're working with
- A sophomore, solo. They know C, basic Java and basic Python. They're new to Linux, the terminal and build tools.
- The course submission is done (2026-11-01). There's no deadline now.
- They want Raphael to be a real product, and they'll write a detailed report about it for their department and perhaps the college magazine. For that they must be able to explain how it works, which file does what (and its key methods), and where each OOP and Java concept is used. They don't need to explain every line.

## How to explain things
- **Architecture, not lines.** Explain what each class is for, its key methods and which OOP concept it shows. Skip line-by-line walkthroughs unless asked.
- **Start from what they know.** Compare with C where it helps, and define jargon on first use.
- **Give exact instructions** when they need to run something: where to type, the exact command, and what they should see.
- **Verify your own work** (run the tests, render a picture) before saying a stage is done, and say what you checked.
- **Keep replies concise.** No recap summaries, and don't repeat the same point in different sections.

## How to work here (from 2026-10-26)
- **Claude writes the code**, stage by stage in ROADMAP.md order, without stopping between stages. There are no mini-exercises, and PLAN.md's "How each stage works" no longer applies.
- **After each stage:** `mvn test` passes, the Progress section and the report chapter in `docs/report/` are updated, and Claude posts a short "for your teacher" note in chat (new files, key methods, OOP concepts).
- **Decision log:** Claude writes each stage's `DECISIONS.md` entry in the user's style: their question headings, short plain paragraphs, no repetition and no extra sections.
- **README.md is the teacher-facing guide:** how it works (the pipeline), a file map (file → job → key methods) and an OOP map (concept → where). Keep it up to date.
- **Java style is mostly classic:** private fields with constructors and getters. Use records only for tiny data holders, and no sealed types or pattern-matching switch.
- **OOP tags:** each class that shows an OOP concept starts with a short `// OOP: ...` comment.
- **Dependencies (relaxed 2026-11-04):** new libraries and build plugins are allowed. Before adding one, name it and say why. Today the app uses the JDK and FlatLaf, and the tests use JUnit 5. The JetBrains Mono NL font files in `src/main/resources/fonts/` (SIL Open Font License, `OFL.txt` beside them) are a resource, not a code dependency.
- **Explain before doing:** explain each stage (what, why, which concepts) before building it, so the user can write about it. New classes are fine (relaxed 2026-11-04).
- **The look (decided 2026-10-26, dark mode added 2026-11-01):** a plain terminal feel, white or dark. All colours, sizes, fonts and timings go in `render/Style`.
  - One monospace font (JetBrains Mono NL, which ships inside the app), square thin lines, lowercase terminal-style labels.
  - The editor's code is coloured as in an IDE (the user asked, 2026-10-31). Those colours avoid the blue, amber and red below.
  - Blue (`ACCENT`) means "about to happen": the next line, variables, markers, outlines. Amber (`CHANGE`) means "just changed". Grey is the line that just ran. Red is an error.
- **Motion:** shapes move on critically damped springs (no bounce) that keep their speed when retargeted. Text is drawn as glyph outlines. Leaving is faster than arriving. Don't go back to fixed-duration tweens.

## Environment
- Everything happens inside WSL 2 (Ubuntu 24.04), to keep Windows clean. WSLg shows Swing windows on the Windows screen.
- The project is at `~/raphael`. Run `java` and `mvn` there, never on Windows.
- The editor is **VS Code connected to WSL** (bottom-left shows `WSL: Ubuntu`), with Red Hat's Java and Microsoft's Java Debugger extensions installed in WSL. Antigravity isn't used.
- The old copy at `C:\Users\amulb\Desktop\Raphael` is out of date. Don't work there.

## Tech
- JDK 21, Maven, Swing + FlatLaf, JUnit 5.
- Package root is `raphael` (see the layout in PLAN.md).
- Compile student code with `-g`, so local variable names are available.
- Tracing puts a breakpoint on every line of the student's classes. JDK classes (`java.*`, `javax.*`, `sun.*`, `jdk.*`) get none. Step requests aren't used, because "step to the next line" never finishes in a one-line loop.
- Drain the debuggee's stdout/stderr on separate threads.
- Step limit is 2,000, so tracing always ends.
- Tracing runs off the UI thread (`SwingWorker`).

## Commands
- Build and test: `mvn test`
- Run: `mvn -q compile exec:java` (or the Run link above `main` in VS Code, or `Run Raphael.bat` on the Windows Desktop)
- After any code change: `scripts/build-app.sh`, which updates `dist/` for the launcher. VS Code's Java extension also compiles into `target/classes` and can leave broken class files that Maven won't replace (they contain "Unresolved compilation problems"). If that happens, run `mvn clean compile`.
- Windows app: `scripts/build-windows.sh`
- Design review: the `raphael-design-critic` agent in `.claude/agents/`. It only reports; it never edits.
