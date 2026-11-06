package raphael.io;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import raphael.model.FrameSnapshot;
import raphael.model.HeapObject;
import raphael.model.ReferenceValue;
import raphael.model.Snapshot;
import raphael.model.Trace;
import raphael.model.Value;

// The run as a table, in a CSV file a spreadsheet opens: one row per step, with its line, the
// method running, a column for each variable, and what the step printed. It's the hand trace
// students write on paper, done for them.
// OOP: a utility class; a PrintWriter on a BufferedWriter (the Decorator pattern again), closed
// by try-with-resources.
public final class StepLog {

    private StepLog() {
    }

    public static void write(Path file, Trace trace) throws IOException {
        // A column for every variable any step's running method has, in the order they first appear.
        Set<String> names = new LinkedHashSet<>();
        for (Snapshot step : trace.getSnapshots()) {
            if (!step.getFrames().isEmpty()) {
                names.addAll(step.getFrames().get(0).getLocals().keySet());
            }
        }
        try (BufferedWriter buffer = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
             PrintWriter out = new PrintWriter(buffer)) {
            out.print('﻿'); // tells Excel the file is UTF-8
            List<String> header = new ArrayList<>(List.of("step", "line", "method"));
            header.addAll(names);
            header.add("printed");
            row(out, header);
            for (int i = 0; i < trace.size(); i++) {
                Snapshot step = trace.get(i);
                List<String> cells = new ArrayList<>();
                cells.add(String.valueOf(i + 1));
                cells.add(step.getLine() > 0 ? String.valueOf(step.getLine()) : "");
                Map<String, Value> locals = Map.of();
                if (!step.getFrames().isEmpty()) {
                    FrameSnapshot running = step.getFrames().get(0);
                    cells.add(running.getMethodName() + "()");
                    locals = running.getLocals();
                } else {
                    cells.add("");
                }
                for (String name : names) {
                    Value value = locals.get(name);
                    cells.add(value == null ? "" : text(value, step));
                }
                String before = i > 0 ? trace.getOutputAt(i - 1) : "";
                String now = trace.getOutputAt(i);
                cells.add(now.startsWith(before) ? now.substring(before.length()).strip() : "");
                row(out, cells);
            }
            if (out.checkError()) { // a PrintWriter keeps quiet about failures; this asks
                throw new IOException("the disk refused some of the writing");
            }
        }
    }

    // The same text the variables panel shows.
    private static String text(Value value, Snapshot snapshot) {
        if (value instanceof ReferenceValue) {
            HeapObject object = snapshot.getObject(((ReferenceValue) value).id());
            if (object != null) {
                return object.describe(snapshot);
            }
        }
        return value.display();
    }

    // One line of CSV. A cell with a comma, a quote or a line break goes in quotes, with any quote
    // inside written twice: Ben said "hi" → "Ben said ""hi""".
    static void row(PrintWriter out, List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            String cell = cells.get(i);
            if (cell.contains(",") || cell.contains("\"") || cell.contains("\n") || cell.contains("\r")) {
                cell = "\"" + cell.replace("\"", "\"\"") + "\"";
            }
            out.print(i == 0 ? cell : "," + cell);
        }
        out.print("\r\n"); // CSV's own line ending, whatever the system
    }
}
