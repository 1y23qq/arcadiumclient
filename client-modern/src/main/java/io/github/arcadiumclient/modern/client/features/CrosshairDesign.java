// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.modern.client.features;

import java.util.ArrayList;
import java.util.List;

public final class CrosshairDesign {
    public String name = "Custom";
    public int mode;
    public int shape;
    public int length = 6;
    public int thickness = 1;
    public int gap = 4;
    public int color = 0xFFFFFFFF;
    public int outlineColor = 0xFF161616;
    public boolean outline = true;
    public int outlineWidth = 1;
    public boolean dot;
    public int dotSize = 1;
    public float opacity = 1f;
    public float scale = 1f;
    public int canvasSize = 32;
    public int[] pixels = new int[32 * 32];

    public record Quad(float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4, int color) {}
    public record Mesh(List<Quad> quads) {
        public boolean isEmpty() { return quads.isEmpty(); }
    }

    public CrosshairDesign copy() {
        CrosshairDesign result = new CrosshairDesign();
        result.name = name;
        result.mode = mode;
        result.shape = shape;
        result.length = length;
        result.thickness = thickness;
        result.gap = gap;
        result.color = color;
        result.outlineColor = outlineColor;
        result.outline = outline;
        result.outlineWidth = outlineWidth;
        result.dot = dot;
        result.dotSize = dotSize;
        result.opacity = opacity;
        result.scale = scale;
        result.canvasSize = canvasSize;
        result.pixels = pixels == null ? new int[32 * 32] : pixels.clone();
        return result;
    }

    public void normalize() {
        name = name == null || name.isBlank() ? "Custom" : name.trim().substring(0, Math.min(32, name.trim().length()));
        mode = mode == 1 ? 1 : 0;
        shape = clamp(shape, 0, 4);
        length = clamp(length, 1, 20);
        thickness = clamp(thickness, 1, 8);
        gap = clamp(gap, 0, 20);
        outlineWidth = clamp(outlineWidth, 1, 4);
        dotSize = clamp(dotSize, 1, 9);
        opacity = Float.isFinite(opacity) ? Math.max(0, Math.min(1, opacity)) : 1;
        scale = Float.isFinite(scale) ? Math.max(0.25f, Math.min(4, scale)) : 1;
        canvasSize = canvasSize == 16 || canvasSize == 64 ? canvasSize : 32;
        if (pixels == null || pixels.length != canvasSize * canvasSize) pixels = new int[canvasSize * canvasSize];
    }

    public Mesh mesh() {
        normalize();
        ArrayList<Quad> quads = new ArrayList<>();
        if (mode == 1) canvas(quads);
        else {
            if (outline) shape(quads, outlineWidth, outlineColor);
            shape(quads, 0, color);
        }
        return new Mesh(List.copyOf(quads));
    }

    private void shape(List<Quad> quads, float expansion, int argb) {
        float half = thickness / 2f;
        float space = shape == 1 ? 0 : gap / 2f;
        if (shape == 2) {
            ring(quads, length, half + expansion, argb);
        } else if (shape != 3) {
            rect(quads, -space - length - expansion, -half - expansion, -space + expansion, half + expansion, argb);
            rect(quads, space - expansion, -half - expansion, space + length + expansion, half + expansion, argb);
            rect(quads, -half - expansion, space - expansion, half + expansion, space + length + expansion, argb);
            if (shape != 4) rect(quads, -half - expansion, -space - length - expansion, half + expansion, -space + expansion, argb);
        }
        if (dot || shape == 3) {
            float size = dotSize / 2f + expansion;
            rect(quads, -size, -size, size, size, argb);
        }
    }

