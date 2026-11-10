package raphael.render;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import raphael.detect.ArrayStructure;
import raphael.detect.BinaryTreeStructure;
import raphael.detect.GenericStructure;
import raphael.detect.GridStructure;
import raphael.detect.IndexUsage;
import raphael.detect.LinkedListStructure;
import raphael.detect.LinkedStructure;
import raphael.detect.MultiwayTreeStructure;
import raphael.detect.Structure;
import raphael.detect.StructureDetector;
import raphael.model.ArrayNode;
import raphael.model.EndReason;
import raphael.model.FrameSnapshot;
import raphael.model.HeapObject;
import raphael.model.ObjectNode;
import raphael.model.PrimitiveValue;
import raphael.model.ReferenceValue;
import raphael.model.Snapshot;
import raphael.model.Trace;
import raphael.model.Value;

// Builds the picture (a Scene) for every step of a Trace, once, right after tracing.
// First it measures the whole run (widest values, room for index markers), so nothing jumps
// between steps. Then, for each Snapshot, it:
//   1. detects the structures (StructureDetector),
//   2. lays each one out with its renderer, in rows from top to bottom. Arrays share rows, and so
//      do plain object boxes, which keeps the picture big. So do trees of one class, so a node
//      that isn't linked yet waits beside its tree instead of landing under something else,
//   3. outlines the cells the next line will read or write (using IndexUsage),
//   4. adds the variable labels, like "curr", above the objects they point at,
//   5. notes a value the line before copied between cells, so it can fly across.
// OOP: encapsulation (helpers are private). It only talks to renderers through the abstract
// StructureRenderer type (polymorphism).
public class SceneBuilder {

    // A plain copy from one cell to another, like "arr[j] = arr[j + 1];"
    private static final Pattern COPY = Pattern.compile("^\\s*[^=;]+\\]\\s*=\\s*[^=;]+\\]\\s*;\\s*(//.*)?$");

    // A cell of an array (for a 2D array: the cell of one row).
    private record Cell(ArrayNode line, int index) {
        String key() {
            return "#" + line.getId() + "[" + index + "]";
        }
    }

    private final StructureDetector detector = new StructureDetector();
    private final IndexUsage indexes; // the whole program: which variables index which arrays
    private final Map<String, IndexUsage> byFile = new java.util.HashMap<>(); // each file's lines, by path
    private boolean crashAhead = true; // false at steps that the recorded run shows don't crash

    public SceneBuilder() {
        this("");
    }

    // source is the program's text: it tells which variables are indexes into which arrays.
    public SceneBuilder(String source) {
        this.indexes = new IndexUsage(source);
        this.heapNames = heapArrays(source);
        this.stackNames = stackArrays(source);
    }

    // A program of several files: path → text. A step's line is read from the file it's in.
    public SceneBuilder(List<raphael.model.SourceFile> files) {
        StringBuilder all = new StringBuilder();
        for (raphael.model.SourceFile file : files) {
            byFile.put(file.path(), new IndexUsage(file.text()));
            all.append(file.text()).append('\n');
        }
        this.indexes = new IndexUsage(all.toString());
        this.heapNames = heapArrays(all.toString());
        this.stackNames = stackArrays(all.toString());
    }

    private final Set<String> stackNames; // arrays the code indexes with top: a stack

    private static Set<String> stackArrays(String source) {
        Set<String> arrays = new HashSet<>();
        java.util.regex.Matcher m = INDEXED.matcher(source);
        while (m.find()) {
            if (Pattern.compile("\\btop\\b").matcher(m.group(2)).find()) {
                arrays.add(m.group(1));
            }
        }
        return arrays;
    }

    // ---- heaps ----

    // Heap index arithmetic: 2 * i + 1, 2 * i + 2, (i - 1) / 2.
    private static final Pattern HEAP_INDEX = Pattern.compile(
            "2\\s*\\*\\s*\\w+\\s*\\+\\s*[12]\\b|\\(\\s*\\w+\\s*-\\s*1\\s*\\)\\s*/\\s*2");
    private static final Pattern ASSIGNED = Pattern.compile("\\b(\\w+)\\s*=\\s*([^;=]+);");
    private static final Pattern INDEXED = Pattern.compile("\\b(\\w+)\\s*\\[\\s*([^\\[\\]]+)\\]");
    private static final Set<String> HEAP_SIZE_NAMES = Set.of("size", "n", "heapsize", "count", "length", "len");

