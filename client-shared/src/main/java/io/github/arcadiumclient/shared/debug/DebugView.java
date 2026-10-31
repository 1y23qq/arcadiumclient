// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.shared.debug;

import java.util.*;

public final class DebugView {
    public record Span(String text, int color) { }
    public record Line(List<Span> spans) {
        public static Line text(String text, int color) { return new Line(List.of(new Span(text, color))); }
        public String text() { StringBuilder out = new StringBuilder(); spans.forEach(s -> out.append(s.text)); return out.toString(); }
    }
    public record Frame(List<Line> left, List<Line> right) { }
    public interface Surface {
        float width(String text);
        void rect(float x1, float y1, float x2, float y2, int color);
        void text(String text, float x, float y, int color, boolean shadow);
        void begin(float scale);
        void end();
    }
    private double visibility;
    private long previous;
    public double animate(boolean visible, boolean enabled, double speed, long now) {
        if (!enabled) visibility = visible ? 1 : 0;
        else {
            double seconds = previous == 0 ? 0 : Math.max(0, Math.min(0.1, (now - previous) / 1_000_000_000.0));
            double step = seconds * Math.max(0.1, Math.min(5, speed)) * 6;
            visibility = visible ? Math.min(1, visibility + step) : Math.max(0, visibility - step);
        }
        previous = now; return visibility;
    }
    public void reset() { visibility = 0; previous = 0; }
    public boolean isVisible() { return visibility > 0; }
    public void draw(Surface surface, Frame frame, int width, float scale, int background, boolean shadow, double visibility) {
        if (visibility <= 0 || frame == null) return;
        scale = Math.max(0.25f, Math.min(4, scale));
        surface.begin(scale);
        try {
            column(surface, frame.left, false, width / scale, background, shadow, visibility);
            column(surface, frame.right, true, width / scale, background, shadow, visibility);
        } finally { surface.end(); }
    }
    private static void column(Surface surface, List<Line> lines, boolean right, float width, int background, boolean shadow, double visibility) {
        float maximum = 0;
        for (Line line : lines) maximum = Math.max(maximum, measure(surface, line));
        float slide = (float) ((1 - visibility) * (maximum + 8));
        for (int row = 0; row < lines.size(); row++) {
            Line line = lines.get(row); float size = measure(surface, line); if (size <= 0) continue;
            float x = right ? width - 2 - size + slide : 2 - slide, y = 2 + row * 9;
            if ((background >>> 24) != 0) surface.rect(x - 1, y - 1, x + size + 1, y + 8, background);
            for (Span span : line.spans) { surface.text(span.text, x, y, span.color, shadow); x += surface.width(span.text); }
        }
    }
    private static float measure(Surface surface, Line line) { float width = 0; for (Span span : line.spans) width += surface.width(span.text); return width; }
    public static Line label(String label, String value, int nameColor, int valueColor) {
        return new Line(List.of(new Span(label.isEmpty() ? "" : label + ": ", nameColor), new Span(value, valueColor)));
    }
    public static Frame columns(DebugLayout layout, Map<String, List<Line>> values, boolean spacing) {
        return new Frame(column(layout.left, values, spacing), column(layout.right, values, spacing));
    }
    private static List<Line> column(List<DebugLayout.Module> modules, Map<String, List<Line>> values, boolean spacing) {
        List<Line> lines = new ArrayList<>();
        for (DebugLayout.Module module : modules) {
            if (!module.enabled) continue;
            if (module.kind.equals("empty")) { if(module.lines.getOrDefault("nothing",true)) for (int i = 0; i < module.emptyLines; i++) lines.add(Line.text("", 0)); continue; }
            List<Line> block = values.getOrDefault(module.key, List.of());
            if (block.isEmpty()) continue;
            if (spacing && !lines.isEmpty()) lines.add(Line.text("", 0));
            lines.addAll(block);
        }
        return lines;
    }
}
