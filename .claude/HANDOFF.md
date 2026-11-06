# Handoff: starting the next session

Written 2026-11-06, at the end of the session that built Stages 7 and 8. Read this after `CLAUDE.md`, which loads by itself. CLAUDE.md has the full progress list, the rules and the look; this file has the state of things, what's open, and how to work smoothly.

## Where things stand
- **Done:** Version 2 Stages 0–8, plus the welcome page and the 103-example library (see CLAUDE.md → Progress).
- **Last commit:** `ef4f0e3` on `main`, pushed. CI (`tests`) is green on Windows, macOS and Linux.
- **Tests:** `mvn test` runs 442 tests in about 2.5 minutes. Most of that is `RememberingTest` (~65 s) and `ExamplesTest` (~30 s), which run every example.
- **Released:** v1.0.0 only, on 2026-11-06. Stage 7, Stage 8 and the welcome page are under "Unreleased" in `CHANGELOG.md`, so users of the installers don't have them yet.
- **Next stage:** ROADMAP **Stage 9, more views, all synchronised**: a view layout with tabs, a trace table, history, a memory view, a sequence diagram, a flowchart, an input view, and (stretch) a streams view. Explain it to the user before building it (what, why, which OOP concepts), as with every stage.

## Open decisions and to-dos
1. **Logo: done.** The user's custom character art (they have permission) is the logo, in `src/main/resources/logo/raphael.jpg`. It isn't under MIT, and the README's licence section says so; keep that line. `ui/Logo` draws it on a rounded tile. To regenerate the icons, run `java -cp target/classes scripts/AppIcon.java target/icons`, then copy `target/icons/raphael.png` to `docs/icon.png`.
2. **Release v1.1.0** when the user wants Stages 7–8 shipped:
   1. Bump `<version>` in `pom.xml` to 1.1.0.
   2. Turn "Unreleased" in `CHANGELOG.md` into "1.1.0 (date)".
   3. Commit and push.
   4. The **user** runs `git tag v1.1.0 && git push origin v1.1.0`. The safety classifier blocks Claude from publishing releases.

   `package.yml` then builds and self-tests the installers and publishes the release. The new `.raphael` file association only reaches users this way.
3. **Code signing:** apply to SignPath (free for open source) at some point; Apple signing later still. The installers are unsigned today, and the README explains the warnings.
4. **Report** (`docs/report/`):
   - Chapters 1–5, 7 and 8 are drafted.
   - 6, 9 and 10 are outlines.
   - Chapter 8's test counts and coverage (78.5% lines, 70.5% branches) predate Stages 7–8; re-measure with JaCoCo (`target/site/jacoco/index.html` after `mvn test`).
   - The format (docx or PDF) is chosen at Stage 14.
5. **Known gaps:**
   - Graphs are drawn as adjacency lists or matrices, and heaps as arrays, until Stage 10.
   - HiDPI hasn't been checked on a real 150% screen, only in CI screenshots.
   - If Raphael is killed during the half second while the program is starting, the program can be left waiting.
   - `edge/Threads.java` once timed out on Windows CI (it passed on the re-run), so watch for it.

## How to work here (the short version)
The rules are in CLAUDE.md and in the user's memory files (`~/.claude/projects/-home-amulb-raphael/memory/`):
- **must-explain-to-teacher:** explain each stage and each tool use before doing it.
- **explain-tradeoffs-plainly:** on a technical choice, give plain pros and cons and a recommendation, not a bare question.
- **ui-pass-every-stage:** at the end of every stage, run `raphael-ui-designer` on what changed and on the interface in general, then build its top items before calling the stage done.

After each stage, the checklist is:
1. `mvn clean test` passes. Use `clean`, because VS Code's Java extension leaves broken class files in `target/`.
2. Run `scripts/build-app.sh`, which updates `dist/` for the Windows launcher.
3. Look at the real window in both themes (the harnesses below), and check what you looked at.
4. Update the docs:
   - CLAUDE.md Progress;
   - the report chapter;
   - DECISIONS.md, in the user's style: their question headings, short plain paragraphs, no extra sections;
   - README (the file map and the OOP map);
   - CHANGELOG "Unreleased";
   - ROADMAP "(done)".
5. Post a short "for your teacher" note: new files, key methods, OOP concepts.
6. Commit, push, and **check CI on all three systems** (`gh run list`, `gh run watch <id>`). Windows has caught real bugs, such as line endings in test resources.

## The subagents (`.claude/agents/`)
Neither agent edits the source. Both write into the session's scratchpad. Claude builds what they recommend.

