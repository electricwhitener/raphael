package raphael.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import raphael.compile.SourceCompiler;
import raphael.model.Trace;
import raphael.trace.JdiTracer;

class StepLogTest {

    @TempDir
    Path folder;

    @Test
    void cellsWithCommasOrQuotesAreQuoted() {
        StringWriter text = new StringWriter();
        try (PrintWriter out = new PrintWriter(text)) {
            StepLog.row(out, List.of("1", "a, b", "Ben said \"hi\"", "two\nlines"));
        }
        assertEquals("1,\"a, b\",\"Ben said \"\"hi\"\"\",\"two\nlines\"\r\n", text.toString());
    }

    @Test
    void eachStepIsARowWithItsVariables() throws Exception {
        Trace trace = new JdiTracer().trace(new SourceCompiler().compile("""
                public class Sum {
                    public static void main(String[] args) {
                        int[] a = {4, 5};
                        int sum = 0;
                        for (int i = 0; i < a.length; i++) {
                            sum += a[i];
                        }
                        System.out.println(sum);
                    }
                }
                """), "");
        Path file = folder.resolve("steps.csv");
        StepLog.write(file, trace);
        List<String> rows = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertEquals("\uFEFFstep,line,method,a,sum,i,printed", rows.get(0));
        assertEquals(trace.size() + 1, rows.size());
        assertEquals("1,3,main(),,,,", rows.get(1));
        assertEquals("2,4,main(),\"int[2] [4, 5]\",,,", rows.get(2));
        assertEquals("7,5,main(),\"int[2] [4, 5]\",9,1,", rows.get(7)); // the loop's last check: i is 1, sum is 9
        assertEquals(trace.size() + ",9,main(),\"int[2] [4, 5]\",9,,9", rows.get(rows.size() - 1)); // 9 was printed
    }
}