    private final Set<String> heapNames; // arrays the code indexes the way a heap is indexed

    // The arrays indexed with heap arithmetic: heap[(i - 1) / 2], or arr[left] after left = 2 * i + 1.
    private static Set<String> heapArrays(String source) {
        Set<String> heapVariables = new HashSet<>();
        java.util.regex.Matcher m = ASSIGNED.matcher(source);
        while (m.find()) {
            if (HEAP_INDEX.matcher(m.group(2)).find()) {
                heapVariables.add(m.group(1));
            }
        }
        Set<String> arrays = new HashSet<>();
        m = INDEXED.matcher(source);
        while (m.find()) {
            String index = m.group(2).trim();
            if (HEAP_INDEX.matcher(index).find() || heapVariables.contains(index)) {
                arrays.add(m.group(1));
            }
        }
        return arrays;
    }

    // Which arrays are heaps, learned over the whole run, and how much of each is in the heap
    // at every step: a variable named like a size, else what it was the step before.
    private List<Map<Long, Integer>> learnHeaps(Trace trace, RenderContext context) {
        List<Map<Long, Integer>> sizes = new ArrayList<>();
        Map<Long, Integer> last = new HashMap<>();
        for (int i = 0; i < trace.size(); i++) {
            Snapshot snapshot = trace.get(i);
            Map<String, Value> visible = visibleVariables(snapshot);
            for (Map.Entry<String, Value> named : visible.entrySet()) {
                ArrayNode array = arrayOf(snapshot, named.getValue());
                if (array != null && stackNames.contains(named.getKey()) && !array.getElementType().endsWith("[]")) {
                    context.markStack(array.getId());
                }
                if (array != null && heapNames.contains(named.getKey()) && !array.getElements().isEmpty()
                        && array.getElements().get(0) instanceof PrimitiveValue) {
                    context.markHeap(array.getId(), array.getLength());
                }
            }
            for (HeapObject object : snapshot.getHeap().values()) {
                if (object instanceof ArrayNode && object.getTypeName().endsWith("PriorityQueue")) {
                    context.markHeap(object.getId(), ((ArrayNode) object).getElements().size()); // its array is a heap
                }
            }
            for (HeapObject object : snapshot.getHeap().values()) {
                if (object instanceof ArrayNode && context.isHeap(object.getId())) {
                    context.markHeap(object.getId(), ((ArrayNode) object).getElements().size());
                    Integer size = null;
                    for (Map.Entry<String, Value> named : visible.entrySet()) {
                        String name = named.getKey().substring(named.getKey().lastIndexOf('.') + 1).toLowerCase(java.util.Locale.ROOT);
                        if (HEAP_SIZE_NAMES.contains(name) && named.getValue() instanceof PrimitiveValue) {
                            try {
                                int value = Integer.parseInt(named.getValue().display());
                                if (value >= 0 && value <= ((ArrayNode) object).getLength()) {
                                    size = value;
                                    break;
                                }
                            } catch (NumberFormatException notAnInt) {
                                // a size that isn't a whole number isn't this
                            }
                        }
                    }
                    if (object.getTypeName().endsWith("PriorityQueue")) {
                        size = ((ArrayNode) object).getElements().size();
                    }
                    if (size != null) {
                        last.put(object.getId(), size);
                    }
                }
            }
            sizes.add(new HashMap<>(last));
        }
        return sizes;
    }

    // The lines of the file this step's line is in.
    private IndexUsage linesOf(Snapshot snapshot) {
        if (byFile.isEmpty() || snapshot.getFrames().isEmpty()) {
            return indexes;
        }
        IndexUsage file = byFile.get(snapshot.getFrames().get(0).getFile());
        return file != null ? file : indexes;
    }

