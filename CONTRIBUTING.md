# Contributing to Raphael

Thanks for helping. Bug reports, ideas and code are all welcome.

## Reporting a bug
Open an [issue](https://github.com/electricwhitener/raphael/issues/new/choose) with the **Bug report** template. The most useful things to include:
- the Java program you ran (or a smaller one that shows the problem),
- what you expected and what happened instead,
- your system and Raphael's version (Help → About Raphael),
- if Raphael showed its "ran into a problem" dialog, the text from **Copy details**.

## Building and testing
You need JDK 21 and Maven 3.8 or newer.

```bash
mvn test                       # the full test suite (about 2 minutes)
mvn -q compile exec:java       # run Raphael from the source
scripts/package.sh             # a ready-to-run copy in target/installer/Raphael
```

Every pull request runs the tests on Windows, macOS and Linux.

## How the code is organised
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) explains the pipeline (compile → trace → detect → lay out → animate) and has a map of every file. [DECISIONS.md](DECISIONS.md) explains why things are the way they are, and [ROADMAP.md](ROADMAP.md) lists what's planned.

A few conventions:
- Every colour, size, font and timing lives in `render/Style`.
- Shapes move on critically damped springs; colours mean the same everywhere (blue: about to happen, amber: just changed, red: an error).
- Classic Java style: private fields, constructors and getters; records only for small data holders.
- Each change comes with tests.

## Licence
By contributing, you agree that your contribution is licensed under the [MIT License](LICENSE), like the rest of Raphael.
