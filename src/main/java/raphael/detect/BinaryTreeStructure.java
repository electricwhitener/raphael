package raphael.detect;

import java.util.Map;
import raphael.model.ObjectNode;

// One binary tree: a root, and two links (usually left and right) on every node.
// OOP: inheritance (LinkedStructure → BinaryTreeStructure: multilevel) and overriding.
public class BinaryTreeStructure extends LinkedStructure {

    private final long root;
    private final String leftField;
    private final String rightField;

    public BinaryTreeStructure(Map<Long, ObjectNode> nodes, long root, String leftField, String rightField) {
        super(nodes);
        this.root = root;
        this.leftField = leftField;
        this.rightField = rightField;
    }

    public long getRoot() {
        return root;
    }

    public long getLeft(long id) {
        return follow(id, leftField);
    }

    public long getRight(long id) {
        return follow(id, rightField);
    }

    @Override
    public String getKind() {
        return "binary tree";
    }
}
