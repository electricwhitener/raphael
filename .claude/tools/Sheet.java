import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
import raphael.io.ExampleCatalog;
import raphael.render.*;
public class Sheet {
    public static void main(String[] a) throws Exception {
        List<ExampleCatalog.Example> all = ExampleCatalog.all();
        int from = Integer.parseInt(a[0]), to = Math.min(all.size(), Integer.parseInt(a[1]));
        int TW = 460, TH = 300, cols = 4;
        int n = (to - from) * 2;
        BufferedImage sheet = new BufferedImage(cols * TW, ((n + cols - 1) / cols) * TH, BufferedImage.TYPE_INT_RGB);
        Graphics2D sg = sheet.createGraphics();
        sg.setColor(Color.WHITE); sg.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        int t = 0;
        for (int k = from; k < to; k++) {
            ExampleCatalog.Example e = all.get(k);
            String src = ExampleCatalog.source(e);
            var tr = new raphael.trace.JdiTracer().trace(new raphael.compile.SourceCompiler().compile(src), "");
            SceneList scenes = new SceneBuilder(src).build(tr);
            Dimension run = scenes.getRunSize();
            for (int step : new int[] {tr.size() * 3 / 5, tr.size() - 1}) {
                int x = (t % cols) * TW, y = (t / cols) * TH;
                Graphics2D g = (Graphics2D) sg.create(x, y, TW, TH);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(Color.LIGHT_GRAY); g.drawRect(0, 0, TW - 1, TH - 1);
                g.setColor(Color.BLACK); g.setFont(new Font("SansSerif", Font.BOLD, 12));
                g.drawString(e.path() + " @" + (step + 1) + "/" + tr.size() + "  run " + run.width + "x" + run.height, 6, 14);
                double s = Math.min(1.0, Math.min((TW - 10.0) / Math.max(1, run.width), (TH - 26.0) / Math.max(1, run.height)));
                g.translate(5, 20); g.scale(s, s);
                new ScenePainter().paint(g, scenes.get(step));
                g.dispose();
                t++;
            }
        }
        ImageIO.write(sheet, "png", new File(a[2]));
        System.exit(0);
    }
}