    // Learns everything about the run that the pictures need, in two passes over the steps, and
    // returns the pictures as a SceneList, which builds each one when it's asked for.
    public SceneList build(Trace trace) {
        RenderContext context = new RenderContext();
        List<Map<Long, Integer>> heapSizes = learnHeaps(trace, context); // before the arrays' room is measured: a heap's tree needs room too
        List<Map<Long, List<RenderContext.Marker>>> markers = new ArrayList<>();
        for (int i = 0; i < trace.size(); i++) { // measure the whole run first
            Snapshot snapshot = trace.get(i);
            measure(snapshot, context);
            Map<Long, List<RenderContext.Marker>> stepMarkers = markersAt(snapshot, i > 0 ? trace.get(i - 1) : null);
            context.makeRoomFor(stepMarkers);
            markers.add(stepMarkers);
        }
        for (int i = 0; i < trace.size(); i++) {
            List<Structure> structures = detector.detect(trace.get(i));
            for (Structure structure : structures) { // where each list node goes, in step order
                if (structure instanceof LinkedListStructure) {
                    context.getOrder().learn((LinkedListStructure) structure, i);
                }
            }
            List<Structure> arrays = new ArrayList<>(structures); // arrays are on top: keep room for the most they need
            arrays.removeIf(structure -> !isArray(structure));
            context.reserveArrays(layOut(arrays, new Scene(), context));
        }
        return new SceneList(trace.size(), i -> {
            context.setHeapSizes(heapSizes.get(i));
            return buildStep(trace, i, context, markers.get(i));
        });
    }

    // The picture of step i. Everything it uses was learned for the whole run beforehand, so steps
    // can be built in any order and always come out the same.
    private Scene buildStep(Trace trace, int i, RenderContext context, Map<Long, List<RenderContext.Marker>> markers) {
        context.setMarkers(markers);
        context.getOrder().showStep(i);
        // The run is already recorded, so we know whether this step really leads to a crash.
        crashAhead = trace.getEndReason() == EndReason.EXCEPTION && i >= trace.size() - 2;
        Scene scene = build(trace.get(i), context);
        if (i > 0) {
            addMoves(scene, trace.get(i - 1));
        }
        return scene;
    }

    private Scene build(Snapshot snapshot, RenderContext context) {
        Scene scene = new Scene();
        List<Structure> structures = detector.detect(snapshot);
        layOut(structures, scene, context);
        addFocus(scene, snapshot);
        addLabels(scene, snapshot);
        return scene;
    }

    // Places the structures in rows from top to bottom. Returns how far down they reach.
    private double layOut(List<Structure> structures, Scene scene, RenderContext context) {
        double y = Style.MARGIN;
        double rowX = Style.SIDE_MARGIN;
        double rowHeight = 0;
        String row = null; // what the current row holds
        for (Structure structure : structures) {
            StructureRenderer renderer = StructureRenderer.forStructure(structure, context);
            String sharesRow = rowOf(structure);
            boolean rowIsFull = rowHeight > 0 && Objects.equals(sharesRow, row)
                    && rowX + widthOf(structure, context) > (structure instanceof GenericStructure ? Style.MAX_BOX_ROW_W : Style.MAX_ROW_W);
            if (rowHeight > 0 && (!Objects.equals(sharesRow, row) || rowIsFull)) {
                y += rowHeight + Style.STRUCTURE_GAP; // finish the row
                rowX = Style.SIDE_MARGIN;
                rowHeight = 0;
            }
            row = sharesRow;
            if (!isArray(structure)) {
                y = Math.max(y, context.getArraysBottom()); // below the room kept for arrays
            }
            Dimension size = renderer.render(scene, rowX, y);
            if (sharesRow == null) {
                y += size.height + Style.STRUCTURE_GAP;
            } else {
                rowX += size.width + Style.ROW_GAP;
                rowHeight = Math.max(rowHeight, size.height);
            }
        }
        return rowHeight > 0 ? y + rowHeight + Style.STRUCTURE_GAP : y;
    }

    private static boolean isArray(Structure structure) {
        return structure instanceof ArrayStructure || structure instanceof GridStructure;
    }

    // How wide a structure would be: a separate renderer draws it onto a scene that's thrown away.
    private static int widthOf(Structure structure, RenderContext context) {
        return StructureRenderer.forStructure(structure, context).render(new Scene(), 0, 0).width;
    }

    // Which row a structure shares with its neighbours, or null if it takes a row of its own.
    private static String rowOf(Structure structure) {
        if (structure instanceof GenericStructure) {
            return "boxes";
        }
        if (structure instanceof ArrayStructure) {
            return "arrays";
        }
        if (structure instanceof BinaryTreeStructure || structure instanceof MultiwayTreeStructure) {
            return ((LinkedStructure) structure).getNodes().values().iterator().next().getTypeName();
        }
        return null;
    }

