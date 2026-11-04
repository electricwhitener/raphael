# 10. Limits and future work

*Written last. Known items so far:*

- One file per program, with a `main` method.
- A step is one line: `for (...) sum += a[i];` on one line runs as one step.
- Runs stop at 2,000 steps; very large programs are slow to record (stage 4 targets this).
- Pasted code runs with the user's own permissions. Java 21 has no portable sandbox.
- Other languages: only `SourceCompiler`, `JdiTracer` and `HeapReader` depend on Java's tools, so another language would need its own compiler and tracer that produce the same snapshots. C/C++ would help DSA courses most, and is the hardest; Python is the easiest.
