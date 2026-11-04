# 8. Quality and testing

*Sections 8.3, 8.5 and 8.6 are written as ROADMAP stages 2 and 4 are done.*

## 8.1 The test suite
Raphael has 116 JUnit 5 tests (version 1 had 107). Each package is tested on its own:
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
*Stage 2.*

## 8.4 Soak testing
Running the same programs many times caught a timing bug in version 1: about 1 run in 60 recorded no steps. The program's JVM was released twice, so it sometimes ran on before its breakpoints were set. It was fixed on 2026-11-01, and 150 of 150 runs were then right. Stage 2 adds this kind of repeated run to CI.

## 8.5 Coverage
*Stage 2.*

## 8.6 Performance
*Stage 4.*