    // Remembers the widest value of every array and node over the whole run.
    private void measure(Snapshot snapshot, RenderContext context) {
        for (HeapObject object : snapshot.getHeap().values()) {
            if (object instanceof ArrayNode) {
                context.growTo(object.getId(), ((ArrayNode) object).getElements().size());
                context.widenCells(object.getId(), StructureRenderer.widestValue(((ArrayNode) object).getElements()));
            } else if (object instanceof ObjectNode) {
                ObjectNode node = (ObjectNode) object;
                context.widenNodes(node.getTypeName(),
                        Math.ceil(Style.textWidth(LinkedStructure.labelOf(node), Style.VALUE_FONT) + 14));
                context.widenBox(node.getId(), Math.ceil(BoxRenderer.widthOf(node)));
            }
        }
    }

    // ---- index markers (i, j) and the cells the next line touches ----

    // The markers to draw at this step: for every array the running method can name, the
    // variables that index it (from IndexUsage) and their current values. An array reached through
    // an object (x.keys) only gets markers while the code around this step is using it, because
    // "j" means nothing on x.keys while the program is working on y.keys.
    private Map<Long, List<RenderContext.Marker>> markersAt(Snapshot snapshot, Snapshot previous) {
        Map<String, Value> visible = visibleVariables(snapshot);
        Set<String> inUse = new HashSet<>();
        for (IndexUsage.Access access : linesOf(snapshot).accessesOnLine(snapshot.getLine())) {
            inUse.add(access.array());
        }
        if (previous != null) {
            for (IndexUsage.Access access : linesOf(previous).accessesOnLine(previous.getLine())) {
                inUse.add(access.array());
            }
        }
        Map<Long, List<RenderContext.Marker>> markers = new HashMap<>();
        for (Map.Entry<String, Value> named : visible.entrySet()) {
            ArrayNode array = arrayOf(snapshot, named.getValue());
            if (array == null || (named.getKey().contains(".") && !inUse.contains(named.getKey()))) {
                continue;
            }
            List<RenderContext.Marker> list = markers.computeIfAbsent(array.getId(), k -> new ArrayList<>());
            addMarkers(list, visible, indexes.rowVariables(named.getKey()), RenderContext.Axis.ROW, array.getLength());
            if (array.getElementType().endsWith("[]")) {
                addMarkers(list, visible, indexes.columnVariables(named.getKey()), RenderContext.Axis.COLUMN,
                        columnsOf(array, snapshot));
            }
        }
        markers.values().removeIf(List::isEmpty);
        return markers;
    }

    private void addMarkers(List<RenderContext.Marker> list, Map<String, Value> visible, Set<String> variables,
                            RenderContext.Axis axis, int length) {
        for (String variable : variables) {
            Integer value = intOf(visible.get(variable));
            if (value == null || value < -1 || value > length) {
                continue; // not a number, or far outside the array
            }
            boolean already = false;
            for (RenderContext.Marker marker : list) {
                already |= marker.name().equals(variable) && marker.axis() == axis;
            }
            if (!already) {
                list.add(new RenderContext.Marker(variable, value, axis, value >= 0 && value < length));
            }
        }
    }

    // Outlines the cells the next line will read (dashed) or write (solid). An index past the end
    // gets a red ghost cell, but only if the run really crashes here: in "i < n && a[i] > 0" the
    // text mentions a[i], yet Java never reads it when i has reached n.
    // A box's key includes its array, so it glides along one array and fades between two arrays.
    private void addFocus(Scene scene, Snapshot snapshot) {
        Map<String, Value> visible = visibleVariables(snapshot);
        int count = 0;
        for (IndexUsage.Access access : linesOf(snapshot).accessesOnLine(snapshot.getLine())) {
            Cell cell = resolve(snapshot, visible, access);
            if (cell == null) {
                continue;
            }
            String key = "focus:" + cell.line().getId() + ":" + count++;
            SceneNode node = scene.getNode(cell.key());
            if (node != null) {
                SceneNode.Kind kind = access.write() ? SceneNode.Kind.FOCUS_WRITE : SceneNode.Kind.FOCUS_READ;
                scene.add(new SceneNode(key, kind, node.x(), node.y(), node.w(), node.h(), "", null));
                continue;
            }
            int length = cell.line().getLength();
            boolean pastEnd = cell.index() >= length && cell.index() < length + 4;
            SceneNode last = scene.getNode(new Cell(cell.line(), length - 1).key());
            if (crashAhead && pastEnd && last != null && length == cell.line().getElements().size()) {
                double x = last.x() + (cell.index() - length + 1) * last.w();
                scene.add(new SceneNode(key, SceneNode.Kind.FOCUS_OUT, x, last.y(), last.w(), last.h(),
                        "", String.valueOf(cell.index())));
            }
        }
    }

