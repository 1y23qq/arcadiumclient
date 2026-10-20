package dev.microcontrollers.rendertweaks.mixin;

//? if >=1.8.9 {

/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.microcontrollers.rendertweaks.config.RenderTweaksConfig;
import net.minecraft.client.render.world.WorldRenderer;
import net.minecraft.world.border.WorldBorder;
import org.polyfrost.compose.render.PolyColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(WorldRenderer.class)
public class WorldBorderRendererMixin {
    @WrapOperation(method = "renderWorldBorder", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;color4f(FFFF)V"))
    private void changeWorldBorderColor(float r, float g, float b, float a, Operation<Void> original, @Local WorldBorder worldBorder) {
        switch (worldBorder.getStatus().name()) {
            case "STATIONARY": {
                if (RenderTweaksConfig.disableWorldBorderStationary || RenderTweaksConfig.worldBorderStationaryColor.getAlpha() == 0) {
                    original.call(r, g, b, 0F);
                    return;
                }
                PolyColor color = RenderTweaksConfig.worldBorderStationaryColor;
                original.call(color.getRedF(), color.getGreenF(), color.getBlueF(), color.getAlphaF());
                break;
            }
            case "SHRINKING": {
                if (RenderTweaksConfig.disableWorldBorderShrinking || RenderTweaksConfig.worldBorderShrinkingColor.getAlpha() == 0) {
                    original.call(r, g, b, 0F);
                    return;
                }
                PolyColor color = RenderTweaksConfig.worldBorderShrinkingColor;
                original.call(color.getRedF(), color.getGreenF(), color.getBlueF(), color.getAlphaF());
                break;
            }
            case "GROWING": {
                if (RenderTweaksConfig.disableWorldBorderGrowing || RenderTweaksConfig.worldBorderGrowingColor.getAlpha() == 0) {
                    original.call(r, g, b, 0F);
                    return;
                }
                PolyColor color = RenderTweaksConfig.worldBorderGrowingColor;
                original.call(color.getRedF(), color.getGreenF(), color.getBlueF(), color.getAlphaF());
            }
        }
    }
}

*///?}
