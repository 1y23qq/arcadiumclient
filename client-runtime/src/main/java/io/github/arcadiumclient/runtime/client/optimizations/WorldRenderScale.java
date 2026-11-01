// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.runtime.client.optimizations;

import io.github.arcadiumclient.runtime.client.features.BetaOptimizationsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.render.pipeline.RenderTarget;
import net.minecraft.client.render.platform.GlStateManager;
import org.apache.logging.log4j.LogManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

public final class WorldRenderScale {
    private static RenderTarget target;
    private static boolean active, failed;
    private static int readFramebuffer, drawFramebuffer;
    private static final java.nio.IntBuffer viewport = BufferUtils.createIntBuffer(4);
    private static volatile String state = "Off";
    public static long scaledFrames;

    private WorldRenderScale() { }

    public static boolean begin(boolean incompatible) {
        var mc = Minecraft.getInstance();
        if (!BetaOptimizationsConfig.getWorldRenderScale() || BetaOptimizationsConfig.getWorldResolution() >= 100) {
            state = "Off"; release(); return false;
        }
        if (failed || incompatible || !GL.getCapabilities().OpenGL30 || mc.width < 2 || mc.height < 2) {
            state = "Using full resolution (renderer compatibility)"; return false;
        }
        int width = Math.max(1, mc.width * Math.clamp(BetaOptimizationsConfig.getWorldResolution(), 50, 100) / 100);
        int height = Math.max(1, mc.height * Math.clamp(BetaOptimizationsConfig.getWorldResolution(), 50, 100) / 100);
        readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        viewport.clear(); GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        try {
            if (target == null || target.width != width || target.height != height) {
                release(); target = new RenderTarget(width, height, true);
            }
            target.bindWrite(true); active = true;
            state = width + " x " + height + " world / " + mc.width + " x " + mc.height + " HUD";
            return true;
        } catch (RuntimeException error) {
            failed = true; restore(); release(); state = "Using full resolution (allocation failed)";
            LogManager.getLogger("Arcadium/Optimizations").warn("World resolution scaling disabled", error); return false;
        }
    }

    public static void viewport(int x, int y, int width, int height) {
        GlStateManager.viewport(x, y, active ? target.width : width, active ? target.height : height);
    }

    public static void end() {
        if (!active) return;
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, target.frameBufferId);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            GL30.glBlitFramebuffer(0, 0, target.width, target.height, viewport.get(0), viewport.get(1),
                    viewport.get(0) + viewport.get(2), viewport.get(1) + viewport.get(3), GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
            scaledFrames++;
        } finally { active = false; restore(); }
    }

    private static void restore() {
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
        GlStateManager.viewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
    }

    public static String status() { return "World resolution: " + state; }
    public static void release() {
        if (target == null) return;
        int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING), draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int old = target.frameBufferId; target.destroyBuffers(); target = null;
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read==old?0:read);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw==old?0:draw);
    }
}
