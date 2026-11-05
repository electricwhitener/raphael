package raphael.trace;

// The limits for one run that the user can change in settings: how many steps, how long the
// program may sit idle, and how much memory it may use. The defaults are in TraceLimits.
// OOP: a record (a small unchangeable data class); its compact constructor checks the values,
// so a RunLimits outside the allowed ranges can't exist (encapsulation).
public record RunLimits(int maxSteps, int timeoutSeconds, int memoryMb) {

    public static final int MIN_STEPS = 100;
    public static final int MAX_STEPS = 20_000;
    public static final int MIN_TIMEOUT = 1;
    public static final int MAX_TIMEOUT = 60;
    public static final int MIN_MEMORY = 64;
    public static final int MAX_MEMORY = 2048;

    public static final RunLimits DEFAULTS = new RunLimits(TraceLimits.MAX_STEPS,
            (int) (TraceLimits.EVENT_TIMEOUT_MS / 1000), TraceLimits.MAX_MEMORY_MB);

    public RunLimits {
        check("steps", maxSteps, MIN_STEPS, MAX_STEPS);
        check("seconds", timeoutSeconds, MIN_TIMEOUT, MAX_TIMEOUT);
        check("MB of memory", memoryMb, MIN_MEMORY, MAX_MEMORY);
    }

    private static void check(String what, int value, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(what + " must be from " + min + " to " + max + ", not " + value);
        }
    }
}
