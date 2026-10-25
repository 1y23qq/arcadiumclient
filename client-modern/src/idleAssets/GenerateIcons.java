// SPDX-License-Identifier: MIT
// Feather geometry: Copyright (c) 2013-2022 Cole Bemis.
// Adaptations: Copyright (c) 2026 Arcadium contributors.
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

public final class GenerateIcons {
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]).toAbsolutePath().normalize();
        for (int level = 0; level <= 10; level++) {
            write(output, "battery/icon/charging_" + level + ".png", battery(16, level, true));
            write(output, "battery/icon/draining_" + level + ".png", battery(16, level, false));
        }
        write(output, "battery/toast/charging.png", battery(16, 10, true));
        write(output, "battery/toast/draining.png", battery(16, 5, false));
        var warning = image(16, 16);
        var g = canvas(warning); g.scale(16 / 24d, 16 / 24d); g.setColor(new Color(0xF5C66D)); g.setStroke(stroke(2));
        g.draw(new Ellipse2D.Double(2, 2, 20, 20)); g.draw(new Line2D.Double(12, 8, 12, 12)); g.draw(new Line2D.Double(12, 16, 12.01, 16)); g.dispose();
        write(output, "battery/toast/reminder.png", warning);
        var background = image(160, 32); g = canvas(background);
        g.setColor(new Color(0xF0171D25, true)); g.fill(new RoundRectangle2D.Double(.5, .5, 159, 31, 6, 6));
        g.setColor(new Color(0xFF3B4553, true)); g.setStroke(stroke(1)); g.draw(new RoundRectangle2D.Double(.5, .5, 159, 31, 6, 6)); g.dispose();
        write(output, "battery/toast/background.png", background);
        var icon = image(128, 128); g = canvas(icon); g.setColor(new Color(0x171D25)); g.fill(new RoundRectangle2D.Double(0, 0, 128, 128, 20, 20));
        g.drawImage(battery(96, 8, true), 16, 16, null); g.dispose();
        write(output, "battery/toast/background_icon.png", icon);
        write(output, "icon.png", icon);
    }
    private static BufferedImage battery(int size, int level, boolean charging) {
        var image = image(size, size); var g = canvas(image); g.scale(size / 24d, size / 24d);
        g.setColor(new Color(level <= 2 ? 0xFF5555 : 0x36F1B0));
        if (level > 0) g.fill(new Rectangle2D.Double(3, 8, 14 * level / 10d, 8));
        g.setStroke(stroke(2)); g.setColor(new Color(0xE5EAF1));
        g.draw(new RoundRectangle2D.Double(1, 6, 18, 12, 4, 4)); g.draw(new Line2D.Double(23, 13, 23, 11));
        if (charging) {
            var bolt = new Path2D.Double();
            bolt.moveTo(10.5, 7); bolt.lineTo(6, 13); bolt.lineTo(10, 13); bolt.lineTo(9.5, 17); bolt.lineTo(14, 11); bolt.lineTo(10, 11); bolt.closePath();
            g.setStroke(stroke(2.8f)); g.setColor(new Color(0x171D25)); g.draw(bolt); g.setColor(Color.WHITE); g.fill(bolt);
        }
        g.dispose(); return image;
    }
    private static BufferedImage image(int width, int height) { return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB); }
    private static BasicStroke stroke(float width) { return new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND); }
    private static Graphics2D canvas(BufferedImage image) {
        var g = image.createGraphics(); g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY); return g;
    }
    private static void write(Path root, String name, BufferedImage image) throws Exception {
        Path file = root.resolve(name).normalize(); if (!file.startsWith(root)) throw new IllegalArgumentException(name);
        Files.createDirectories(file.getParent()); ImageIO.write(image, "PNG", file.toFile());
    }
}
