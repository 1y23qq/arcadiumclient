// SPDX-License-Identifier: GPL-3.0-only
package io.github.arcadiumclient.modern.client.glint;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.arcadiumclient.modern.mixin.GuiRenderAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public final class ShinyPotionRenderer {
    private static RenderPipeline pipeline;
    private ShinyPotionRenderer() { }
    private record Quad(Matrix3x2f pose, int x, int y, int size, int color, Matrix4f texture, TextureSetup textureSetup, ScreenRectangle bounds) implements GuiElementRenderState {
        private void vertex(VertexConsumer out, float x, float y, float u, float v) {
            var uv = texture.transform(new Vector4f(u, v, 0, 1));
            out.addVertexWith2DPose(pose, x, y).setUv(uv.x, uv.y).setColor(color);
        }
        public void buildVertices(VertexConsumer out) {
            vertex(out, x, y, 0, 0); vertex(out, x, y + size, 0, 1);
            vertex(out, x + size, y + size, 1, 1); vertex(out, x + size, y, 1, 0);
        }
        public RenderPipeline pipeline() { return ShinyPotionRenderer.pipeline(); }
        public ScreenRectangle scissorArea() { return null; }
    }
    private static RenderPipeline pipeline() {
        if (pipeline == null) pipeline = ModernGlint.clonePipeline(RenderPipelines.GUI_TEXTURED, "pipeline/shiny_potion",
            RenderPipelines.GUI_TEXTURED.getVertexShader().toString(), "arcadium:core/shiny_potion",
            RenderPipelines.GUI_TEXTURED.getVertexFormatBinding(0), BlendFunction.GLINT);
        return pipeline;
    }
    public static void render(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y) {
        var config = GlintConfig.INSTANCE;
        if (!config.getEnabled() || !config.getPotionGlint() || !config.getPotionGlintBackground() && !config.getPotionGlintForeground() && !config.getPotionGlintSize()) return;
        var data = ModernGlint.inspect(stack);
        if (!data.potion()) return;
        var settings = config.getPotionGlintBackground() ? config.getShinyPots() : config.getGuiItem();
        var client = Minecraft.getInstance();
        var texture = client.getTextureManager().getTexture(ItemFeatureRenderer.ENCHANTED_GLINT_ITEM);
        var binding = TextureSetup.singleTexture(texture.getTextureView(), texture.getSampler());
        var pose = new Matrix3x2f(graphics.pose());
        int inset = config.getPotionGlintSize() ? 0 : 2;
        int size = 16 - inset * 2;
        var bounds = new ScreenRectangle(x + inset, y + inset, size, size).transformMaxBounds(pose);
        for (int stroke = 0; stroke < 2; stroke++) {
            var uv = GlintMath.texture(settings, stroke, net.minecraft.util.Util.getMillis(), client.options.glintSpeed().get());
            int color = GlintMath.color(settings, stroke, config.getPotionBasedColor(), data);
            ((GuiRenderAccess) graphics).arcadium$state().addGuiElement(new Quad(pose, x + inset, y + inset, size, color, uv, binding, bounds));
        }
    }
}
