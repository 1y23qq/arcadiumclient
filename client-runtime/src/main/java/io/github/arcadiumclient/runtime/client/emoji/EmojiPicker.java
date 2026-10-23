// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.emoji;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiElement;
import org.polyfrost.oneconfig.internal.emoji.EmojiText;

public final class EmojiPicker {
    private boolean open;
    public void toggle() { open = !open; }
    public boolean close() { boolean previous = open; open = false; return previous; }

    private int cell(int height) { return Math.max(12, Math.min(20, (height - 66) / 8)); }
    private int left(int width, int height) { return Math.max(2, width - cell(height) * 8 - 12); }
    private int top(int height) { return Math.max(2, height - 43 - cell(height) * 8 - 22); }
    private boolean overButton(int x, int y, int width, int height) {
        return x >= width - 44 && x < width - 3 && y >= height - 39 && y < height - 24;
    }

    public void render(int width, int height, int mouseX, int mouseY) {
        boolean hover = overButton(mouseX, mouseY, width, height);
        GuiElement.fill(width - 44, height - 39, width - 3, height - 24, hover || open ? 0xEE354450 : 0xD51A2229);
        Minecraft.getInstance().textRenderer.draw("Emoji", width - 39, height - 35, 0xFFEEF3F8);
        if (!open) return;
        int size = cell(height), left = left(width, height), top = top(height);
        GuiElement.fill(left - 4, top - 4, left + size * 8 + 4, top + size * 8 + 20, 0xF2182027);
        String label = "Ctrl+Space";
        for (int i = 0; i < EmojiText.entries().size(); i++) {
            int x = left + i % 8 * size, y = top + i / 8 * size;
            if (mouseX >= x && mouseX < x + size && mouseY >= y && mouseY < y + size) {
                GuiElement.fill(x, y, x + size, y + size, 0xFF354450);
                label = EmojiText.entries().get(i).name();
            }
            EmojiPainter.draw(EmojiText.entries().get(i).glyph(), x + 2, y + 2, size - 4, 0xFFFFFFFF, false);
        }
        Minecraft.getInstance().textRenderer.draw(label, left, top + size * 8 + 5, 0xFFEEF3F8);
    }

    public String click(int x, int y, int width, int height) {
        if (overButton(x, y, width, height)) { toggle(); return ""; }
        if (!open) return null;
        int size = cell(height), left = left(width, height), top = top(height);
        if (x >= left && x < left + size * 8 && y >= top && y < top + size * 8) {
            int index = (y - top) / size * 8 + (x - left) / size;
            return EmojiText.entries().get(index).shortcode() + " ";
        }
        open = false;
        return null;
    }
}
