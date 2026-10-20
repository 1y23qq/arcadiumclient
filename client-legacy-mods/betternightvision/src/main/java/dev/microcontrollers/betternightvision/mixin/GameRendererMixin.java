package dev.microcontrollers.betternightvision.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.microcontrollers.betternightvision.config.BetterNightVisionConfig;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = GameRenderer.class)
public class GameRendererMixin {
    @ModifyReturnValue(method = "getNightVisionScale", at = @At("RETURN"))
    private float cleanerNightVision(float original, @Local(ordinal = 0) int i) {
        if (BetterNightVisionConfig.disableNightVision) return 0F;
        else if (BetterNightVisionConfig.cleanerNightVision) return !(i < 200) ? 1.0F : (float) i / 200F;
        else return original;
    }
}
