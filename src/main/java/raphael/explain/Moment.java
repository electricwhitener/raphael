package raphael.explain;

// A key moment of a run: the step it happens at, what kind it is ("base-case", "caught",
// "refused"...), a short title, a plain sentence made from the run's real values, and whether
// playing pauses there (a key moment) or it's only noted (listed, but play goes on).
// file and line: where in the code it is (file null for a program of one file).
// OOP: a record, a small unchangeable data class.
public record Moment(int step, String kind, String title, String text, boolean pauses, String file, int line) {

    // The same moment, but only noted, not paused at (see MomentFinder's rule against noise).
    Moment noted() {
        return new Moment(step, kind, title, text, false, file, line);
    }
}
