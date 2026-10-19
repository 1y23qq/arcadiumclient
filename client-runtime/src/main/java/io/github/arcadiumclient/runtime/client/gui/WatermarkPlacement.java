// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.gui;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class WatermarkPlacement {
    private static final int MINIMUM_HEIGHT = 22;
    public record Rect(int x, int y, int width, int height) {
        public long right() { return (long) x + width; }
        public long bottom() { return (long) y + height; }
        public boolean overlaps(Rect other) { return overlap(other) > 0; }
        private long overlap(Rect other) {
            return Math.max(0L, Math.min(right(), other.right()) - Math.max(x, other.x))
                * Math.max(0L, Math.min(bottom(), other.bottom()) - Math.max(y, other.y));
        }
    }

    private WatermarkPlacement() { }
    private record Placement(Rect bounds, boolean clear) { }

    public static Rect pixels(Rect rect, int logicalWidth, int logicalHeight, int width, int height) {
        if (logicalWidth <= 0 || logicalHeight <= 0 || width <= 0 || height <= 0) throw new IllegalArgumentException("Invalid watermark viewport");
        int x = clamp((long) Math.floor((double) rect.x * width / logicalWidth), width);
        int y = clamp((long) Math.floor((double) rect.y * height / logicalHeight), height);
        int right = clamp((long) Math.ceil((double) rect.right() * width / logicalWidth), width);
        int bottom = clamp((long) Math.ceil((double) rect.bottom() * height / logicalHeight), height);
        return new Rect(x, y, Math.max(0, right - x), Math.max(0, bottom - y));
    }

    public static Rect choose(int screenWidth, int screenHeight, int imageWidth, int imageHeight, int padding, List<Rect> obstacles) {
        if (screenWidth <= 0 || screenHeight <= 0 || imageWidth <= 0 || imageHeight <= 0 || padding < 0) throw new IllegalArgumentException("Invalid watermark bounds");
        double fit = Math.min(1.0, Math.min((double) screenWidth / imageWidth, (double) screenHeight / imageHeight));
        int width = Math.max(1, (int) Math.floor(imageWidth * fit)), height = Math.max(1, (int) Math.floor(imageHeight * fit));
        int pad = Math.min(padding, Math.min((screenWidth - width) / 2, (screenHeight - height) / 2));
        Placement full = place(screenWidth, screenHeight, width, height, pad, obstacles);
        if (full.clear) return full.bounds;
        Rect smaller = null;
        int low = Math.min(height, MINIMUM_HEIGHT), high = height - 1;
        while (low <= high) {
            int candidateHeight = low + (high - low) / 2;
            int candidateWidth = Math.max(1, (int) ((long) width * candidateHeight / height));
            Placement candidate = place(screenWidth, screenHeight, candidateWidth, candidateHeight, pad, obstacles);
            if (candidate.clear) {
                smaller = candidate.bounds;
                low = candidateHeight + 1;
            } else high = candidateHeight - 1;
        }
        return smaller == null ? full.bounds : smaller;
    }

    private static Placement place(int screenWidth, int screenHeight, int width, int height, int pad, List<Rect> obstacles) {
        int left = pad, right = screenWidth - width - pad, top = pad, bottom = screenHeight - height - pad;
        int centerX = (screenWidth - width) / 2, centerY = (screenHeight - height) / 2, gap = Math.max(1, pad / 3);
        Set<Rect> candidates = new LinkedHashSet<>();
        Set<Integer> xs = new LinkedHashSet<>(List.of(right, left, centerX));
        Set<Integer> ys = new LinkedHashSet<>(List.of(bottom, top, centerY));
        for (int[] corner : new int[][] {{right, bottom}, {left, bottom}, {right, top}, {left, top}, {centerX, bottom}, {centerX, top}, {left, centerY}, {right, centerY}}) {
            candidates.add(new Rect(corner[0], corner[1], width, height));
        }
        for (Rect obstacle : obstacles) {
            if (obstacle.width <= 0 || obstacle.height <= 0) continue;
            for (long x : new long[] {(long) obstacle.x - width - gap, obstacle.right() + gap}) {
                xs.add(clamp(x, screenWidth - width));
                candidates.add(new Rect(clamp(x, screenWidth - width), bottom, width, height));
                candidates.add(new Rect(clamp(x, screenWidth - width), top, width, height));
            }
        }
        for (Rect obstacle : obstacles) {
            if (obstacle.width <= 0 || obstacle.height <= 0) continue;
            for (long y : new long[] {(long) obstacle.y - height - gap, obstacle.bottom() + gap}) {
                ys.add(clamp(y, screenHeight - height));
                for (int x : new int[] {right, left, centerX}) candidates.add(new Rect(x, clamp(y, screenHeight - height), width, height));
            }
        }
        for (int x : xs) for (int y : ys) candidates.add(new Rect(x, y, width, height));
        Rect best = candidates.iterator().next(); long bestOverlap = Long.MAX_VALUE;
        for (Rect candidate : candidates) {
            boolean clear = true; long overlap = 0;
            for (Rect obstacle : obstacles) {
                if (obstacle.width <= 0 || obstacle.height <= 0) continue;
                clear &= candidate.right() + gap <= obstacle.x || obstacle.right() + gap <= candidate.x
                    || candidate.bottom() + gap <= obstacle.y || obstacle.bottom() + gap <= candidate.y;
                overlap += candidate.overlap(obstacle);
            }
            if (clear) return new Placement(candidate, true);
            if (overlap < bestOverlap) { best = candidate; bestOverlap = overlap; }
        }
        return new Placement(best, false);
    }

    private static int clamp(long value, int maximum) { return (int) Math.max(0, Math.min(maximum, value)); }
}