    // If the line that just ran copied one cell into another, remember it so the value can fly across.
    private void addMoves(Scene scene, Snapshot before) {
        String code = linesOf(before).lineText(before.getLine());
        if (!COPY.matcher(code).matches()) {
            return;
        }
        List<IndexUsage.Access> accesses = IndexUsage.accessesIn(code);
        if (accesses.size() != 2 || !accesses.get(0).write() || accesses.get(1).write()) {
            return;
        }
        Map<String, Value> visible = visibleVariables(before); // the indexes as they were when it ran
        Cell from = resolve(before, visible, accesses.get(1));
        Cell to = resolve(before, visible, accesses.get(0));
        SceneNode target = to == null ? null : scene.getNode(to.key());
        if (from != null && target != null && !from.key().equals(to.key()) && target.kind() == SceneNode.Kind.CELL) {
            scene.add(new Scene.Move(from.key(), to.key()));
        }
    }

    // Which cell an access like arr[j + 1] or grid[r][c] means right now, or null if it can't tell.
    private Cell resolve(Snapshot snapshot, Map<String, Value> visible, IndexUsage.Access access) {
        ArrayNode array = arrayOf(snapshot, visible.get(access.array()));
        Integer row = valueOf(access.row(), visible);
        if (array == null || row == null) {
            return null;
        }
        if (access.column() == null) {
            return new Cell(array, row);
        }
        if (row < 0 || row >= array.getElements().size()) {
            return null;
        }
        ArrayNode rowArray = arrayOf(snapshot, array.getElements().get(row));
        Integer column = valueOf(access.column(), visible);
        return rowArray == null || column == null ? null : new Cell(rowArray, column);
    }

    // The variables the running method can use by name: its own locals first, then the fields of
    // "this", then static fields. Fields reached through one of those objects are named too,
    // like "x.keys" for the keys array of the object x.
    private static Map<String, Value> visibleVariables(Snapshot snapshot) {
        Map<String, Value> visible = new LinkedHashMap<>();
        if (!snapshot.getFrames().isEmpty()) {
            Map<String, Value> locals = snapshot.getFrames().get(0).getLocals();
            visible.putAll(locals);
            Value self = locals.get("this");
            if (self instanceof ReferenceValue && snapshot.getObject(((ReferenceValue) self).id()) instanceof ObjectNode) {
                ObjectNode object = (ObjectNode) snapshot.getObject(((ReferenceValue) self).id());
                for (Map.Entry<String, Value> field : object.getFields().entrySet()) {
                    visible.putIfAbsent(field.getKey(), field.getValue());
                }
            }
        }
        for (Map.Entry<String, Value> field : snapshot.getStatics().entrySet()) {
            visible.putIfAbsent(field.getKey().substring(field.getKey().indexOf('.') + 1), field.getValue());
        }
        for (Map.Entry<String, Value> named : new ArrayList<>(visible.entrySet())) {
            if (named.getValue() instanceof ReferenceValue
                    && snapshot.getObject(((ReferenceValue) named.getValue()).id()) instanceof ObjectNode) {
                ObjectNode object = (ObjectNode) snapshot.getObject(((ReferenceValue) named.getValue()).id());
                for (Map.Entry<String, Value> field : object.getFields().entrySet()) {
                    visible.putIfAbsent(named.getKey() + "." + field.getKey(), field.getValue());
                }
            }
        }
        return visible;
    }

    private static ArrayNode arrayOf(Snapshot snapshot, Value value) {
        if (value instanceof ReferenceValue && snapshot.getObject(((ReferenceValue) value).id()) instanceof ArrayNode) {
            return (ArrayNode) snapshot.getObject(((ReferenceValue) value).id());
        }
        return null;
    }

