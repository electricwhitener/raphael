package raphael.detect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Reads the program's text to find out which variables are indexes into which arrays:
//   arr[j + 1]          → j is an index into arr
//   paths[r][c]         → r indexes paths' rows, c its columns
//   x.keys[i]           → i is an index into the keys array of the object x
//   i < arr.length      → i walks along arr
// Raphael uses this to draw markers like "j" under array cells, and to outline the cells that
// the next line will read or write.
// OOP: encapsulation (the maps are private); nested records for one access and one index.
public class IndexUsage {

    // An index like "j + 1" (a variable plus a constant), "0" (just a constant), or "j + T"
    // (two variables: other is added, or subtracted when otherSign is -1).
    public record Index(String variable, int offset, String other, int otherSign) {

        public Index(String variable, int offset) {
            this(variable, offset, null, 0);
        }
    }

    // One array access on a line, e.g. paths[r - 1][c]. column is null for a 1D access.
    public record Access(String array, Index row, Index column, boolean write) {
    }

    // A name, possibly reached through objects: arr, x.keys, this.data
    private static final String NAME = "(?:[A-Za-z_]\\w*\\.)*[A-Za-z_]\\w*";
    private static final Pattern ACCESS = Pattern.compile(
            "(" + NAME + ")\\s*\\[([^\\[\\]]*)\\](?:\\s*\\[([^\\[\\]]*)\\])?");
    private static final Pattern INDEX = Pattern.compile(
            "^(\\+\\+|--)?\\s*(" + NAME + ")\\s*(?:\\+\\+|--)?\\s*(?:([+-])\\s*(\\d{1,9}))?$");
    private static final Pattern TWO_NAMES = Pattern.compile("^(" + NAME + ")\\s*([+-])\\s*(" + NAME + ")$");
    private static final Pattern NUMBER = Pattern.compile("^\\d{1,9}$");
    private static final Pattern LENGTH_CHECK = Pattern.compile(
            "(" + NAME + ")\\s*(?:<=|<|>=|>)\\s*(" + NAME + ")\\.length");

    private final List<String> lines;
    private final Map<String, Set<String>> rowVariables = new HashMap<>();
    private final Map<String, Set<String>> columnVariables = new HashMap<>();

    public IndexUsage(String source) {
        lines = source.lines().toList();
        for (String line : lines) {
            for (Access access : accessesIn(line)) {
                remember(rowVariables, access.array(), access.row());
                remember(columnVariables, access.array(), access.column());
            }
            Matcher loop = LENGTH_CHECK.matcher(withoutComment(line));
            while (loop.find()) {
                rowVariables.computeIfAbsent(loop.group(2), k -> new LinkedHashSet<>()).add(loop.group(1));
            }
        }
    }

    // The variables used to index this array's rows (or, for a 1D array, its cells).
    public Set<String> rowVariables(String array) {
        return rowVariables.getOrDefault(array, Set.of());
    }

    // The variables used to index this array's columns (2D arrays only).
    public Set<String> columnVariables(String array) {
        return columnVariables.getOrDefault(array, Set.of());
    }

    // The array accesses on one line of the program (lines count from 1).
    public List<Access> accessesOnLine(int line) {
        return accessesIn(lineText(line));
    }

    // The text of one line (lines count from 1), or "" if there's no such line.
    public String lineText(int line) {
        return line >= 1 && line <= lines.size() ? lines.get(line - 1) : "";
    }

    // Finds every array access in one line of code. In each statement, accesses before the "="
    // (or followed by ++ or --) are writes; the rest are reads.
    public static List<Access> accessesIn(String code) {
        List<Access> found = new ArrayList<>();
        for (String statement : withoutComment(code).split(";", -1)) {
            int assignment = assignmentIn(statement);
            Matcher m = ACCESS.matcher(statement);
            while (m.find()) {
                if (statement.substring(0, m.start()).trim().endsWith("new")) {
                    continue; // "new int[n]" makes an array; it doesn't index one
                }
                Index row = parseIndex(m.group(2));
                Index column = m.group(3) == null ? null : parseIndex(m.group(3));
                if (row == null || (m.group(3) != null && column == null)) {
                    continue; // empty brackets (a declaration) or an index too complicated to follow
                }
                boolean write = (assignment >= 0 && m.end() <= assignment)
                        || statement.substring(m.end()).trim().startsWith("++")
                        || statement.substring(m.end()).trim().startsWith("--");
                found.add(new Access(m.group(1), row, column, write));
            }
        }
        return found;
    }

    // Only a variable used plainly (arr[i], not just arr[i - 1]) counts as an index: in
    // "paths[rows - 1]", rows is a size, not a position.
    private static void remember(Map<String, Set<String>> map, String array, Index index) {
        if (index != null && index.variable() != null && index.offset() == 0 && index.other() == null) {
            map.computeIfAbsent(array, k -> new LinkedHashSet<>()).add(index.variable());
        }
    }

    private static Index parseIndex(String text) {
        String t = text.trim();
        if (NUMBER.matcher(t).matches()) {
            return new Index(null, Integer.parseInt(t));
        }
        Matcher two = TWO_NAMES.matcher(t);
        if (two.matches()) {
            return new Index(two.group(1), 0, two.group(3), "-".equals(two.group(2)) ? -1 : 1);
        }
        Matcher m = INDEX.matcher(t);
        if (!m.matches()) {
            return null;
        }
        int offset = m.group(4) == null ? 0 : Integer.parseInt(m.group(4)) * ("-".equals(m.group(3)) ? -1 : 1);
        if ("++".equals(m.group(1))) {
            offset++; // ++i is used after it goes up
        } else if ("--".equals(m.group(1))) {
            offset--;
        }
        return new Index(m.group(2), offset);
    }

    // Where the assignment "=" is in a statement ("+=" counts; "==", "<=", ">=", "!=" don't), or -1.
    private static int assignmentIn(String statement) {
        for (int i = 0; i < statement.length(); i++) {
            if (statement.charAt(i) != '=') {
                continue;
            }
            char before = i > 0 ? statement.charAt(i - 1) : ' ';
            char after = i + 1 < statement.length() ? statement.charAt(i + 1) : ' ';
            if (after == '=') {
                i++;
                continue;
            }
            if (before == '!' || before == '<' || before == '>' || before == '=') {
                continue;
            }
            return i;
        }
        return -1;
    }

    private static String withoutComment(String line) {
        int comment = line.indexOf("//");
        return comment >= 0 ? line.substring(0, comment) : line;
    }
}
