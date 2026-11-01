package raphael.detect;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class IndexUsageTest {

    @Test
    void findsTheVariablesThatIndexEachArray() {
        IndexUsage usage = new IndexUsage("""
                int[] arr = new int[n];
                for (int i = 0; i < arr.length - 1; i++) {
                    if (arr[j] > arr[j + 1]) { }
                }
                paths[r][c] = paths[r - 1][c] + paths[r][c - 1];
                System.out.println(paths[rows - 1][cols - 1]);
                """);

        assertEquals(Set.of("i", "j"), usage.rowVariables("arr")); // i from "i < arr.length"
        assertEquals(Set.of("r"), usage.rowVariables("paths"));    // rows is only used as "rows - 1"
        assertEquals(Set.of("c"), usage.columnVariables("paths"));
        assertEquals(Set.of(), usage.rowVariables("int"));          // "new int[n]" isn't an index
    }

    @Test
    void tellsReadsFromWrites() {
        List<IndexUsage.Access> accesses = IndexUsage.accessesIn("arr[j] = arr[j + 1];");

        assertEquals(2, accesses.size());
        assertTrue(accesses.get(0).write());
        assertEquals(new IndexUsage.Index("j", 0), accesses.get(0).row());
        assertFalse(accesses.get(1).write());
        assertEquals(new IndexUsage.Index("j", 1), accesses.get(1).row());
    }

    @Test
    void readsFieldsThroughObjectsAndTwoVariableIndexes() {
        IndexUsage usage = new IndexUsage("x.keys[i + 1] = x.keys[i];");
        assertEquals(Set.of("i"), usage.rowVariables("x.keys"));

        List<IndexUsage.Access> split = IndexUsage.accessesIn("z.keys[j] = y.keys[j + T];");
        assertEquals("y.keys", split.get(1).array());
        assertEquals(new IndexUsage.Index("j", 0, "T", 1), split.get(1).row());
    }

    @Test
    void comparisonsAreReadsAndConstantsWork() {
        List<IndexUsage.Access> compare = IndexUsage.accessesIn("if (dp[i] == dp[0]) {");
        assertFalse(compare.get(0).write());
        assertFalse(compare.get(1).write());
        assertEquals(new IndexUsage.Index(null, 0), compare.get(1).row());

        List<IndexUsage.Access> grid = IndexUsage.accessesIn("grid[r][c] += 1; // add one");
        assertTrue(grid.get(0).write());
        assertEquals(new IndexUsage.Index("c", 0), grid.get(0).column());
    }
}