    private static int columnsOf(ArrayNode grid, Snapshot snapshot) {
        int columns = 0;
        for (Value row : grid.getElements()) {
            ArrayNode rowArray = arrayOf(snapshot, row);
            if (rowArray != null) {
                columns = Math.max(columns, rowArray.getLength());
            }
        }
        return columns;
    }

    private static Integer valueOf(IndexUsage.Index index, Map<String, Value> visible) {
        if (index.variable() == null) {
            return index.offset();
        }
        Integer value = intOf(visible.get(index.variable()));
        if (value == null) {
            return null;
        }
        if (index.other() == null) {
            return value + index.offset();
        }
        Integer other = intOf(visible.get(index.other())); // e.g. the T in "j + T"
        return other == null ? null : value + index.offset() + index.otherSign() * other;
    }

    private static Integer intOf(Value value) {
        if (!(value instanceof PrimitiveValue)) {
            return null;
        }
        try {
            return Integer.parseInt(value.display());
        } catch (NumberFormatException e) {
            return null; // not a whole number (a boolean, a char, a String…)
        }
    }

    // ---- variable labels ----

    // One label per variable name. The running method's variables win (in recursion, you see the
    // newest "n"), then the callers', then static fields.
    private void addLabels(Scene scene, Snapshot snapshot) {
        Set<String> named = new HashSet<>();
        Map<String, Integer> labelsOn = new HashMap<>(); // target key → labels already stacked on it
        for (FrameSnapshot frame : snapshot.getFrames()) {
            for (Map.Entry<String, Value> local : frame.getLocals().entrySet()) {
                addLabel(scene, local.getKey(), local.getValue(), named, labelsOn);
            }
        }
        for (Map.Entry<String, Value> field : snapshot.getStatics().entrySet()) {
            String name = field.getKey().substring(field.getKey().indexOf('.') + 1);
            addLabel(scene, name, field.getValue(), named, labelsOn);
        }
        addFieldLabels(scene, snapshot, labelsOn);
    }

    // An array that only an object's field points at is named after that field, because arrays
    // are drawn on top, away from the object that holds them.
    private void addFieldLabels(Scene scene, Snapshot snapshot, Map<String, Integer> labelsOn) {
        for (HeapObject object : snapshot.getHeap().values()) {
            if (!(object instanceof ObjectNode)) {
                continue;
            }
            for (Map.Entry<String, Value> field : ((ObjectNode) object).getFields().entrySet()) {
                if (field.getValue() instanceof ReferenceValue) {
                    String target = "#" + ((ReferenceValue) field.getValue()).id();
                    SceneNode node = scene.getNode(target);
                    if (node != null && node.kind() == SceneNode.Kind.FRAME && node.caption() != null
                            && !labelsOn.containsKey(target)) {
                        placeLabel(scene, "field:" + target, field.getKey(), target, node, labelsOn);
                    }
                }
            }
        }
    }

    private void addLabel(Scene scene, String name, Value value, Set<String> named, Map<String, Integer> labelsOn) {
        if (!named.add(name) || !(value instanceof ReferenceValue)) {
            return;
        }
        String target = "#" + ((ReferenceValue) value).id();
        SceneNode node = scene.getNode(target);
        if (node == null) {
            return;
        }
        placeLabel(scene, "var:" + name, name, target, node, labelsOn);
    }

    private void placeLabel(Scene scene, String key, String name, String target, SceneNode node,
                            Map<String, Integer> labelsOn) {
        int level = labelsOn.merge(target, 1, Integer::sum) - 1; // 0 = closest to the object
        double w = Style.textWidth(name, Style.LABEL_FONT) + 6;
        double x = node.kind() == SceneNode.Kind.FRAME ? node.x() : node.centerX() - w / 2;
        double y = node.y() - 6 - (level + 1) * Style.LABEL_HEIGHT;
        scene.add(new SceneNode(key, SceneNode.Kind.LABEL, x, y, w, Style.LABEL_HEIGHT, name, null));
        if (level == 0) {
            scene.add(new SceneEdge(key + "→", key, target, SceneEdge.Kind.POINTER));
        }
    }
}
