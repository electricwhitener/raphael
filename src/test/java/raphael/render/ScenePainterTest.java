package raphael.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class ScenePainterTest {

    private static Color centreAfterPainting(SceneNode from) {
        Scene scene = new Scene();
        scene.add(from);
        scene.add(new SceneNode("to", SceneNode.Kind.BOX, 100, 10, 44, 34, "", null));
        scene.add(new SceneEdge("arrow", "from", "to", SceneEdge.Kind.ARROW));
        BufferedImage image = new BufferedImage(200, 60, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Style.BACKGROUND);
        g.fillRect(0, 0, 200, 60);
        new ScenePainter().paint(g, scene);
        g.dispose();
        return new Color(image.getRGB((int) from.centerX(), (int) from.centerY()));
    }

    private static Color whereTheArrowCrossesABox(String slotCaption) {
        Scene scene = new Scene();
        scene.add(new SceneNode("from", SceneNode.Kind.SLOT, 10, 10, 18, 34, "", slotCaption));
        scene.add(new SceneNode("in the way", SceneNode.Kind.OBJECT, 0, 80, 100, 40, "", null));
        scene.add(new SceneNode("to", SceneNode.Kind.BOX, 0, 200, 38, 34, "", null));
        scene.add(new SceneEdge("arrow", "from", "to", SceneEdge.Kind.ARROW));
        BufferedImage image = new BufferedImage(200, 260, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        new ScenePainter().paint(g, scene);
        g.dispose();
        return new Color(image.getRGB(19, 112)); // below the title bar, in the box's body
    }

    @Test
    void anArrayCellsArrowShowsFaintlyAcrossABoxInItsWay() {
        assertNotEquals(Style.FILL, whereTheArrowCrossesABox("0"));
        assertEquals(Style.FILL, whereTheArrowCrossesABox(null), "other arrows still pass behind");
    }

    @Test
    void aPointerSlotShowsItsDotOnTopOfTheSlot() {
        assertEquals(Style.INK_SOFT, centreAfterPainting(
                new SceneNode("from", SceneNode.Kind.SLOT, 10, 10, 18, 34, "", null)));
    }

    @Test
    void anArrowFromATreeNodeDoesNotCoverTheNodesValue() {
        assertEquals(Style.FILL, centreAfterPainting(
                new SceneNode("from", SceneNode.Kind.CIRCLE, 10, 10, 40, 40, "", null)));
    }
}