    private static void ring(List<Quad> quads, float radius, float half, int color) {
        if ((color >>> 24) == 0) return;
        float inner = Math.max(0, radius - half), outer = radius + half;
        for (int i = 0; i < 64; i++) {
            double a = i * Math.PI / 32, b = (i + 1) * Math.PI / 32;
            quads.add(new Quad((float) Math.cos(a) * inner, (float) Math.sin(a) * inner,
                    (float) Math.cos(a) * outer, (float) Math.sin(a) * outer,
                    (float) Math.cos(b) * outer, (float) Math.sin(b) * outer,
                    (float) Math.cos(b) * inner, (float) Math.sin(b) * inner, color));
        }
    }

    private void canvas(List<Quad> quads) {
        if (outline && (outlineColor >>> 24) != 0) {
            int border = outlineWidth, dimension = canvasSize + border * 2;
            int[] mask = new int[dimension * dimension];
            for (int y = 0; y < canvasSize; y++) for (int x = 0; x < canvasSize; x++) {
                if ((pixels[y * canvasSize + x] >>> 24) == 0) continue;
                for (int dy = -border; dy <= border; dy++) for (int dx = -border; dx <= border; dx++)
                    mask[(y + border + dy) * dimension + x + border + dx] = outlineColor;
            }
            runs(quads, mask, dimension, -dimension / 2f);
        }
        runs(quads, pixels, canvasSize, -canvasSize / 2f);
    }

    private static void runs(List<Quad> quads, int[] pixels, int dimension, float origin) {
        for (int y = 0; y < dimension; y++) {
            int x = 0;
            while (x < dimension) {
                int start = x, color = pixels[y * dimension + x++];
                if ((color >>> 24) == 0) continue;
                while (x < dimension && pixels[y * dimension + x] == color) x++;
                rect(quads, origin + start, origin + y, origin + x, origin + y + 1, color);
            }
        }
    }

    private static void rect(List<Quad> quads, float x1, float y1, float x2, float y2, int color) {
        if ((color >>> 24) != 0) quads.add(new Quad(x1, y1, x2, y1, x2, y2, x1, y2, color));
    }

    public void resizeCanvas(int dimension) {
        if (dimension != 16 && dimension != 32 && dimension != 64) return;
        normalize();
        int[] next = new int[dimension * dimension];
        int offset = (dimension - canvasSize) / 2;
        for (int y = 0; y < canvasSize; y++) for (int x = 0; x < canvasSize; x++) {
            int nx = x + offset, ny = y + offset;
            if (nx >= 0 && ny >= 0 && nx < dimension && ny < dimension) next[ny * dimension + nx] = pixels[y * canvasSize + x];
        }
        canvasSize = dimension;
        pixels = next;
    }

    public void paint(int x, int y, int color, boolean mirrorX, boolean mirrorY) {
        if (x < 0 || y < 0 || x >= canvasSize || y >= canvasSize) return;
        pixels[y * canvasSize + x] = color;
        if (mirrorX) pixels[y * canvasSize + canvasSize - x - 1] = color;
        if (mirrorY) pixels[(canvasSize - y - 1) * canvasSize + x] = color;
        if (mirrorX && mirrorY) pixels[(canvasSize - y - 1) * canvasSize + canvasSize - x - 1] = color;
    }

    public static List<CrosshairDesign> presets() {
        ArrayList<CrosshairDesign> result = new ArrayList<>();
        String[] names = {"Classic", "Compact", "Dot", "Ring", "T", "Plus"};
        for (int i = 0; i < names.length; i++) {
            CrosshairDesign d = new CrosshairDesign();
            d.name = names[i];
            switch (i) {
                case 1 -> { d.length = 3; d.gap = 3; d.color = 0xFF79EDB3; }
                case 2 -> { d.shape = 3; d.dotSize = 3; }
                case 3 -> { d.shape = 2; d.length = 5; d.dot = true; d.color = 0xFF6BD9EA; }
                case 4 -> { d.shape = 4; d.length = 5; d.color = 0xFFEBCB7B; }
                case 5 -> { d.shape = 1; d.length = 5; d.outline = false; }
            }
            result.add(d);
        }
        return List.copyOf(result);
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
