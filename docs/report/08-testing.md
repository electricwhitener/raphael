# 8. Quality and testing

## 8.1 The test suite
Raphael has 150 JUnit 5 tests in its normal run (version 1 had 107), plus a separate soak test (8.4). Each package is tested on its own:
- **Compiling:** errors on the right line, file rules, invisible characters.
- **Tracing:** a second JVM, with the exact line path of loops, the step limit, crashes, input, collections and stop.
- **Detection:** snapshots built by hand, with no JVM.
- **Layout and animation:** scenes and springs checked by number.
- **The window:** key handling and the editor.

`ExamplesTest` runs every example in the menu to its expected end.

## 8.2 Testing on Windows, macOS and Linux
Raphael is written in Java, which runs on every system, but a program can still depend on its computer without meaning to. Version 1 was only ever run on Linux inside WSL. Since stage 1, GitHub Actions runs the whole suite on fresh Windows, macOS and Linux machines on every push. Linux runs inside a virtual screen (`xvfb-run`), because some tests open Swing windows.

| System | Tests run | Failures | Skipped |
|---|---|---|---|
| Windows (windows-latest) | 116 | 0 | 0 |
| macOS (macos-latest) | 116 | 0 | 0 |
| Linux (ubuntu-latest) | 116 | 0 | 0 |

(At stage 1. Every later push runs the whole suite on all three.)

Reviewing the code for system-dependent behaviour found five problems, each now fixed and tested:

| Problem | Effect | Fix |
|---|---|---|
| Shortcuts written as Ctrl | On a Mac, Cmd+S did nothing | `Shortcuts.key()` uses Cmd on a Mac; `Shortcuts.label()` shows Mac key names |
| Open and save started at a WSL-only path | No start folder on other systems | The Desktop on every system |
| `toLowerCase()` followed the computer's language | On Turkish computers, `"SIZE"` became `"sıze"`, and a B-tree's count was missed | `Locale.ROOT` everywhere; a test runs with Turkish as the language |
| javac used the computer's encoding and language | Errors in other languages; non-ASCII code could be misread | UTF-8 and English, always |
| Always light at first launch | Didn't match a dark system | `SystemTheme` asks the system once |

The two Mac tests caught real mistakes during development:
- "Option+Down, Option+Up" was too long for the keys screen's column.
- One description still said "Ctrl".

## 8.3 Edge cases
A student can paste anything, so Raphael is tested against programs chosen to be awkward. Each of the 32 programs in `src/test/resources/edge/` states at its top what must happen:

```java
// expect: EXCEPTION
// input: 1 2
// message: NoSuchElementException
```

`EdgeCaseTest`, a JUnit parameterised test, runs each one and checks those statements. It then lays out and draws every step, as the window would, so a program that crashed the drawing would fail too.

| Area | Programs |
|---|---|
| Ending | empty `main`; `System.exit` mid-run; a thread that outlives `main`; an 8-second sleep (timeout); infinite loops with and without output; 1 MB of output |
| Errors | recursion with no base case; running out of memory (256 MB cap); `Scanner` running out of input; wrong input type; caught exceptions, `finally` and multi-catch; a compile error on the right line |
| Language features | generics; lambdas and streams; inner, anonymous and nested classes; interfaces with default and static methods; records and enums with a switch expression; `var`, varargs, text blocks and pattern matching; static initialisers; `printf` |
| Data | a 10,000-cell array and a 300 × 300 grid; cycles; nulls everywhere; `Integer` caching; `NaN`, infinity, overflow and `Long.MAX_VALUE` |
| Text | unicode names and emoji; a 3,000-character line; Windows line endings and tabs; a `package` line and several top-level classes |
| Threads | two threads updating a shared counter |

All 32 now pass. Raphael already behaved correctly on every one. The only change was a clearer message: recursion with no base case reaches the 2,000-step limit long before Java's `StackOverflowError`, so Raphael now says which method keeps calling itself and asks whether its base case is missing.

