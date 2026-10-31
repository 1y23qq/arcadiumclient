// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.debug;

import io.github.arcadiumclient.runtime.client.features.CustomF3Config;
import io.github.arcadiumclient.shared.debug.DebugView;
import io.github.arcadiumclient.shared.debug.DebugGraphs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.Window;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.gui.screen.game.ChatScreen;

public final class CustomF3Renderer {
    private static final DebugView VIEW = new DebugView();
    private static final DebugGraphs GRAPHS = new DebugGraphs();
    private static boolean started;
    private CustomF3Renderer() { }
    public static boolean showing() { return CustomF3Config.getEnabled() && Minecraft.getInstance().options.debugEnabled; }
    public static void cleanup() { if(started && (Minecraft.getInstance().world==null || (!CustomF3Config.getEnabled() && !CustomF3Config.getPreview()))) { VIEW.reset();GRAPHS.reset();started=false; } }
    public static boolean needsFrame() {
        var mc=Minecraft.getInstance();
        if((!CustomF3Config.getEnabled() && !CustomF3Config.getPreview()) || mc.world==null || mc.player==null){VIEW.reset();GRAPHS.reset();started=false;return false;}
        return mc.options.debugEnabled || CustomF3Config.getPreview() || VIEW.isVisible() || (!started && CustomF3Config.getAutoStart());
    }
    public static void render(Window window, DebugInfoSource source) {
        Minecraft mc = Minecraft.getInstance();
        if ((!CustomF3Config.getEnabled() && !CustomF3Config.getPreview()) || mc.world == null || mc.player == null) { VIEW.reset(); started = false; return; }
        if (!started) { if (CustomF3Config.getEnabled() && CustomF3Config.getAutoStart()) mc.options.debugEnabled = true; started = true; }
        boolean visible = mc.options.debugEnabled || CustomF3Config.getPreview();
        boolean allowed = mc.screen == null || mc.screen instanceof ChatScreen || CustomF3Config.getPreview();
        if (visible && CustomF3Config.getEnabled()) {
            if (CustomF3Config.getAlwaysProfiler()) mc.options.debugProfilerEnabled = true;
        }
        double progress = VIEW.animate(visible && allowed, CustomF3Config.getAnimations(), CustomF3Config.getAnimationSpeed(), System.nanoTime());
        if (progress <= 0) return;
        var surface = new DebugView.Surface() {
            public float width(String text) { return mc.textRenderer.getWidth(text); }
            public void rect(float x1, float y1, float x2, float y2, int color) { GuiElement.fill((int)x1, (int)y1, (int)x2, (int)y2, color); }
            public void text(String text, float x, float y, int color, boolean shadow) { mc.textRenderer.draw(text, (int)x, (int)y, color, shadow); }
            public void begin(float scale) { GlStateManager.pushMatrix(); GlStateManager.scalef(scale, scale, 1); }
            public void end() { GlStateManager.popMatrix(); }
        };
        VIEW.draw(surface, CustomF3Data.collect(mc, source), (int)window.getScaledWidth(), CustomF3Config.getFontScale(), CustomF3Config.getBackgroundColor().getRGB(), CustomF3Config.getShadowText(), progress);
        double serverMillis=mc.getServer()==null?-1:java.util.Arrays.stream(mc.getServer().averageTickTimes).average().orElse(0)/1_000_000;
        GRAPHS.draw(surface,(int)window.getScaledWidth(),(int)window.getScaledHeight(),visible && CustomF3Config.getAlwaysTps() && !CustomF3Config.getAlwaysPing() && !NativeDebugInfo.nativeFpsChart(mc),visible && CustomF3Config.getAlwaysPing(),NativeDebugInfo.ping(mc),serverMillis,System.nanoTime());
    }
}
