// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.gui.qa;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import net.minecraft.client.renderer.SkyRenderer;
import io.github.arcadiumclient.modern.client.profile.LegacySmoothSkyOptions;

public final class NativeLegacySkyProbe {
    private static boolean active, ownCall;
    private static int attempted, hiddenFlag, entered, drawn, completed;
    private NativeLegacySkyProbe() {}
    static void start() { attempted = hiddenFlag = entered = drawn = completed = 0; active = true; }
    static Map<String, Integer> finish() { active = false; return Map.of("attempted", attempted, "hiddenFlagCalls", hiddenFlag, "entered", entered, "gpuDraws", drawn, "completed", completed); }
    public static void sample(SkyRenderer renderer) {
        if (!active || ownCall) return;
        attempted++;
        if (LegacySmoothSkyOptions.removeSunriseColor()) hiddenFlag++;
        ownCall = true;
        try { renderer.renderSunriseAndSunset(new PoseStack(), 1f, 0xFFFF8844); }
        finally { ownCall = false; }
    }
    public static void entered() { if (active && ownCall) entered++; }
    public static void draw() { if (active && ownCall) drawn++; }
    public static void completed() { if (active && ownCall) completed++; }
}
