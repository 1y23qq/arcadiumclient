// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.client.features;

import net.minecraft.client.render.Window;
import net.minecraft.client.render.platform.GlStateManager;

public final class LegacyGuiScaleTransform {
    private LegacyGuiScaleTransform() {}

    public static boolean hotbar(Window window) {
        return push(GuiScaling.hotbarRatio(window), window.getWidth() / 2f, window.getHeight());
    }

    public static boolean crosshair(Window window, int x, int y) {
        return push(GuiScaling.crosshairRatio(window), x + 7, y + 7);
    }

    private static boolean push(float ratio, float x, float y) {
        if (ratio == 1f) return false;
        GlStateManager.pushMatrix();
        GlStateManager.translatef(x, y, 0);
        GlStateManager.scalef(ratio, ratio, 1);
        GlStateManager.translatef(-x, -y, 0);
        return true;
    }

    public static void pop(boolean pushed) {
        if (pushed) GlStateManager.popMatrix();
    }
}
