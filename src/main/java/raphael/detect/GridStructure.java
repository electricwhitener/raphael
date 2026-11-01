package raphael.detect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import raphael.model.ArrayNode;

// A two-dimensional array (an array of arrays), drawn as a table, e.g. a DP table.
// OOP: inheritance (extends Structure) and overriding.
public class GridStructure extends Structure {

    private final ArrayNode grid;       // the outer array
    private final List<ArrayNode> rows; // one per row; null where the row itself is null

    public GridStructure(ArrayNode grid, List<ArrayNode> rows) {
        this.grid = grid;
        this.rows = Collections.unmodifiableList(new ArrayList<>(rows));
    }

    public ArrayNode getGrid() {
        return grid;
    }

    public List<ArrayNode> getRows() {
        return rows;
    }

    @Override
    public Set<Long> getObjectIds() {
        Set<Long> ids = new LinkedHashSet<>();
        ids.add(grid.getId());
        for (ArrayNode row : rows) {
            if (row != null) {
                ids.add(row.getId());
            }
        }
        return ids;
    }

    @Override
    public String getKind() {
        return "grid";
    }
}
