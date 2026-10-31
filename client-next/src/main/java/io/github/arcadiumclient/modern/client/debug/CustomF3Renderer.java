// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.debug;

import io.github.arcadiumclient.modern.client.features.CustomF3Config;
import io.github.arcadiumclient.shared.debug.DebugView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;

public final class CustomF3Renderer {
    private static final DebugView VIEW = new DebugView();
    private static boolean started, showing;
    private CustomF3Renderer() { }
    public static boolean active() { return CustomF3Config.getEnabled(); }
    public static void cleanup() {
        if(started && (Minecraft.getInstance().level==null || (!active() && !CustomF3Config.getPreview()))) {VIEW.reset();started=showing=false;CustomF3Data.clear();}
    }
    public static boolean showing() { return active() && showing; }
    public static void render(GuiGraphicsExtractor graphics) {
        Minecraft mc = Minecraft.getInstance();
        if ((!active() && !CustomF3Config.getPreview()) || mc.level == null || mc.player == null) { VIEW.reset(); started = false; showing = false; return; }
        if (!started) {
            if (active() && CustomF3Config.getAutoStart()) mc.debugEntries.setOverlayVisible(true);
            started = true;
        }
        showing = mc.getDebugOverlay().showDebugScreen() || CustomF3Config.getPreview();
        boolean allowed = mc.gui.screen() == null || mc.gui.screen() instanceof ChatScreen || CustomF3Config.getPreview();
        double progress = VIEW.animate(showing && allowed, CustomF3Config.getAnimations(), CustomF3Config.getAnimationSpeed(), System.nanoTime());
        if (showing && active()) {
            var debug = mc.getDebugOverlay();
            if (CustomF3Config.getAlwaysProfiler() && !debug.showProfilerChart()) debug.toggleProfilerChart();
            if (CustomF3Config.getAlwaysPing()) { if (!debug.showNetworkCharts()) debug.toggleNetworkCharts(); }
            else if (CustomF3Config.getAlwaysTps() && !debug.showFpsCharts()) debug.toggleFpsCharts();
        }
        if (progress <= 0) return;
        VIEW.draw(new DebugView.Surface() {
            public float width(String text) { return mc.font.width(text); }
            public void rect(float x1, float y1, float x2, float y2, int color) { graphics.fill((int)x1, (int)y1, (int)x2, (int)y2, color); }
            public void text(String text, float x, float y, int color, boolean shadow) { graphics.text(mc.font, text, (int)x, (int)y, color, shadow); }
            public void begin(float scale) { graphics.pose().pushMatrix(); graphics.pose().scale(scale, scale); }
            public void end() { graphics.pose().popMatrix(); }
        }, CustomF3Data.collect(mc), mc.getWindow().getGuiScaledWidth(), CustomF3Config.getFontScale(), CustomF3Config.getBackgroundColor().getRGB(), CustomF3Config.getShadowText(), progress);
    }
}
