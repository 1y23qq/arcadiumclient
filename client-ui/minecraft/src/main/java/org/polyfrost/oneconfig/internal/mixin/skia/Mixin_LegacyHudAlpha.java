package org.polyfrost.oneconfig.internal.mixin.skia;

//? if = 1.7.10 {
/*import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.pipeline.RenderTarget;
import org.lwjgl.opengl.GL11;
import org.polyfrost.oneconfig.internal.ui.compose.SkiaCtx;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RenderTarget.class)
public class Mixin_LegacyHudAlpha {
    @WrapWithCondition(method = "draw(II)V", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDisable(I)V"), require = 1)
    private boolean oneconfig$preserveHudBlending(int capability) {
        return capability != GL11.GL_BLEND || !SkiaCtx.isLegacyHudTarget(this);
    }
}
*///?}
