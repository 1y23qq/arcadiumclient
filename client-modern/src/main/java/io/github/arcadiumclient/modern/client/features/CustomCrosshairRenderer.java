// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.features;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.arcadiumclient.modern.mixin.GuiRenderAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.Matrix3x2f;

public final class CustomCrosshairRenderer {
    private record QuadState(CrosshairDesign.Quad quad, Matrix3x2f pose, int color, ScreenRectangle bounds) implements GuiElementRenderState {
        public void buildVertices(VertexConsumer vertices) {
            vertices.addVertexWith2DPose(pose, quad.x1(), quad.y1()).setColor(color);
            vertices.addVertexWith2DPose(pose, quad.x4(), quad.y4()).setColor(color);
            vertices.addVertexWith2DPose(pose, quad.x3(), quad.y3()).setColor(color);
            vertices.addVertexWith2DPose(pose, quad.x2(), quad.y2()).setColor(color);
        }
        public RenderPipeline pipeline() { return RenderPipelines.GUI; }
        public TextureSetup textureSetup() { return TextureSetup.noTexture(); }
        public ScreenRectangle scissorArea() { return null; }
    }
    private CustomCrosshairRenderer() {}
    public static boolean render(GuiGraphics graphics) {
        if (!CustomCrosshair.isActive()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (!mc.options.getCameraType().isFirstPerson() && !LegacyCrosshairTweaks.thirdPerson(CustomCrosshairConfig.getThirdPerson())) return false;
        CrosshairDesign design = CustomCrosshair.design();
        Matrix3x2f pose = new Matrix3x2f(graphics.pose()).translate(graphics.guiWidth() / 2f, graphics.guiHeight() / 2f).scale(design.scale);
        var state = ((GuiRenderAccess) graphics).arcadium$state();
        for (var quad : CustomCrosshair.mesh().quads()) {
            int alpha = Math.round((quad.color() >>> 24) * design.opacity * (LegacyCrosshairTweaks.active() ? LegacyCrosshairTweaks.alpha() / 255f : 1));
            if (alpha == 0) continue;
            float x = Math.min(Math.min(quad.x1(), quad.x2()), Math.min(quad.x3(), quad.x4()));
            float y = Math.min(Math.min(quad.y1(), quad.y2()), Math.min(quad.y3(), quad.y4()));
            float right = Math.max(Math.max(quad.x1(), quad.x2()), Math.max(quad.x3(), quad.x4()));
            float bottom = Math.max(Math.max(quad.y1(), quad.y2()), Math.max(quad.y3(), quad.y4()));
            var bounds = new ScreenRectangle((int) Math.floor(x), (int) Math.floor(y), (int) Math.ceil(right - x) + 1, (int) Math.ceil(bottom - y) + 1).transformMaxBounds(pose);
            state.submitGuiElement(new QuadState(quad, pose, quad.color() & 0xFFFFFF | alpha << 24, bounds));
        }
        return true;
    }
}