**Cleaning up after itself.** Two further tests check that the student's program never outlives Raphael. A helper program plays the part of Raphael and is ended in two ways:
- **Quitting normally:** a shutdown hook ends every program Raphael started.
- **Killed outright:** shutdown hooks don't run, so a small Java agent inside the student's program (`ParentWatch`) watches Raphael's process and halts the program when it disappears.

With the agent switched off, the second test fails, which shows it is what makes the difference.

## 8.4 Soak testing
Running the same programs many times caught a timing bug in version 1: about 1 run in 60 recorded no steps. The program's JVM was released twice, so it sometimes ran on before its breakpoints were set. It was fixed on 2026-11-01, and 150 of 150 runs were then right. Since stage 2, `SoakTest` repeats this for good. It runs every example 100 times, and every run must take exactly the same lines, print the same output and end the same way. It takes about 10 minutes per system, so it is left out of the normal test run. A separate CI job (`soak.yml`) runs it every night on Windows, macOS and Linux.

The first soak run (2026-11-04) passed on all three systems: 900 runs each, with every run of an example identical to its first.

| System | Runs | Differences | Time |
|---|---|---|---|
| macOS | 900 | 0 | 3 minutes |
| Linux | 900 | 0 | 8 minutes |
| Windows | 900 | 0 | 41 minutes |

Windows is much slower because starting a new JVM, which every run does, takes longer there. That is a lead for stage 4.

## 8.5 Coverage
JaCoCo, a Maven plugin, records which lines and branches of Raphael the tests execute.

| Package | Lines covered |
|---|---|
| `compile` | 99% |
| `model` | 97% |
| `detect` | 95% |
| `trace` | 93% |
| `render` | 89% |
| `ui` | 58% |
| **All** | **78.5%** of lines, **70.5%** of branches |

The engine, from compiling to drawing, is almost fully exercised. The window code is lower, because much of it only runs in response to clicks and menus.

## 8.6 Performance
Performance work started with measurement. A timing program recorded three typical programs and timed compiling, recording and layout separately. Nearly all the time went to recording: reading the paused program's memory at every step.

**Remembering unchanged objects.** Reading an object is a round trip between the two JVMs. Version 1 read every reachable object at every step, so building a 500-node list (2,000 steps) made 285,857 reads.
- JDI can report every write to a field (a modification watchpoint). Raphael asks for this on every field of the student's classes, with the `SUSPEND_NONE` policy, so the program is not stopped.
- Objects are then remembered between steps, and only those whose fields were written are read again.
- Arrays (whose cell writes cannot be watched) and multi-threaded programs (where a write may be pending) are always read in full.
- A test records 38 programs both ways and requires identical steps.

**Ending runs promptly.** After the program finished, its JVM waited about 320 ms for the attached debugger before exiting. Raphael now detaches as soon as the program reports its end.

**Building pictures on demand.** Building every step's picture up front used 155 MB for 2,000 steps. The pictures are now built when shown and kept in a 64-entry least-recently-used cache (`SceneList`), with list positions learned for the whole run beforehand, so a step looks the same however it is reached.

| Measurement | Version 1 | Now |
|---|---|---|
| Recording a 500-node list (2,000 steps) | 20.6 s | 1.9 s |
| Objects read for that run | 285,857 | 856 |
| Recording an empty program | 380 ms | 66 ms |
| Recording BubbleSort | 434 ms | 114 ms |
| Recording 604 steps with an `ArrayList` and a `HashMap` | 2.6 s | 1.7 s |
| Memory for the pictures of 2,000 steps | 155 MB | under 1 MB |
| Drawing one animation frame of the largest picture (574 shapes) | — | 2 ms average, 7 ms worst |
| Opening Raphael's window | — | about 0.5 s |

Measured on the development laptop (WSL 2). Because CI machines vary in speed, the regression test counts reads rather than time: the 500-node list must read fewer than two objects per step.
