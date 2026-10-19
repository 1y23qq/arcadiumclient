// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.gui;

import java.util.List;

public final class ServerListDrag<T> {
    private T pressed;
    private int source = -1;
    private int startX;
    private int startY;
    private boolean dragging;
    private T lastClick;
    private long lastClickTime;

    public enum Kind { NONE, CLICK, DOUBLE_CLICK, DROP }
    public record Result(Kind kind, int source, int target) {}

    public boolean begin(T entry, int index, int x, int y, int button) {
        if (button != 0 || entry == null || index < 0) return false;
        pressed = entry;
        source = index;
        startX = x;
        startY = y;
        dragging = false;
        return true;
    }

    public void update(int x, int y) {
        if (pressed == null || dragging) return;
        long dx = (long) x - startX;
        long dy = (long) y - startY;
        if (dx * dx + dy * dy >= 25) {
            dragging = true;
            lastClick = null;
        }
    }

    public Result release(int x, int y, int target, boolean inside, long now) {
        if (pressed == null) return new Result(Kind.NONE, -1, -1);
        update(x, y);
        Kind kind = Kind.NONE;
        if (inside && target >= 0) {
            if (dragging) {
                kind = Kind.DROP;
                lastClick = null;
            } else if (target == source) {
                long elapsed = now - lastClickTime;
                kind = lastClick == pressed && elapsed >= 0 && elapsed < 250 ? Kind.DOUBLE_CLICK : Kind.CLICK;
                lastClick = kind == Kind.CLICK ? pressed : null;
                lastClickTime = now;
            }
        }
        if (kind == Kind.NONE) lastClick = null;
        Result result = new Result(kind, source, target);
        pressed = null;
        source = -1;
        dragging = false;
        return result;
    }

    public boolean active() { return pressed != null; }
    public boolean dragging() { return dragging; }
    public T pressed() { return pressed; }
    public int source() { return source; }

    public void cancel() {
        pressed = null;
        source = -1;
        dragging = false;
        lastClick = null;
    }

    public static int targetIndex(int mouseY, int top, int scroll, int rowHeight, int count) {
        if (count <= 0 || rowHeight <= 0) return -1;
        int index = Math.floorDiv(mouseY - top + scroll, rowHeight);
        return Math.max(0, Math.min(count - 1, index));
    }

    public static double scrollDelta(int mouseY, int top, int bottom, double elapsedMs) {
        int band = Math.min(24, Math.max(1, (bottom - top) / 4));
        double speed = 0;
        if (mouseY < top + band) speed = -Math.min(1, (top + band - mouseY) / (double) band);
        else if (mouseY > bottom - band) speed = Math.min(1, (mouseY - bottom + band) / (double) band);
        return speed * 0.36 * Math.max(0, Math.min(50, elapsedMs));
    }

    public static <T> boolean reorder(List<T> entries, int from, int to) {
        if (from < 0 || to < 0 || from >= entries.size() || to >= entries.size() || from == to) return false;
        T entry = entries.remove(from);
        entries.add(to, entry);
        return true;
    }
}
