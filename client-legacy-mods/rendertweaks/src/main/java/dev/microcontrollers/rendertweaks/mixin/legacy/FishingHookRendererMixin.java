package dev.microcontrollers.rendertweaks.mixin.legacy;

//? if =1.7.10 {
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.microcontrollers.rendertweaks.config.RenderTweaksConfig;
import net.minecraft.client.render.entity.FishingBobberRenderer;
import net.minecraft.client.render.vertex.Tesselator;
import org.lwjgl.opengl.GL11;
import org.polyfrost.compose.render.PolyColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FishingBobberRenderer.class)
public class FishingHookRendererMixin {
    @WrapWithCondition(method = "render(Lnet/minecraft/entity/FishingBobberEntity;DDDFF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/Tesselator;vertex(DDD)V"))
    private boolean renderLineVertex(Tesselator vertices, double x, double y, double z) {
        return !RenderTweaksConfig.disableFishingLine && RenderTweaksConfig.fishingLineColor.getAlpha() != 0;
    }

    @WrapOperation(method = "render(Lnet/minecraft/entity/FishingBobberEntity;DDDFF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/Tesselator;color(I)V"))
    private void colorLine(Tesselator vertices, int rgb, Operation<Void> original) {
        PolyColor color = RenderTweaksConfig.fishingLineColor;
        if (color.getAlpha() == 255) original.call(vertices, color.getRed() << 16 | color.getGreen() << 8 | color.getBlue());
        else vertices.color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
    }

    @WrapOperation(method = "render(Lnet/minecraft/entity/FishingBobberEntity;DDDFF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/Tesselator;end()I", ordinal = 1))
    private int drawLine(Tesselator vertices, Operation<Integer> original) {
        int alpha = RenderTweaksConfig.fishingLineColor.getAlpha();
        if (RenderTweaksConfig.disableFishingLine || alpha == 0 || RenderTweaksConfig.fishingLineWidthMultiplier == 1f && alpha == 255)
            return original.call(vertices);
        GL11.glPushAttrib(GL11.GL_LINE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glLineWidth(RenderTweaksConfig.fishingLineWidthMultiplier);
        if (alpha < 255) {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        }
        try {
            return original.call(vertices);
        } finally {
            GL11.glPopAttrib();
        }
    }
}
//?}