### `raphael-ui-designer`: chief UI designer
- **Model:** sonnet.
- **Tools:** Read, Bash, Write.
- **Job:** audits the interface against modern desktop apps, using real screenshots of the running app, never just the code, and writes an exact spec: a prioritised list with px sizes, Style tokens, components and methods to change, and how to verify.
- **When:** the UI pass at the end of every stage (the user's standing rule since 2026-11-05).
- **A good prompt gives it:**
  - what changed in the stage and which files;
  - how to build (`mvn -q package -DskipTests`, classpath `"target/app/*"`);
  - the harnesses in `.claude/tools/`;
  - the isolation rules below;
  - "at most 8 items, the top 5 buildable with existing Style tokens";
  - where to save the spec.

  Run it in the background, and write docs while it works.
- **Isolation rules to repeat to it every time:**
  - use its own `-Djava.util.prefs.userRoot=…`, `XDG_DATA_HOME` and `XDG_STATE_HOME` folders;
  - set `App.SETTINGS.putLong("lastUpdateCheck", System.currentTimeMillis())` before `App.main`, so no network request is made;
  - never click "Check for updates…" (it once did);
  - kill only its own java processes by PID (`jps -l`), never with `pkill -f <pattern>`, which also kills the shell running it.
- **Last spec:** Stage 8, all items built. Earlier mockups are in `docs/design/`.

### `raphael-design-critic`: animation and teaching critic
- **Model:** the default.
- **Tools:** Read, Bash.
- **Job:** checks that animation frames and pictures don't confuse a learner: what moves, what flashes, whether the picture at each step tells the truth.
- **When:** for visual changes to the picture or the motion. It'll matter for Stage 9's new views and Stage 10's new structures (graphs and heaps).
- **Open item for it:** in `lists/MergeSorted`, nodes keep their placement order (`ListOrder`), so the merged list draws an arrow curving back across it. Ask whether the list should re-lay out once it's merged.

## Verification tools (`.claude/tools/`, not compiled by Maven)
These are small programs that were used to check the examples and the window. Compile them against the app with `javac -cp "target/app/*" -d $OUT .claude/tools/X.java` (or `-cp target/classes` for the non-UI ones), and run them with `java -cp "target/app/*:$OUT" X …`. Use your own prefs and XDG folders, as above.

| Tool | What it does | Arguments |
|---|---|---|
| `RunAll` | Runs every catalog example; prints steps, end reason, time and output | optional path prefix, like `graphs/` |
| `Sheet` | Contact sheet: draws each example at 60% of its run and at its last step | from index, to index, out.png |
| `Expected` | Regenerates `src/test/resources/expected-output.txt` (check the outputs by hand first) | out file |
| `FilesProbe` | Prints how one example's files change, step by step | example name |
| `ShotsW` | Screenshots of the welcome page, a group, a 1000×640 window, the Examples menu and a submenu | `light`/`dark`, out folder |
| `Shots8` | Welcome with recent files, the WriteAndRead example's Files panel, and an opened recording | `light`/`dark`, out folder |

**Adding an example:**
1. Add a `.java` file under `src/main/resources/examples/<folder>/`; the class name must be unique.
2. Add a line to `examples/catalog.txt`: `path | title | one-sentence blurb`.
3. Run it with `RunAll`, look at it with `Sheet`, and add its output to `expected-output.txt`.

`ExamplesTest` fails if a file isn't in the catalog, or if the output differs.

## Pitfalls met in these sessions
- **Classifier blocks:** pushing a release tag was refused, so the user runs it.
- **Playwright has no Chrome** here, so web pages (GitHub Pages) can't be checked in a browser.
- **Harness hangs:**
  - a modal dialog (ErrorReporter's crash dialog or the draft-restore dialog) blocks `invokeAndWait`; read the app's log in the harness's XDG state folder to see why;
  - clicking a button by a name it doesn't have at that moment ("Play" while it's "Pause").
- **Heredocs:** `\uFEFF` and similar escapes inside a heredoc can end up as real characters in Java source.
- **Line endings:** test resources read on Windows have `\r\n`. Normalise before comparing.
- **Slow examples:** `bugs/MissingBaseCase` takes about 8 s, because it runs to the 2,000-step limit. It's left out of `RememberingTest` on purpose.
- **How the program starts now:** the student's program now runs in a temp `raphael-run-*` folder started with `ProcessBuilder` and a listening connector (`JdiTracer.launch`), not JDI's launching connector. `vm.process()` is null there, so use the `Process` that `launch()` returns.

## Environment
- WSL 2 (Ubuntu 24.04), project at `~/raphael`, JDK 21, Maven, VS Code connected to WSL.
- Run with `mvn -q compile exec:java`, or `Run Raphael.bat` on the Windows Desktop, which runs `dist/` (`scripts/build-app.sh`).
- **GitHub:** `electricwhitener/raphael` (public, MIT). The site is https://electricwhitener.github.io/raphael/ (served from `docs/`).
- **Workflows:**
  - `ci.yml`: tests on 3 systems on every push.
  - `soak.yml`: nightly, each original example 100 times.
  - `package.yml`: installers, on `v*` tags and by hand.
