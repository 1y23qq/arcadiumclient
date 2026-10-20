// SPDX-License-Identifier: GPL-3.0-only
// Copyright (c) 2026 0aurorq3 and Arcadium contributors.

package io.github.arcadiumclient.runtime.legacy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.render.Window;

public final class LegacyWindows {
    private static Window cached;
    private static int width, height, scale;
    private static boolean unicode;
    private LegacyWindows() {}
    public static Window current() {
        var client = Minecraft.getInstance();
        if (cached == null || width != client.width || height != client.height || scale != client.options.guiScale || unicode != client.isUnicode()) {
            width = client.width; height = client.height; scale = client.options.guiScale; unicode = client.isUnicode();
            cached = new Window(client, width, height);
        }
        return cached;
    }
}
