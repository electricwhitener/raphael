package raphael.trace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RunLimitsTest {

    @Test
    void theDefaultsAreTheOldLimits() {
        assertEquals(TraceLimits.MAX_STEPS, RunLimits.DEFAULTS.maxSteps());
        assertEquals(5, RunLimits.DEFAULTS.timeoutSeconds());
        assertEquals(TraceLimits.MAX_MEMORY_MB, RunLimits.DEFAULTS.memoryMb());
    }

    @Test
    void valuesOutsideTheRangesCantExist() {
        assertThrows(IllegalArgumentException.class, () -> new RunLimits(10, 5, 256));
        assertThrows(IllegalArgumentException.class, () -> new RunLimits(2000, 0, 256));
        assertThrows(IllegalArgumentException.class, () -> new RunLimits(2000, 5, 100_000));
    }
}
